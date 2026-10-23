package com.ahmadns7.mercureal.assistant.memory

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.notesStore by preferencesDataStore(name = "notes")

data class Note(val id: Long, val text: String, val createdAt: Long)

/**
 * On-device notes ("remember that ..."). Stored as one JSON array in the app's private DataStore
 * (filesDir/datastore). No network, no sync, cleared when the app data is cleared.
 */
class NoteStore(private val context: Context) {

    private val key = stringPreferencesKey("notes_json")

    /** Newest first. */
    val notes: Flow<List<Note>> = context.notesStore.data.map { decode(it[key]) }

    suspend fun all(): List<Note> = notes.first()

    suspend fun add(text: String): Note {
        val clean = text.trim().take(MAX_TEXT)
        require(clean.isNotEmpty()) { "empty note" }
        var saved: Note? = null
        context.notesStore.edit { prefs ->
            val list = decode(prefs[key]).toMutableList()
            var id = System.currentTimeMillis()
            while (list.any { it.id == id }) id++
            val note = Note(id, clean, System.currentTimeMillis())
            saved = note
            list.add(0, note)
            prefs[key] = encode(list.take(MAX_NOTES))
        }
        return checkNotNull(saved)
    }

    suspend fun delete(id: Long) {
        context.notesStore.edit { prefs ->
            prefs[key] = encode(decode(prefs[key]).filterNot { it.id == id })
        }
    }

    suspend fun deleteAll() {
        context.notesStore.edit { it.remove(key) }
    }

    private fun decode(json: String?): List<Note> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(o.getLong("id"), o.getString("text"), o.getLong("createdAt"))
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(notes: List<Note>): String {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("text", n.text)
                    .put("createdAt", n.createdAt)
            )
        }
        return arr.toString()
    }

    companion object {
        const val MAX_NOTES = 200
        const val MAX_TEXT = 500
    }
}
