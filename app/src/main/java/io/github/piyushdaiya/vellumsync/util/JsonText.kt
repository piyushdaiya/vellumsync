package io.github.piyushdaiya.vellumsync.util

object JsonText {
    fun quote(value: String?): String {
        if (value == null) {
            return "null"
        }

        val escaped = buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }

        return "\"$escaped\""
    }

    fun stringArray(values: List<String>): String {
        return values.joinToString(prefix = "[", postfix = "]") { quote(it) }
    }
}
