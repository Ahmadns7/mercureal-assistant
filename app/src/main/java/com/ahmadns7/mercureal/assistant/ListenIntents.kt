package com.ahmadns7.mercureal.assistant

import android.content.Context
import android.content.Intent

/** Entry point used by the home screen widget and the quick settings tile. */
object ListenIntents {
    const val ACTION_LISTEN = "com.ahmadns7.mercureal.assistant.action.LISTEN"

    fun launchIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_LISTEN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
