package com.nexus.control

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object FilePreview {
    private const val MAX_PREVIEW_BYTES = 100 * 1024

    suspend fun read(context: Context, uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val name = uri.lastPathSegment ?: "Dosya"
        val bytes = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Dosya açılamadı" }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (total < MAX_PREVIEW_BYTES) {
                val wanted = minOf(buffer.size, MAX_PREVIEW_BYTES - total)
                val read = input.read(buffer, 0, wanted)
                if (read <= 0) break
                out.write(buffer, 0, read)
                total += read
            }
            out.toByteArray()
        }

        val text = if (looksBinary(bytes)) {
            "<binary dosya · ilk ${bytes.size} bayt metin olarak gösterilmedi>"
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        name to text
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(4096)
        val controls = sample.count { b ->
            val v = b.toInt() and 0xFF
            v == 0 || (v < 9) || (v in 14..31)
        }
        return controls > sample.size / 20
    }
}
