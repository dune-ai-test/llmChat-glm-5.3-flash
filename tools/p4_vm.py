import io
import re

p = 'app/src/main/java/com/mrrob/llmchat/AppViewModel.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

start = s.find('    /** Encrypts the workspace with the vault format and writes it to [uri]. */')
end_marker = '\n    }\n'
end = s.find('/** Reads a backup', start)
assert start > 0 and end > start, (start, end)
export_block = s[start:end].rstrip() + '\n'

new_export = '''    /** Encrypted backup bytes staged for the SAF document launcher. */
    private val _exportBytes = MutableStateFlow<ByteArray?>(null)
    val exportBytes: StateFlow<ByteArray?> = _exportBytes.asStateFlow()
    fun consumeExportBytes() { _exportBytes.value = null }

    fun prepareExport(password: String) {
        viewModelScope.launch {
            _dataOp.value = DataOp.Running("Encrypting\\u2026")
            try {
                val json = repo.exportJson()
                val bytes = com.mrrob.llmchat.data.Vault.encrypt(json, password)
                _dataOp.value = DataOp.Idle
                _exportBytes.value = bytes
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Export failed: ${e.message}")
            }
        }
    }

    /** Writes the staged bytes while the SAF write grant is definitely alive. */
    fun writeExportTo(uri: android.net.Uri) {
        val bytes = _exportBytes.value ?: return
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val result = runCatching {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Could not open the file for writing.")
        }
        viewModelScope.launch {
            _dataOp.value = if (result.isSuccess) {
                DataOp.Done("Backup exported (${bytes.size / 1024} KB, encrypted).")
            } else {
                DataOp.Done("Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    '''
s = s[:start] + new_export + s[end:]

# importBackup: make the whole coroutine IO + persistable read grant
start2 = s.find('    fun importBackup(uri: android.net.Uri, password: String) {')
assert start2 > 0
end2 = s.find('\n    }\n', start2) + len('\n    }\n')
new_import = '''    fun importBackup(uri: android.net.Uri, password: String) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            _dataOp.value = DataOp.Running("Importing\\u2026")
            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the file.")
                val json = if (com.mrrob.llmchat.data.Vault.isVault(bytes)) {
                    com.mrrob.llmchat.data.Vault.decrypt(bytes, password)
                } else {
                    String(bytes, Charsets.UTF_8)
                }
                val r = repo.importJson(json)
                _dataOp.value = when (r) {
                    is AppRepository.ImportResult.Success -> DataOp.Done(
                        "Imported ${r.conversations} conversation${if (r.conversations == 1) "" else "s"}" +
                            if (r.connections > 0) " and ${r.connections} connection${if (r.connections == 1) "" else "s"}." else "."
                    )
                    is AppRepository.ImportResult.Failure -> DataOp.Done(r.reason)
                }
            } catch (e: com.mrrob.llmchat.data.Vault.VaultException) {
                _dataOp.value = DataOp.NeedPassword(e.message ?: "Password required.")
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Import failed: ${e.message}")
            }
        }
    }
'''
s = s[:start2] + new_import + s[end2:]

# remove any leftover withContext import usage (kept harmless)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('vm export/import replaced')
