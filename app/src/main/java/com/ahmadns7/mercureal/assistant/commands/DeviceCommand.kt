package com.ahmadns7.mercureal.assistant.commands

import java.util.Calendar

/**
 * A phone action the user asked for by voice. Anything that does not match is [None] and falls
 * through to normal LLM chat.
 */
sealed interface DeviceCommand {
    data class Alarm(val hour: Int, val minute: Int) : DeviceCommand
    data class Timer(val seconds: Int) : DeviceCommand
    data class Call(val name: String) : DeviceCommand
    data class OpenApp(val name: String) : DeviceCommand

    /** [triggerAtMs] is null when no usable time was spoken. */
    data class Reminder(val text: String, val triggerAtMs: Long?) : DeviceCommand

    data object None : DeviceCommand
}

/** Pure parser, no Android dependencies, so it can be unit tested on the JVM. */
object DeviceCommands {

    private val alarm = Regex(
        """^\s*(?:please\s+)?(?:set|create|add)\s+(?:an?\s+|my\s+)?alarm\s+(?:for\s+)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val timer = Regex(
        """^\s*(?:please\s+)?(?:set|start)\s+(?:an?\s+)?timer\s+(?:for\s+)?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val call = Regex(
        """^\s*(?:please\s+)?(?:call|dial|ring)\s+(.{2,60}?)[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val open = Regex(
        """^\s*(?:please\s+)?open\s+(?:the\s+)?(.{2,40}?)(?:\s+app)?[\s.!?]*$""",
        RegexOption.IGNORE_CASE,
    )
    private val reminder = Regex(
        """^\s*(?:please\s+)?(?:add|set|create|make)\s+(?:a\s+)?reminder\s+(?:to\s+|about\s+|that\s+)?(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val reminderWhen = Regex("""^(.+)\s+(?:at|in)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val clockTime = Regex(
        """^(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val relative = Regex(
        """^(?:in\s+)?(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)$""",
        RegexOption.IGNORE_CASE,
    )
    private val numberLike = Regex("""[+\d][\d\s\-()]{4,}""")

    private val callStop = setOf("it", "back", "them", "him", "her", "me", "someone", "anyone")
    private val openStop = setOf("it", "that", "this", "up", "the door", "the window", "file", "link")

    /** Clock-time or relative reminder times are at most one week out. */
    private const val MAX_REMINDER_MS = 7L * 24 * 3600 * 1000

    fun parse(text: String, nowMs: Long = System.currentTimeMillis()): DeviceCommand {
        val t = text.trim()
        if (t.isEmpty()) return DeviceCommand.None

        alarm.matchEntire(t)?.let { m ->
            val hour = toHour24(m.groupValues[1].toInt(), m.groupValues[3]) ?: return DeviceCommand.None
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (minute !in 0..59) return DeviceCommand.None
            return DeviceCommand.Alarm(hour, minute)
        }

        timer.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return DeviceCommand.None
            val unit = m.groupValues[2].lowercase()
            val secs = unitSeconds(unit) * n
            if (secs !in 1L..86_400L) return DeviceCommand.None
            return DeviceCommand.Timer(secs.toInt())
        }

        call.matchEntire(t)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.wordCount() <= 3 && name.lowercase() !in callStop) return DeviceCommand.Call(name)
        }

        open.matchEntire(t)?.let { m ->
            val name = m.groupValues[1].trim()
            if (name.wordCount() <= 3 && name.lowercase() !in openStop) return DeviceCommand.OpenApp(name)
        }

        reminder.matchEntire(t)?.let { m ->
            val rest = m.groupValues[1].trim().trimEnd('.', '!', '?').trim()
            val withTime = reminderWhen.matchEntire(rest)
            if (withTime != null) {
                val body = withTime.groupValues[1].trim()
                val trigger = parseWhen(withTime.groupValues[2].trim(), nowMs)
                if (trigger != null && body.length >= 2) {
                    return DeviceCommand.Reminder(body.take(200), trigger)
                }
            }
            if (rest.length >= 2) return DeviceCommand.Reminder(rest.take(200), null)
        }

        return DeviceCommand.None
    }

    /** Next wall-clock occurrence of a time ("5pm", "17:30") or a relative delay ("in 10 minutes"). */
    fun parseWhen(s: String, nowMs: Long): Long? {
        val value = s.trim().trimEnd('.', '!', '?').trim()
        relative.matchEntire(value)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            val ms = n * unitSeconds(m.groupValues[2].lowercase()) * 1000
            return if (ms in 1..MAX_REMINDER_MS) nowMs + ms else null
        }
        clockTime.matchEntire(value)?.let { m ->
            val hour = toHour24(m.groupValues[1].toInt(), m.groupValues[3]) ?: return null
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (minute !in 0..59) return null
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_MONTH, 1)
            return cal.timeInMillis
        }
        return null
    }

    /** 12h (with am/pm) or 24h (without) to 0..23. Null when out of range. */
    internal fun toHour24(h: Int, ampm: String): Int? {
        val ap = ampm.lowercase().replace(".", "")
        if (ap.isEmpty()) return if (h in 0..23) h else null
        if (h !in 1..12) return null
        val pm = ap.startsWith("p")
        return when {
            pm && h == 12 -> 12
            pm -> h + 12
            h == 12 -> 0
            else -> h
        }
    }

    private fun unitSeconds(unit: String): Long = when {
        unit.startsWith("sec") -> 1L
        unit.startsWith("min") -> 60L
        else -> 3600L
    }

    private fun String.wordCount(): Int = trim().split(Regex("\\s+")).size
}
