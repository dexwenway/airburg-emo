package dev.amor.emomonitor.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SampleDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE sample (
                bucket INTEGER PRIMARY KEY,
                ts INTEGER NOT NULL,
                pm25 REAL NOT NULL,
                particles INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sample")
        onCreate(db)
    }

    /** 每分钟只保留一条，重复写入同一分钟时覆盖。 */
    fun insert(reading: EmoReading) {
        val values = ContentValues().apply {
            put("bucket", reading.timestamp / MINUTE_MS)
            put("ts", reading.timestamp)
            put("pm25", reading.pm25)
            put("particles", reading.particles)
        }
        writableDatabase.insertWithOnConflict("sample", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun samplesSince(from: Long): List<EmoReading> {
        val rows = mutableListOf<EmoReading>()
        readableDatabase.query(
            "sample",
            arrayOf("ts", "pm25", "particles"),
            "ts >= ?",
            arrayOf(from.toString()),
            null,
            null,
            "ts ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows += EmoReading(
                    pm25 = cursor.getDouble(1),
                    particles = cursor.getInt(2),
                    timestamp = cursor.getLong(0),
                )
            }
        }
        return rows
    }

    fun pruneBefore(time: Long) {
        writableDatabase.delete("sample", "ts < ?", arrayOf(time.toString()))
    }

    fun clear() {
        writableDatabase.delete("sample", null, null)
    }

    private companion object {
        const val NAME = "emo_samples.db"
        const val VERSION = 1
        const val MINUTE_MS = 60_000L
    }
}
