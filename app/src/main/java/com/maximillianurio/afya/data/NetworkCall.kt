package com.maximillianurio.afya.data

/**
 * One request that left the phone: when, to which host, for which scale, and how it ended. The only
 * kind there is today is the per-scale key lookup; the log exists so that stays checkable.
 */
data class NetworkCall(
    val timestampMs: Long,
    val host: String,
    val mac: String,
    val outcome: String,
) {
    /** One tab-separated line; tabs and newlines in the fields are flattened to spaces first. */
    fun line(): String = listOf(timestampMs.toString(), host, mac, outcome)
        .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    companion object {
        fun parse(line: String): NetworkCall? {
            val parts = line.split('\t')
            if (parts.size != 4) return null
            val at = parts[0].toLongOrNull() ?: return null
            return NetworkCall(at, parts[1], parts[2], parts[3])
        }
    }
}
