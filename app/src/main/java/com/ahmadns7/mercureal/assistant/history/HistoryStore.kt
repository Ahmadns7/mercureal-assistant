package com.ahmadns7.mercureal.assistant.history

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ahmadns7.mercureal.assistant.model.ModelSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** One saved conversation turn. [mode] is chat, recall, note, device or translate. */
@Entity(tableName = "turns", indices = [Index("sessionId"), Index("timestamp")])
data class Turn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: Long,
    val mode: String,
    val question: String,
    val answer: String,
)

/** Full-text index over the same turn. rowid = Turn.id; kept in step by [HistoryStore]. */
@Fts4
@Entity(tableName = "turns_fts")
data class TurnFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int,
    val question: String,
    val answer: String,
)

@Dao
interface TurnDao {
    @Insert
    suspend fun insertTurn(turn: Turn): Long

    @Insert
    suspend fun insertFts(row: TurnFts)

    @Query("SELECT * FROM turns ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<Turn>>

    @Query("SELECT * FROM turns ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<Turn>

    @Query(
        "SELECT turns.* FROM turns JOIN turns_fts ON turns.id = turns_fts.rowid " +
            "WHERE turns_fts MATCH :match ORDER BY turns.timestamp DESC LIMIT 200"
    )
    suspend fun search(match: String): List<Turn>

    @Query("SELECT id FROM turns WHERE timestamp < :cutoff")
    suspend fun idsOlderThan(cutoff: Long): List<Long>

    @Query("SELECT id FROM turns ORDER BY timestamp DESC, id DESC LIMIT -1 OFFSET :keep")
    suspend fun idsBeyond(keep: Int): List<Long>

    @Query("SELECT id FROM turns WHERE sessionId = :sessionId")
    suspend fun idsInSession(sessionId: String): List<Long>

    @Query("DELETE FROM turns_fts WHERE rowid IN (:ids)")
    suspend fun deleteFts(ids: List<Long>)

    @Query("DELETE FROM turns WHERE id IN (:ids)")
    suspend fun deleteTurns(ids: List<Long>)

    @Query("DELETE FROM turns_fts")
    suspend fun clearFts()

    @Query("DELETE FROM turns")
    suspend fun clearTurns()
}

@Database(entities = [Turn::class, TurnFts::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun turns(): TurnDao
}

/**
 * Conversation history in the app's private Room database (history.db). Nothing leaves the phone.
 * Every answered turn is saved with its question, answer, timestamp and mode. Retention runs after
 * each save: turns older than the day limit, and anything past the turn cap, are deleted.
 */
class HistoryStore(context: Context, private val settings: ModelSettings) {

    private val db: HistoryDatabase = Room.databaseBuilder(
        context.applicationContext, HistoryDatabase::class.java, "history.db"
    ).build()
    private val dao = db.turns()

    /** Newest first, capped at [MAX_ROWS]. */
    fun observe(): Flow<List<Turn>> = dao.observe(MAX_ROWS)

    suspend fun record(sessionId: String, mode: String, question: String, answer: String) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val id = dao.insertTurn(
                Turn(sessionId = sessionId, timestamp = now, mode = mode, question = question, answer = answer)
            )
            dao.insertFts(TurnFts(rowid = id.toInt(), question = question, answer = answer))
            prune(now)
        }
    }

    /** Last [count] turns, oldest first, formatted for the LLM system prompt. Null when there are none. */
    suspend fun recentContext(count: Int = RECENT_TURNS): String? = withContext(Dispatchers.IO) {
        val turns = dao.recent(count).reversed()
        if (turns.isEmpty()) return@withContext null
        buildString {
            append("Recent conversation on this phone, oldest first. Use it only for follow-up questions ")
            append("(for example, \"what did I just ask\"):\n")
            for (t in turns) {
                append("User: ").append(clip(t.question)).append('\n')
                append("Assistant: ").append(clip(t.answer)).append('\n')
            }
        }.trimEnd()
    }

    /** Full-text search. Each typed word matches as a prefix, all words must appear. */
    suspend fun search(query: String): List<Turn> = withContext(Dispatchers.IO) {
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase()).map { it.value }.toList()
        if (tokens.isEmpty()) emptyList() else dao.search(tokens.joinToString(" ") { "\"$it\"*" })
    }

    suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            val ids = dao.idsInSession(sessionId)
            if (ids.isNotEmpty()) {
                dao.deleteFts(ids)
                dao.deleteTurns(ids)
            }
        }
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            dao.clearFts()
            dao.clearTurns()
        }
    }

    private suspend fun prune(now: Long) {
        val days = settings.retentionDays.first()
        val cap = settings.retentionTurns.first()
        val ids = (dao.idsOlderThan(now - days * DAY_MS) + dao.idsBeyond(cap)).distinct()
        if (ids.isNotEmpty()) {
            dao.deleteFts(ids)
            dao.deleteTurns(ids)
        }
    }

    private fun clip(s: String): String = if (s.length <= CLIP_CHARS) s else s.take(CLIP_CHARS) + "..."

    companion object {
        const val RECENT_TURNS = 4
        const val MAX_ROWS = 1000
        private const val CLIP_CHARS = 300
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
