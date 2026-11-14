package io.github.jaswantk917.chitthi

import java.io.ByteArrayOutputStream

/** an sms:, smsto:, mms: or mmsto: link (RFC 5724), from its still-encoded part after the scheme */
data class SmsLink(val number: String?, val body: String) {
    companion object {
        fun parse(encoded: String): SmsLink {
            val parts = encoded.removePrefix("//").split('?', limit = 2)
            val number = parts[0].split(',', ';').map { decode(it).trim() }.firstOrNull { it.isNotEmpty() }
            val body = parts.getOrNull(1).orEmpty().split('&')
                .firstOrNull { it.startsWith("body=", ignoreCase = true) }
                ?.substringAfter('=')?.let(::decode).orEmpty()
            return SmsLink(number, body)
        }

        // hand-typed links carry stray '%'s ("hi%kat"); keep those as text rather than drop the link
        private fun decode(s: String): String {
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val hi = if (s[i] == '%' && i + 2 < s.length) s[i + 1].digitToIntOrNull(16) else null
                val lo = hi?.let { s[i + 2].digitToIntOrNull(16) }
                if (hi != null && lo != null) {
                    bytes.write(hi * 16 + lo)
                    i += 3
                } else {
                    val codePoint = s.codePointAt(i)
                    bytes.write(String(Character.toChars(codePoint)).toByteArray())
                    i += Character.charCount(codePoint)
                }
            }
            return String(bytes.toByteArray(), Charsets.UTF_8)
        }
    }
}
