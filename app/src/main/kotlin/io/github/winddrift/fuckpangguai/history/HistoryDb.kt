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
    val payOrderId: String,
    val identify: String,
    val phase: String,
    val error: String,
    val startedAt: Long,
    val updatedAt: Long,
    val billState: BillState,
    val bill: BillInfo?,
    val billError: String,
    val billFetchedAt: Long,
)

internal data class HistoryImportCounts(
    val inserted: Int,
    val duplicates: Int,
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
                pay_order_id TEXT NOT NULL DEFAULT '',
                identify TEXT NOT NULL DEFAULT '',
                phase TEXT NOT NULL,
                error TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                bill_state TEXT NOT NULL DEFAULT 'none',
                bill_json TEXT NOT NULL DEFAULT '',
                bill_error TEXT NOT NULL DEFAULT '',
                bill_fetched_at INTEGER NOT NULL DEFAULT 0
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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN pay_order_id TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN identify TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN bill_state TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN bill_json TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN bill_error TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN bill_fetched_at INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun upsert(session: WaterSession, accountId: String): Long {
        val orderKey = session.orderNo
            .ifBlank { session.msgId }
            .ifBlank { "local-${session.startedAt}" }
        val values = ContentValues().apply {
            put("account_id", accountId)
            put("goods_id", session.goodsId)
            put("device_name", session.deviceName)
            put("sku_id", session.skuId)
            put("amount", session.amount)
            put("order_no", orderKey)
            put("pay_order_no", session.payOrderNo)
            put("pay_order_id", session.payOrderId)
            put("identify", session.identify)
            put("phase", session.phase)
            put("error", session.error)
            put("started_at", session.startedAt)
            put("updated_at", session.updatedAt)
        }
        val existingId = readableDatabase.query(
            TABLE,
            arrayOf("_id"),
            "order_no = ?",
            arrayOf(orderKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
        if (existingId != null) {
            writableDatabase.update(TABLE, values, "_id = ?", arrayOf(existingId.toString()))
            return existingId
        }
        return writableDatabase.insertOrThrow(TABLE, null, values)
    }

    internal fun insertImported(
        accountId: String,
        rows: List<ImportHistoryRow>,
    ): HistoryImportCounts {
        if (accountId.isBlank() || rows.isEmpty()) return HistoryImportCounts(0, 0)
        var inserted = 0
        var duplicates = 0
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach { row ->
                val values = ContentValues().apply {
                    put("account_id", accountId)
                    put("goods_id", row.goodsId)
                    put("device_name", row.deviceName)
                    put("sku_id", row.skuId)
                    put("amount", row.amount)
                    put("order_no", row.orderNo)
                    put("pay_order_no", row.payOrderNo)
                    put("phase", row.phase)
                    put("error", row.error)
                    put("started_at", row.startedAt)
                    put("updated_at", row.updatedAt)
                }
                val id = db.insertWithOnConflict(
                    TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                if (id == -1L) duplicates++ else inserted++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return HistoryImportCounts(inserted = inserted, duplicates = duplicates)
    }

    fun count(accountId: String): Int {
        if (accountId.isBlank()) return 0
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE WHERE account_id = ?",
            arrayOf(accountId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
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
                val rawBill = cursor.getString(cursor.getColumnIndexOrThrow("bill_json"))
                result += HistoryRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                    accountId = cursor.getString(cursor.getColumnIndexOrThrow("account_id")),
                    goodsId = cursor.getString(cursor.getColumnIndexOrThrow("goods_id")),
                    deviceName = cursor.getString(cursor.getColumnIndexOrThrow("device_name")),
                    skuId = cursor.getString(cursor.getColumnIndexOrThrow("sku_id")),
                    amount = cursor.getString(cursor.getColumnIndexOrThrow("amount")),
                    orderNo = cursor.getString(cursor.getColumnIndexOrThrow("order_no")),
                    payOrderNo = cursor.getString(cursor.getColumnIndexOrThrow("pay_order_no")),
                    payOrderId = cursor.getString(cursor.getColumnIndexOrThrow("pay_order_id")),
                    identify = cursor.getString(cursor.getColumnIndexOrThrow("identify")),
                    phase = cursor.getString(cursor.getColumnIndexOrThrow("phase")),
                    error = cursor.getString(cursor.getColumnIndexOrThrow("error")),
                    startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                    updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                    billState = BillState.fromStorage(
                        cursor.getString(cursor.getColumnIndexOrThrow("bill_state")),
                    ),
                    bill = BillCodec.decode(rawBill),
                    billError = cursor.getString(cursor.getColumnIndexOrThrow("bill_error")),
                    billFetchedAt = cursor.getLong(cursor.getColumnIndexOrThrow("bill_fetched_at")),
                )
            }
        }
        return result
    }

    fun saveBill(id: Long, accountId: String, bill: BillInfo): Int {
        val values = ContentValues().apply {
            put("bill_state", BillState.MATCHED.storage)
            put("bill_json", BillCodec.encode(bill))
            put("bill_error", "")
            put("bill_fetched_at", bill.fetchedAt)
        }
        return writableDatabase.update(
            TABLE,
            values,
            "_id = ? AND account_id = ?",
            arrayOf(id.toString(), accountId),
        )
    }

    fun markBillUnmatched(id: Long, accountId: String, message: String, fetchedAt: Long): Int {
        val values = ContentValues().apply {
            put("bill_state", BillState.UNMATCHED.storage)
            put("bill_json", "")
            put("bill_error", message)
            put("bill_fetched_at", fetchedAt)
        }
        return writableDatabase.update(
            TABLE,
            values,
            "_id = ? AND account_id = ?",
            arrayOf(id.toString(), accountId),
        )
    }

    fun markBillError(id: Long, accountId: String, message: String, fetchedAt: Long): Int {
        val values = ContentValues().apply {
            put("bill_state", BillState.ERROR.storage)
            put("bill_error", message)
            put("bill_fetched_at", fetchedAt)
        }
        return writableDatabase.update(
            TABLE,
            values,
            "_id = ? AND account_id = ?",
            arrayOf(id.toString(), accountId),
        )
    }

    fun markBillRefreshError(id: Long, accountId: String, message: String, fetchedAt: Long): Int {
        val values = ContentValues().apply {
            put("bill_error", message)
            put("bill_fetched_at", fetchedAt)
        }
        return writableDatabase.update(
            TABLE,
            values,
            "_id = ? AND account_id = ?",
            arrayOf(id.toString(), accountId),
        )
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
        const val DB_VERSION = 3
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
        list(accountId)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId() ?: return@withContext 0
        ensureBackfill(accountId)
        db.count(accountId)
    }

    suspend fun importCsv(csv: String): HistoryImportResult = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId()
            ?: return@withContext HistoryImportResult(formatError = true)
        when (val parsed = parseHistoryCsv(csv)) {
            HistoryCsvParseResult.InvalidFormat -> HistoryImportResult(formatError = true)
            is HistoryCsvParseResult.Success -> {
                ensureBackfill(accountId)
                val counts = db.insertImported(accountId, parsed.rows)
                HistoryImportResult(
                    inserted = counts.inserted,
                    duplicates = counts.duplicates,
                    invalid = parsed.invalidRows,
                )
            }
        }
    }

    suspend fun list(accountId: String): List<HistoryRecord> = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext emptyList()
        ensureBackfill(accountId)
        db.list(accountId)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val accountId = accountStore.activeLocalId() ?: return@withContext 0
        db.delete(id, accountId)
    }

    suspend fun delete(id: Long, accountId: String) = withContext(Dispatchers.IO) {
        if (accountId.isBlank()) return@withContext 0
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

    suspend fun saveBill(id: Long, accountId: String, bill: BillInfo) = withContext(Dispatchers.IO) {
        if (accountId.isNotBlank()) db.saveBill(id, accountId, bill)
    }

    suspend fun markBillUnmatched(
        id: Long,
        accountId: String,
        message: String,
        fetchedAt: Long,
    ) = withContext(Dispatchers.IO) {
        if (accountId.isNotBlank()) db.markBillUnmatched(id, accountId, message, fetchedAt)
    }

    suspend fun markBillError(
        id: Long,
        accountId: String,
        message: String,
        fetchedAt: Long,
    ) = withContext(Dispatchers.IO) {
        if (accountId.isNotBlank()) db.markBillError(id, accountId, message, fetchedAt)
    }

    suspend fun markBillRefreshError(
        id: Long,
        accountId: String,
        message: String,
        fetchedAt: Long,
    ) = withContext(Dispatchers.IO) {
        if (accountId.isNotBlank()) db.markBillRefreshError(id, accountId, message, fetchedAt)
    }

    private fun ensureBackfill(accountId: String) {
        if (backfilledFor == accountId) return
        db.backfillLegacy(accountId)
        backfilledFor = accountId
    }
}
