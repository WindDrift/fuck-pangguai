package io.github.winddrift.fuckpangguai.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.winddrift.fuckpangguai.account.AccountStore
import io.github.winddrift.fuckpangguai.water.WaterSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HistoryRecord(
    val id: Long,
    val accountId: String,
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
                account_id TEXT NOT NULL DEFAULT '',
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
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_${TABLE}_account_updated " +
                "ON $TABLE(account_id, updated_at DESC)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN account_id TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_${TABLE}_account_updated " +
                    "ON $TABLE(account_id, updated_at DESC)",
            )
        }
    }

    fun upsert(session: WaterSession, accountId: String): Long {
        val values = ContentValues().apply {
            put("account_id", accountId)
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

    fun list(accountId: String): List<HistoryRecord> {
        val result = mutableListOf<HistoryRecord>()
        readableDatabase.query(
            TABLE,
            null,
            "account_id = ?",
            arrayOf(accountId),
            null,
            null,
            "updated_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += HistoryRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                    accountId = cursor.getString(cursor.getColumnIndexOrThrow("account_id")),
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

    fun delete(id: Long, accountId: String): Int =
        writableDatabase.delete(
            TABLE,
            "_id = ? AND account_id = ?",
            arrayOf(id.toString(), accountId),
        )

    fun clear(accountId: String): Int =
        writableDatabase.delete(TABLE, "account_id = ?", arrayOf(accountId))

    fun backfillLegacy(accountId: String): Int {
        if (accountId.isBlank()) return 0
        val values = ContentValues().apply { put("account_id", accountId) }
        return writableDatabase.update(TABLE, values, "account_id = ''", null)
    }

    fun reassignAccount(from: String, to: String): Int {
        if (from.isBlank() || to.isBlank() || from == to) return 0
        val values = ContentValues().apply { put("account_id", to) }
        return writableDatabase.update(TABLE, values, "account_id = ?", arrayOf(from))
    }

    private companion object {
        const val DB_NAME = "pangguai_history.db"
        const val DB_VERSION = 2
        const val TABLE = "water_history"
    }
}

class HistoryRepository(
    context: Context,
    private val accountStore: AccountStore,
) {
    private val db = HistoryDb(context)

    @Volatile
    private var backfilledFor: String? = null

    suspend fun save(session: WaterSession) = withContext(Dispatchers.IO) {
        val accountId = session.accountId.ifBlank { accountStore.activeLocalId().orEmpty() }
        if (accountId.isNotBlank()) ensureBackfill(accountId)
        db.upsert(session, accountId)
    }

    suspend fun list(): List<HistoryRecord> = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId() ?: return@withContext emptyList()
        ensureBackfill(accountId)
        db.list(accountId)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId() ?: return@withContext 0
        db.delete(id, accountId)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId() ?: return@withContext 0
        db.clear(accountId)
    }

    suspend fun clearForAccount(localId: String) = withContext(Dispatchers.IO) {
        if (localId.isBlank()) return@withContext 0
        db.clear(localId)
    }

    suspend fun reassignAccount(fromLocalId: String, toLocalId: String) = withContext(Dispatchers.IO) {
        db.reassignAccount(fromLocalId, toLocalId)
    }

    private fun ensureBackfill(accountId: String) {
        if (backfilledFor == accountId) return
        db.backfillLegacy(accountId)
        backfilledFor = accountId
    }
}
