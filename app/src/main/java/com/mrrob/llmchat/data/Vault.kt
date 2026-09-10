package com.mrrob.llmchat.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Aster's small self-contained backup encryption format ("ASTV1"):
 *
 *   "ASTV1" | salt(16) | iv(12) | aes-cbc ciphertext | hmac-sha256(32)
 *
 * A password is stretched with PBKDF2-HMAC-SHA256 into 64 bytes: a 32-byte
 * AES key and a 32-byte MAC key. Encrypt-then-MAC, so a wrong password (or a
 * tampered file) fails the tag check before anything is decrypted. Only
 * standard JDK primitives are used - no third-party crypto.
 */
object Vault {

    private const val MAGIC = "ASTV1"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val MAC_LEN = 32
    private const val ITERATIONS = 120_000

    fun encrypt(json: String, password: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)

        val (encKey, macKey) = deriveKeys(password, salt)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))

        val blob = MAGIC.toByteArray(Charsets.ISO_8859_1) + salt + iv + ciphertext
        val mac = hmac(macKey, blob)
        return blob + mac
    }

    class VaultException(message: String) : Exception(message)

    /** Returns true when the bytes are an Aster encrypted vault. */
    fun isVault(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.length && String(bytes, 0, MAGIC.length, Charsets.ISO_8859_1) == MAGIC

    fun decrypt(bytes: ByteArray, password: String): String {
        val magicLen = MAGIC.length
        if (bytes.size < magicLen + SALT_LEN + IV_LEN + MAC_LEN) {
            throw VaultException("The backup file is truncated or corrupted.")
        }
        if (String(bytes, 0, magicLen, Charsets.ISO_8859_1) != MAGIC) {
            throw VaultException("This is not an Aster backup file.")
        }
        val salt = bytes.copyOfRange(magicLen, magicLen + SALT_LEN)
        val iv = bytes.copyOfRange(magicLen + SALT_LEN, magicLen + SALT_LEN + IV_LEN)
        val macStart = bytes.size - MAC_LEN
        val ciphertext = bytes.copyOfRange(magicLen + SALT_LEN + IV_LEN, macStart)
        val storedMac = bytes.copyOfRange(macStart, bytes.size)

        val (encKey, macKey) = deriveKeys(password, salt)
        val computed = hmac(macKey, bytes.copyOfRange(0, macStart))
        if (!MessageDigest.isEqual(computed, storedMac)) {
            throw VaultException("Wrong password - the backup could not be verified.")
        }
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: VaultException) {
            throw e
        } catch (_: Exception) {
            throw VaultException("The backup could not be decrypted.")
        }
    }

    private fun deriveKeys(password: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 512)
        val key = factory.generateSecret(spec).encoded
        return key.copyOfRange(0, 32) to key.copyOfRange(32, 64)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
}
