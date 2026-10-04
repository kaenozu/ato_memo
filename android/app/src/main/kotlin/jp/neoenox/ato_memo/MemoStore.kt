package jp.neoenox.ato_memo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File

class MemoStore(context: Context) : SQLiteOpenHelper(context, "memos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memos(id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT UNIQUE NOT NULL, path TEXT NOT NULL, title TEXT NOT NULL, url TEXT NOT NULL DEFAULT '', ocr TEXT NOT NULL DEFAULT '', ocrError TEXT NOT NULL DEFAULT '', candidates TEXT NOT NULL DEFAULT '[]', created INTEGER NOT NULL, due INTEGER NOT NULL, custom INTEGER NOT NULL DEFAULT 0, delivered INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX reminders ON memos(completed,delivered,due)")
        db.execSQL("CREATE TABLE seen(source TEXT PRIMARY KEY)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Unsupported database migration") }
    fun seen(source: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM seen WHERE source=?", arrayOf(source)).use { it.moveToFirst() }
    fun idForSource(source: String): Long = readableDatabase.rawQuery("SELECT id FROM memos WHERE source=?", arrayOf(source)).use { if (it.moveToFirst()) it.getLong(0) else -1 }
    fun remember(source: String) { writableDatabase.insertWithOnConflict("seen", null, ContentValues().apply { put("source", source) }, SQLiteDatabase.CONFLICT_IGNORE) }
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
            val json = JSONArray(c.getString(c.getColumnIndexOrThrow("candidates")))
            add(mapOf("id" to c.getLong(c.getColumnIndexOrThrow("id")), "path" to c.getString(c.getColumnIndexOrThrow("path")),
                "title" to c.getString(c.getColumnIndexOrThrow("title")), "url" to c.getString(c.getColumnIndexOrThrow("url")),
                "ocr" to c.getString(c.getColumnIndexOrThrow("ocr")), "ocrError" to c.getString(c.getColumnIndexOrThrow("ocrError")),
                "created" to c.getLong(c.getColumnIndexOrThrow("created")), "due" to c.getLong(c.getColumnIndexOrThrow("due")),
                "delivered" to c.getLong(c.getColumnIndexOrThrow("delivered")), "completed" to c.getLong(c.getColumnIndexOrThrow("completed")),
                "custom" to (c.getInt(c.getColumnIndexOrThrow("custom")) == 1), "candidates" to (0 until json.length()).map { json.getLong(it) }))
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
    fun cleanup() { items().filter { (it["completed"] as Long) in 1 until System.currentTimeMillis() - 30L * 86400000 }.forEach { delete(it["id"] as Long) } }
    fun nextDue(): Long? = readableDatabase.rawQuery("SELECT MIN(due) FROM memos WHERE completed=0 AND delivered=0", null).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    fun due(now: Long): List<Long> = readableDatabase.rawQuery("SELECT id FROM memos WHERE completed=0 AND delivered=0 AND due<=?", arrayOf(now.toString())).use { c -> buildList { while(c.moveToNext()) add(c.getLong(0)) } }
    fun delivered(ids: List<Long>) { for (id in ids) writableDatabase.update("memos", ContentValues().apply { put("delivered", System.currentTimeMillis()) }, "id=?", arrayOf(id.toString())) }
    fun rescheduleDefaults(due: Long) { writableDatabase.update("memos", ContentValues().apply { put("due", due) }, "completed=0 AND delivered=0 AND custom=0", null) }
}
