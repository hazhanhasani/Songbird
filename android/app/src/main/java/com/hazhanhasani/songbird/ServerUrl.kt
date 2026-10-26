package com.hazhanhasani.songbird

import java.net.URI

/** HTTPS-only origin; reject credentials, fragments and query-based host tricks. */
object ServerUrl {
    fun normalize(raw: String): String? {
        val input = raw.trim()
        if (input.isBlank() || input.contains(' ') || input.contains('\\')) return null
        val withScheme = if (input.contains("://")) input else "https://$input"
        return try {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
            if (uri.path?.trim('/')?.isNotEmpty() == true) return null
            if (host == "localhost" || host.endsWith(".localhost")) return null
            if (uri.port > 65535 || uri.port == 0 || uri.port < -1) return null
            val normalizedHost = host.lowercase()
            if (!normalizedHost.contains('.') && !normalizedHost.contains(':')) return null
            URI("https", null, normalizedHost, uri.port, "/", null, null).toASCIIString().removeSuffix("/")
        } catch (_: Exception) {
            null
        }
    }
}
