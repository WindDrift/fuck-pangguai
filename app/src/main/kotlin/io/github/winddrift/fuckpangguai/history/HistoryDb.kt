package io.github.winddrift.fuckpangguai.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.winddrift.fuckpangguai.water.WaterSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoryRecord(
    val id: Long,
    val goodsId: String,
    val deviceName: String,
    val skuId: String,
    val amount: String,
    val orderNo: String,
    val payOrderNo: String,
    val phase: String,
    val error: String,
    val startedAt: Long,
    val updatedAt: Long,
)

class HistoryDb(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                goods_id TEXT NOT NULL,
                device_name TEXT NOT NULL,
                sku_id TEXT NOT NULL,
                amount TEXT NOT NULL,
                order_no TEXT NOT NULL UNIQUE,
                pay_order_no TEXT NOT NULL,
                phase TEXT NOT NULL,
                error TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun upsert(session: WaterSession): Long {
        val values = ContentValues().apply {
            put("goods_id", session.goodsId)
            put("device_name", session.deviceName)
            put("sku_id", session.skuId)
            put("amount", session.amount)
            put("order_no", session.orderNo.ifBlank { session.msgId })
            put("pay_order_no", session.payOrderNo)
            put("phase", session.phase)
            put("error", session.error)
            put("started_at", session.startedAt)
            put("updated_at", session.updatedAt)
        }
        return writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun list(): List<HistoryRecord> {
        val result = mutableListOf<HistoryRecord>()
        readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                    goodsId = cursor.getString(cursor.getColumnIndexOrThrow("goods_id")),
                    deviceName = cursor.getString(cursor.getColumnIndexOrThrow("device_name")),
                    skuId = cursor.getString(cursor.getColumnIndexOrThrow("sku_id")),
                    amount = cursor.getString(cursor.getColumnIndexOrThrow("amount")),
                    orderNo = cursor.getString(cursor.getColumnIndexOrThrow("order_no")),
                    payOrderNo = cursor.getString(cursor.getColumnIndexOrThrow("pay_order_no")),
                    phase = cursor.getString(cursor.getColumnIndexOrThrow("phase")),
                    error = cursor.getString(cursor.getColumnIndexOrThrow("error")),
                    startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                )
            }
        }
        return result
    }

    fun delete(id: Long): Int =
        writableDatabase.delete(TABLE, "_id = ?", arrayOf(id.toString()))

    fun clear(): Int = writableDatabase.delete(TABLE, null, null)

    private companion object {
        const val DB_NAME = "pangguai_history.db"
        const val DB_VERSION = 1
        const val TABLE = "water_history"
    }
}

class HistoryRepository(context: Context) {
    private val db = HistoryDb(context)

    suspend fun save(session: WaterSession) = withContext(Dispatchers.IO) {
        db.upsert(session)
    }

    suspend fun list(): List<HistoryRecord> = withContext(Dispatchers.IO) {
        db.list()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        db.delete(id)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        db.clear()
    }
}
