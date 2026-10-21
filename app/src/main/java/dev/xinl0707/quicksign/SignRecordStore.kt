package dev.xinl0707.quicksign

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * 签到记录本地存储：SQLite（记录）+ 内部文件（网页截图）。
 * 零第三方依赖，仅使用系统 SQLiteOpenHelper。
 *
 * - 库：sign_records.db，单表 records，按提交时间倒序查询
 * - 截图：filesDir/signshots/rec_<id>.jpg（JPEG 质量 82，长边 ≤ 1080px）
 * - 上限：SignRecord.STATUS_MAX 条，超出清理最旧记录并连带删除其截图
 */
class SignRecordStore(context: Context) : SQLiteOpenHelper(context, "sign_records.db", null, 2) {

    private val appContext = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                status INTEGER NOT NULL,
                course_name TEXT DEFAULT '',
                course_start INTEGER DEFAULT 0,
                course_end INTEGER DEFAULT 0,
                code TEXT DEFAULT '',
                method TEXT DEFAULT 'manual',
                ts_submit INTEGER NOT NULL,
                ts_result INTEGER NOT NULL,
                stats_text TEXT DEFAULT '',
                hours_text TEXT DEFAULT '',
                ratio_text TEXT DEFAULT '',
                shot_path TEXT DEFAULT '',
                app_version TEXT DEFAULT '',
                note TEXT DEFAULT '',
                asr_text TEXT DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ts_submit ON records(ts_submit DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 → v2：语音签到详情需展示识别依据（MIMO 转写文本）
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE records ADD COLUMN asr_text TEXT DEFAULT ''")
            } catch (e: Exception) {}
        }
    }

    /** 写入一条记录，返回自增 id（截图文件命名依赖） */
    fun insert(rec: SignRecord): Long {
        val cv = ContentValues().apply {
            put("status", rec.status)
            put("course_name", rec.courseName)
            put("course_start", rec.courseStart)
            put("course_end", rec.courseEnd)
            put("code", rec.code)
            put("method", rec.method)
            put("ts_submit", rec.tsSubmit)
            put("ts_result", rec.tsResult)
            put("stats_text", rec.statsText)
            put("hours_text", rec.hoursText)
            put("ratio_text", rec.ratioText)
            put("shot_path", rec.shotPath)
            put("app_version", rec.appVersion)
            put("note", rec.note)
            put("asr_text", rec.asrText)
        }
        val id = writableDatabase.insert("records", null, cv)
        if (id > 0) deleteOldIfNeeded()
        return id
    }

    /** 按提交时间倒序取记录（最新在上） */
    fun list(limit: Int = 200, offset: Int = 0): List<SignRecord> {
        val out = mutableListOf<SignRecord>()
        val db = readableDatabase
        db.query(
            "records", null, null, null, null, null, "ts_submit DESC",
            "$offset, $limit"
        ).use { c ->
            while (c.moveToNext()) {
                out.add(recordFrom(c))
            }
        }
        return out
    }

    fun get(id: Long): SignRecord? {
        val db = readableDatabase
        db.query("records", null, "id=?", arrayOf(id.toString()), null, null, null, "1")
            .use { c ->
                return if (c.moveToFirst()) recordFrom(c) else null
            }
    }

    /** 记录总数（列表页可显示统计） */
    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM records", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** 删除一条记录（连同其截图文件）；返回是否删除成功 */
    fun deleteRecord(id: Long): Boolean {
        val rec = get(id)
        val deleted = writableDatabase.delete("records", "id=?", arrayOf(id.toString())) > 0
        if (deleted && rec != null && rec.shotPath.isNotBlank()) {
            try { shotFile(rec.shotPath).delete() } catch (e: Exception) {}
        }
        return deleted
    }

    private fun recordFrom(c: android.database.Cursor): SignRecord = SignRecord(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        status = c.getInt(c.getColumnIndexOrThrow("status")),
        courseName = c.getString(c.getColumnIndexOrThrow("course_name")),
        courseStart = c.getLong(c.getColumnIndexOrThrow("course_start")),
        courseEnd = c.getLong(c.getColumnIndexOrThrow("course_end")),
        code = c.getString(c.getColumnIndexOrThrow("code")),
        method = c.getString(c.getColumnIndexOrThrow("method")),
        tsSubmit = c.getLong(c.getColumnIndexOrThrow("ts_submit")),
        tsResult = c.getLong(c.getColumnIndexOrThrow("ts_result")),
        statsText = c.getString(c.getColumnIndexOrThrow("stats_text")),
        hoursText = c.getString(c.getColumnIndexOrThrow("hours_text")),
        ratioText = c.getString(c.getColumnIndexOrThrow("ratio_text")),
        shotPath = c.getString(c.getColumnIndexOrThrow("shot_path")),
        appVersion = c.getString(c.getColumnIndexOrThrow("app_version")),
        note = c.getString(c.getColumnIndexOrThrow("note")),
        asrText = try { c.getString(c.getColumnIndexOrThrow("asr_text")) } catch (e: Exception) { "" }
    )

    /** 记录写入后补截图路径（截图命名依赖自增 id，故先 insert 再 update） */
    fun updateShot(id: Long, relativePath: String) {
        try {
            writableDatabase.update("records", ContentValues().apply {
                put("shot_path", relativePath)
            }, "id=?", arrayOf(id.toString()))
        } catch (e: Exception) {}
    }

    /** 超过上限：删除最旧记录并连带删除其截图文件 */
    fun deleteOldIfNeeded(max: Int = SignRecord.STATUS_MAX) {
        val db = writableDatabase
        db.rawQuery("SELECT id FROM records ORDER BY ts_submit ASC LIMIT -1 OFFSET $max", null)
            .use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val rec = get(id)
                    rec?.shotPath?.takeIf { it.isNotEmpty() }?.let { path ->
                        try { File(appContext.filesDir, path).delete() } catch (e: Exception) {}
                    }
                    db.delete("records", "id=?", arrayOf(id.toString()))
                }
            }
    }

    /** 截图目录（filesDir/signshots/），不存在则创建 */
    fun shotDir(): File {
        val dir = File(appContext.filesDir, "signshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 保存网页截图为 JPEG（质量 82，长边 ≤ 1080px 压缩），返回相对路径；失败返回 null */
    fun saveShot(id: Long, bmp: Bitmap): String? {
        return try {
            val scale = 1080f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val out: Bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else bmp
            val f = File(shotDir(), "rec_$id.jpg")
            FileOutputStream(f).use { fos ->
                out.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            if (out !== bmp) out.recycle()
            bmp.recycle()
            "signshots/rec_$id.jpg"
        } catch (e: Exception) {
            null
        }
    }

    /** 按相对路径取截图文件 */
    fun shotFile(relativePath: String): File = File(appContext.filesDir, relativePath)
}
