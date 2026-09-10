package com.mrrob.llmchat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Local image attachments: picked images are copied into app-private storage,
 * downscaled for sending, and encoded as `data:` URLs in the OpenAI
 * `image_url` content format.
 */
object ImageAttachments {

    private val counter = AtomicLong()

    fun attachmentsDir(context: Context): File =
        File(context.filesDir, "attachments").apply { mkdirs() }

    /** Copies [uri] into app storage, downscaled to at most [maxEdge]. Returns the absolute path. */
    fun save(context: Context, uri: Uri, maxEdge: Int = 1536): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val out = File(attachmentsDir(context), "img_${System.currentTimeMillis()}_${counter.incrementAndGet()}.jpg")
        ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, bos)
            out.writeBytes(bos.toByteArray())
        }
        bitmap.recycle()
        out.absolutePath
    }.getOrNull()

    /** base64 data URL for the wire payload. */
    fun dataUrl(path: String): String? = runCatching {
        val bytes = File(path).readBytes()
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    /** Decodes a stored attachment for thumbnail display. */
    fun loadBitmap(path: String, maxEdge: Int = 256): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }.getOrNull()

    fun parseList(json: String): List<String> =
        if (json.isBlank()) emptyList() else runCatching {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())

    fun toJson(paths: List<String>): String = org.json.JSONArray(paths).toString()
}
