package com.ahmadns7.mercureal.assistant.commands

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs a [DeviceCommand] with public Android intents only (AlarmClock, ACTION_DIAL, launcher
 * intents). Returns the sentence to show on screen and speak aloud. No network is used.
 *
 * [requestPermission] asks the activity to show a runtime permission prompt. The user repeats the
 * command after granting it.
 */
class DeviceActions(
    private val context: Context,
    private val requestPermission: (String) -> Unit,
) {

    fun run(cmd: DeviceCommand): String? = when (cmd) {
        is DeviceCommand.Alarm -> alarm(cmd)
        is DeviceCommand.Timer -> timer(cmd)
        is DeviceCommand.Call -> call(cmd)
        is DeviceCommand.OpenApp -> openApp(cmd)
        is DeviceCommand.Reminder -> reminder(cmd)
        DeviceCommand.None -> null
    }

    private fun alarm(c: DeviceCommand.Alarm): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, c.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, c.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) {
            "alarm set for ${clock(c.hour, c.minute)}"
        } else {
            "i couldn't reach a clock app to set that alarm"
        }
    }

    private fun timer(c: DeviceCommand.Timer): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, c.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) {
            "timer set for ${duration(c.seconds)}"
        } else {
            "i couldn't reach a clock app to set that timer"
        }
    }

    private fun call(c: DeviceCommand.Call): String {
        val number: String
        val label: String
        if (c.name.matches(Regex("""[+\d][\d\s\-()]{4,}"""))) {
            number = c.name.filter { it.isDigit() || it == '+' }
            label = c.name
        } else {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                requestPermission(Manifest.permission.READ_CONTACTS)
                return "i need contacts permission to find ${c.name}. allow it and say the command again"
            }
            when (val found = lookup(c.name)) {
                is Lookup.Found -> {
                    number = found.number
                    label = found.name
                }
                is Lookup.Ambiguous ->
                    return "more than one contact matches ${c.name}: ${found.names.joinToString(", ")}. say the full name"
                Lookup.NotFound -> return "no contact called ${c.name}"
            }
        }
        val i = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
        return if (launch(i)) {
            "dialer open with $label. tap call to confirm"
        } else {
            "no phone app to dial with"
        }
    }

    private fun openApp(c: DeviceCommand.OpenApp): String {
        val pkg = CURATED_APPS[c.name.lowercase().trim()]
        if (pkg != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return "${c.name} isn't installed on this phone"
            return if (launch(launchIntent)) "opening ${c.name}" else "couldn't open ${c.name}"
        }
        // Not on the curated list: show the system app chooser so the user picks the app.
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            "open ${c.name} with",
        )
        return if (launch(chooser)) {
            "${c.name} isn't on my list. pick the app from the chooser"
        } else {
            "couldn't open the app chooser"
        }
    }

    private fun reminder(c: DeviceCommand.Reminder): String {
        val at = c.triggerAtMs
            ?: return "when should i remind you? say it like: add reminder ${c.text} at 5pm"
        Reminders.schedule(context, c.text, at)
        val notes = if (!Reminders.notificationsAllowed(context)) {
            requestPermission(Manifest.permission.POST_NOTIFICATIONS)
            " (allow notifications or i can't show it)"
        } else ""
        val exact = if (!Reminders.canScheduleExact(context)) {
            " (approximate timing: allow exact alarms for this app in system settings)"
        } else ""
        return "reminder set for ${SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(at))}: ${c.text}$notes$exact"
    }

    private sealed interface Lookup {
        data class Found(val name: String, val number: String) : Lookup
        data class Ambiguous(val names: List<String>) : Lookup
        data object NotFound : Lookup
    }

    private fun lookup(name: String): Lookup {
        val rows = ArrayList<Pair<String, String>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null,
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    rows += n to num
                }
            }
        } catch (e: SecurityException) {
            return Lookup.NotFound
        }
        val exact = rows.filter { it.first.equals(name, ignoreCase = true) }
        val pool = if (exact.isNotEmpty()) exact else rows
        val names = pool.map { it.first }.distinct()
        return when {
            names.isEmpty() -> Lookup.NotFound
            names.size > 1 -> Lookup.Ambiguous(names.take(5))
            else -> Lookup.Found(names[0], pool[0].second)
        }
    }

    private fun launch(i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (i.resolveActivity(context.packageManager) == null) {
                false
            } else {
                context.startActivity(i)
                true
            }
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            // Background activity starts can be blocked (for example from the wake word service).
            false
        }
    }

    private fun hasPermission(p: String): Boolean =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    private fun clock(h: Int, m: Int): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, h)
            set(java.util.Calendar.MINUTE, m)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = buildList {
            if (h > 0) add("$h ${if (h == 1) "hour" else "hours"}")
            if (m > 0) add("$m ${if (m == 1) "minute" else "minutes"}")
            if (s > 0) add("$s ${if (s == 1) "second" else "seconds"}")
        }
        return parts.joinToString(" ")
    }

    companion object {
        /** Launch by package name. Only these packages are declared in the manifest <queries>. */
        val CURATED_APPS: Map<String, String> = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "settings" to "com.android.settings",
            "play store" to "com.android.vending",
            "photos" to "com.google.android.apps.photos",
            "calendar" to "com.google.android.calendar",
        )
    }
}
