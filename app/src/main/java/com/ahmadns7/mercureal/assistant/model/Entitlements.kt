package com.ahmadns7.mercureal.assistant.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadns7.mercureal.assistant.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.entitlementStore by preferencesDataStore(name = "entitlements")

/** Free-tier limits. Change [DAILY_CONVERSATIONS] here to tune the cap. */
object FreeTier {
    /** Conversations (a hold-to-talk or wake-word turn, plus its follow-ups) allowed per local day. */
    const val DAILY_CONVERSATIONS = 20
}

/**
 * Pro unlock state and the free-tier daily counter. Everything is stored on this phone.
 * The Pro flag caches the Google Play one-time purchase (see billing/BillingManager.kt).
 * Debug builds also have a local test switch so premium UI can be exercised without Play.
 */
class Entitlements(private val context: Context) {

    private val store: DataStore<Preferences> get() = context.entitlementStore

    private val proKey = booleanPreferencesKey("pro_purchased")
    private val debugProKey = booleanPreferencesKey("debug_pro")
    private val dateKey = stringPreferencesKey("count_date")
    private val countKey = intPreferencesKey("count_today")

    /** True when the Play purchase is owned, or (debug builds only) the local test switch is on. */
    val isPro: Flow<Boolean> = store.data.map { p ->
        p[proKey] == true || (BuildConfig.DEBUG && p[debugProKey] == true)
    }

    /** Conversations started today on the free tier. Resets at local midnight. */
    val usedToday: Flow<Int> = store.data.map { p ->
        if (p[dateKey] == LocalDate.now().toString()) p[countKey] ?: 0 else 0
    }

    suspend fun setPurchased(owned: Boolean) {
        store.edit { it[proKey] = owned }
    }

    /** Debug builds only. Release builds ignore this flag. */
    suspend fun setDebugPro(on: Boolean) {
        store.edit { it[debugProKey] = on }
    }

    /**
     * Called when a new conversation starts. Returns false when the free tier is used up.
     * Pro users are never counted or limited.
     */
    suspend fun startConversation(): Boolean {
        if (isPro.first()) return true
        var allowed = false
        store.edit { p ->
            val today = LocalDate.now().toString()
            val count = if (p[dateKey] == today) p[countKey] ?: 0 else 0
            allowed = count < FreeTier.DAILY_CONVERSATIONS
            p[dateKey] = today
            p[countKey] = if (allowed) count + 1 else count
        }
        return allowed
    }
}
