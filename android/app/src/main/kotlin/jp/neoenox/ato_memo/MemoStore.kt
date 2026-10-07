package jp.neoenox.ato_memo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File

class MemoStore(context: Context) : SQLiteOpenHelper(context, "memos.db", null, DB_VERSION) {
    companion object {
        const val DB_VERSION = 2
        const val RETENTION_DAYS = 30L
        // seen pruning bounds for #8 (unbounded growth guard).
        const val SEEN_MAX_ENTRIES = 20000
        const val SEEN_MAX_AGE_DAYS = 180L
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS memos(id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT UNIQUE NOT NULL, path TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL DEFAULT '', ocr TEXT NOT NULL DEFAULT '', ocrError TEXT NOT NULL DEFAULT '', candidates TEXT NOT NULL DEFAULT '[]', created INTEGER NOT NULL, due INTEGER NOT NULL, custom INTEGER NOT NULL DEFAULT 0, delivered INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX IF NOT EXISTS reminders ON memos(completed,delivered,due)")
        db.execSQL("CREATE INDEX IF NOT EXISTS completed_idx ON memos(completed)")
        db.execSQL("CREATE TABLE IF NOT EXISTS seen(source TEXT PRIMARY KEY, added INTEGER NOT NULL DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Staged migration: never crash existing installs (#5).
        db.beginTransaction()
        try {
            onCreate(db)
            if (oldVersion < 2) migrateTo2(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Keep user data on downgrade; ensure tables exist.
        onCreate(db)
    }
    private fun migrateTo2(db: SQLiteDatabase) {
        val hasAdded = db.rawQuery("PRAGMA table_info(seen)", null).use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == "added") { found = true; break }
            }
            found
        }
        if (!hasAdded) {
            db.execSQL("ALTER TABLE seen ADD COLUMN added INTEGER NOT NULL DEFAULT 0")
        }
        val now = System.currentTimeMillis()
        db.execSQL("UPDATE seen SET added=? WHERE added=0", arrayOf(now.toString()))
        db.execSQL("CREATE INDEX IF NOT EXISTS completed_idx ON memos(completed)")
    }
    fun seen(source: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM seen WHERE source=?", arrayOf(source)).use { it.moveToFirst() }
    fun idForSource(source: String): Long = readableDatabase.rawQuery("SELECT id FROM memos WHERE source=?", arrayOf(source)).use { if (it.moveToFirst()) it.getLong(0) else -1 }
    fun updateUrl(id: Long, url: String) { writableDatabase.update("memos", ContentValues().apply { put("url", url) }, "id=?", arrayOf(id.toString())) }
    fun remember(source: String, now: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        // Backfill timestamp for rows created before v2.
        val updated = db.update("seen", ContentValues().apply { put("added", now) }, "source=? AND added=0", arrayOf(source))
        if (updated == 0) {
            db.insertWithOnConflict("seen", null, ContentValues().apply { put("source", source); put("added", now) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
    /** Delete stale seen rows. Keeps sources still referenced by memos. Returns deleted count. */
    fun pruneSeen(activeSources: Set<String> = emptySet(), now: Long = System.currentTimeMillis()): Int {
        val db = writableDatabase
        var deleted = 0
        // 1) Drop seen rows whose memo is gone and which are old.
        val cutoff = now - SEEN_MAX_AGE_DAYS * 86400000L
        deleted += db.delete("seen", "added < ? AND source NOT IN (SELECT source FROM memos)", arrayOf(cutoff.toString()))
        // 2) Hard cap: delete oldest beyond max entries (excluding active memos).
        val count = db.rawQuery("SELECT COUNT(*) FROM seen", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
        if (count > SEEN_MAX_ENTRIES) {
            val overflow = (count - SEEN_MAX_ENTRIES).toInt()
            val quoted = activeSources.take(1000).map { "'${it.replace("'", "''")}'" }
            val exclusion = if (quoted.isEmpty()) "" else "AND source NOT IN (${quoted.joinToString(",")}) "
            deleted += db.delete(
                "seen",
                "source NOT IN (SELECT source FROM memos) ${exclusion}AND rowid IN (SELECT rowid FROM seen ORDER BY added ASC LIMIT ?)",
                arrayOf(overflow.toString()),
            )
        }
        return deleted
    }
    fun add(source: String, path: String, title: String, url: String, due: Long): Long {
        val value = ContentValues().apply {
            put("source", source); put("path", path); put("title", title); put("url", url); put("created", System.currentTimeMillis()); put("due", due)
        }
        val id = writableDatabase.insertWithOnConflict("memos", null, value, SQLiteDatabase.CONFLICT_IGNORE)
        if (id != -1L) { remember(source); return id }
        return readableDatabase.rawQuery("SELECT id FROM memos WHERE source=?", arrayOf(source)).use { if (it.moveToFirst()) it.getLong(0) else -1 }
    }
    fun ocr(id: Long, text: String, candidates: List<Long>, error: String = "") {
        writableDatabase.update("memos", ContentValues().apply { put("ocr", text); put("candidates", JSONArray(candidates).toString()); put("ocrError", error) }, "id=?", arrayOf(id.toString()))
    }
    fun items(): List<Map<String, Any>> = readableDatabase.rawQuery("SELECT * FROM memos ORDER BY completed ASC, due ASC, created DESC", null).use { c ->
        buildList { while (c.moveToNext()) {
            try {
                val json = JSONArray(c.getString(c.getColumnIndexOrThrow("candidates")))
                add(mapOf("id" to c.getLong(c.getColumnIndexOrThrow("id")), "path" to c.getString(c.getColumnIndexOrThrow("path")),
                    "title" to c.getString(c.getColumnIndexOrThrow("title")), "url" to c.getString(c.getColumnIndexOrThrow("url")),
                    "ocr" to c.getString(c.getColumnIndexOrThrow("ocr")), "ocrError" to c.getString(c.getColumnIndexOrThrow("ocrError")),
                    "created" to c.getLong(c.getColumnIndexOrThrow("created")), "due" to c.getLong(c.getColumnIndexOrThrow("due")),
                    "delivered" to c.getLong(c.getColumnIndexOrThrow("delivered")), "completed" to c.getLong(c.getColumnIndexOrThrow("completed")),
                    "custom" to (c.getInt(c.getColumnIndexOrThrow("custom")) == 1), "candidates" to (0 until json.length()).map { json.getLong(it) }))
            } catch (_: Exception) {
                // Skip a single corrupt row instead of failing the whole snapshot.
            }
        } }
    }
    fun schedule(id: Long, due: Long) {
        require(due > System.currentTimeMillis()) { "これからの日時を選んでください" }
        writableDatabase.update("memos", ContentValues().apply { put("due", due); put("custom", 1); put("delivered", 0); put("completed", 0) }, "id=?", arrayOf(id.toString()))
    }
    fun complete(id: Long) { writableDatabase.update("memos", ContentValues().apply { put("completed", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString())) }
    fun delete(id: Long) {
        readableDatabase.rawQuery("SELECT path FROM memos WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) File(it.getString(0)).delete() }
        writableDatabase.delete("memos", "id=?", arrayOf(id.toString()))
    }
    /** SQL-first expiry: avoids loading every memo into memory (#11). Returns deleted ids. */
    fun deleteExpiredCopies(now: Long = System.currentTimeMillis(), retentionDays: Long = RETENTION_DAYS): List<Long> {
        val cutoff = now - retentionDays * 86400000L
        val ids = readableDatabase.rawQuery("SELECT id, path FROM memos WHERE completed > 0 AND completed < ?", arrayOf(cutoff.toString())).use { c ->
            buildList { while (c.moveToNext()) {
                try { File(c.getString(1)).delete() } catch (_: Exception) { }
                add(c.getLong(0))
            } }
        }
        if (ids.isNotEmpty()) {
            val placeholders = ids.joinToString(",") { "?" }
            writableDatabase.delete("memos", "id IN ($placeholders)", ids.map { it.toString() }.toTypedArray())
        }
        return ids
    }
    fun cleanup(now: Long = System.currentTimeMillis()) { deleteExpiredCopies(now) }
    fun nextDue(): Long? = readableDatabase.rawQuery("SELECT MIN(due) FROM memos WHERE completed=0 AND delivered=0", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    fun due(now: Long): List<Long> = readableDatabase.rawQuery("SELECT id FROM memos WHERE completed=0 AND delivered=0 AND due<=?", arrayOf(now.toString())).use { c -> buildList { while(c.moveToNext()) add(c.getLong(0)) } }
    fun delivered(ids: List<Long>) { for (id in ids) writableDatabase.update("memos", ContentValues().apply { put("delivered", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString())) }
    fun rescheduleDefaults(due: Long) { writableDatabase.update("memos", ContentValues().apply { put("due", due) }, "completed=0 AND delivered=0 AND custom=0", null) }
}
