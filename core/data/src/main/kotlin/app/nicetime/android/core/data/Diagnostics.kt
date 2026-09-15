package app.nicetime.android.core.data

private val sensitiveQuery = Regex("(?i)(token|key|secret|signature|sig|auth)=([^&\\s]+)")
private val bearerToken = Regex("(?i)bearer\\s+[a-z0-9._~+/=-]+")

fun redactSensitiveText(value: String): String = value
    .replace(sensitiveQuery) { "${it.groupValues[1]}=[REDACTED]" }
    .replace(bearerToken, "Bearer [REDACTED]")

class DiagnosticLog(private val capacity: Int = 200) {
    private val events = ArrayDeque<String>()
    // ponytail: optional logcat callback, not used in tests
    var logcatWriter: ((String) -> Unit)? = null

    @Synchronized
    fun record(message: String) {
        val redacted = redactSensitiveText(message)
        if (events.size == capacity) events.removeFirst()
        events.addLast(redacted)
        logcatWriter?.invoke(redacted.take(4000))
    }

    @Synchronized
    fun export(): String = events.joinToString(separator = "\n")

    @Synchronized
    fun exportRedacted(): String = export()

    @Synchronized
    fun clear() = events.clear()

    /** Export events with source/format/OS context for diagnostics. */
    @Synchronized
    fun exportWithContext(): String = buildString {
        appendLine("=== NiceTime Diagnostics ===")
        appendLine("OS: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Events: ${events.size}")
        appendLine("---")
        events.forEach { appendLine(it) }
    }
}
