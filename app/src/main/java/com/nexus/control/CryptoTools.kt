package com.nexus.control

import android.util.Base64
import java.security.MessageDigest

object CryptoTools {
    fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    fun base64Encode(input: String): String = Base64.encodeToString(input.toByteArray(), Base64.NO_WRAP)
    fun base64Decode(input: String): String = runCatching { String(Base64.decode(input.trim(), Base64.DEFAULT)) }.getOrElse { "Decode hatası: ${it.message}" }
    fun decodeJwt(input: String): String {
        val parts = input.trim().split(".")
        if (parts.size < 2) return "JWT biçimi geçersiz"
        fun decodePart(s: String) = runCatching { String(Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)) }.getOrElse { "decode error" }
        return "HEADER\n${decodePart(parts[0])}\n\nPAYLOAD\n${decodePart(parts[1])}\n\nNot: İmza doğrulanmadı."
    }
}
