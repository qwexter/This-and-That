package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.Record
import xyz.qwexter.tat.models.RecordId
import xyz.qwexter.tat.models.RecordTitle
import kotlin.uuid.Uuid

interface RecordsRepository {
    suspend fun allActiveRecords(ownerId: String): List<Record>

    suspend fun createRecord(
        ownerId: String,
        title: String,
        content: String?,
    ): Record

    suspend fun getRecordById(ownerId: String, recordId: RecordId): Record?

    suspend fun updateRecord(
        ownerId: String,
        recordId: RecordId,
        title: String? = null,
        content: String? = null,
    ): Record?

    suspend fun deleteRecord(ownerId: String, recordId: RecordId): Boolean

    companion object {
        fun buildInMemory(initial: List<Record>): RecordsRepository = InMemoryRecords(initial)

        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): RecordsRepository = DbRecordsRepository(db = db, dbDispatcher = dbDispatcher)
    }
}

private class DbRecordsRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : RecordsRepository {

    override suspend fun allActiveRecords(ownerId: String): List<Record> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectAllActiveRecordsByOwner(ownerId).executeAsList().map { row ->
            recordFromDb(
                row.id,
                row.owner_id,
                row.title,
                row.content,
                row.created_at,
                row.updated_at,
                row.record_deleted_at,
            )
        }
    }

    override suspend fun createRecord(ownerId: String, title: String, content: String?): Record =
        withContext(dbDispatcher) {
            val record = Record(
                id = RecordId(Uuid.random().toString()),
                ownerId = ownerId,
                title = RecordTitle(title),
                content = content,
                createdAt = Clock.System.now(),
                updatedAt = null,
                deletedAt = null,
            )
            db.tatDatabaseQueries.insertRecord(
                id = record.id.id,
                owner_id = ownerId,
                title = record.title.title,
                content = record.content,
                created_at = record.createdAt.toEpochMilliseconds(),
                updated_at = null,
            )
            record
        }

    override suspend fun getRecordById(ownerId: String, recordId: RecordId): Record? = withContext(dbDispatcher) {
        val dbItem = db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull() ?: return@withContext null
        if (dbItem.owner_id != ownerId) return@withContext null
        recordFromDb(
            dbItem.id,
            dbItem.owner_id,
            dbItem.title,
            dbItem.content,
            dbItem.created_at,
            dbItem.updated_at,
            dbItem.record_deleted_at,
        )
    }

    override suspend fun updateRecord(ownerId: String, recordId: RecordId, title: String?, content: String?): Record? =
        withContext(dbDispatcher) {
            val current = db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull()
                ?: return@withContext null
            if (current.owner_id != ownerId) return@withContext null
            db.tatDatabaseQueries.updateRecord(
                title = title ?: current.title,
                content = content ?: current.content,
                updated_at = Clock.System.now().toEpochMilliseconds(),
                id = recordId.id,
                owner_id = ownerId,
            )
            db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull()?.let { row ->
                recordFromDb(
                    row.id,
                    row.owner_id,
                    row.title,
                    row.content,
                    row.created_at,
                    row.updated_at,
                    row.record_deleted_at,
                )
            }
        }

    override suspend fun deleteRecord(ownerId: String, recordId: RecordId): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull()
        if (existing == null || existing.record_deleted_at != null || existing.owner_id != ownerId) {
            return@withContext false
        }
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.softDeleteRecord(
            record_deleted_at = now,
            updated_at = now,
            id = recordId.id,
            owner_id = ownerId,
        )
        true
    }
}

private fun recordFromDb(
    id: String,
    ownerId: String,
    title: String,
    content: String?,
    createdAt: Long,
    updatedAt: Long?,
    deletedAt: Long?,
): Record = Record(
    id = RecordId(id),
    ownerId = ownerId,
    title = RecordTitle(title),
    content = content,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = updatedAt?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = deletedAt?.let { Instant.fromEpochMilliseconds(it) },
)

private class InMemoryRecords(initial: List<Record>) : RecordsRepository {

    private val all = initial.toMutableList()

    override suspend fun allActiveRecords(ownerId: String): List<Record> =
        all.filter { it.ownerId == ownerId && it.deletedAt == null }.toList()

    override suspend fun createRecord(ownerId: String, title: String, content: String?): Record {
        val record = Record(
            id = RecordId(Uuid.random().toString()),
            ownerId = ownerId,
            title = RecordTitle(title),
            content = content,
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
        )
        all += record
        return record
    }

    override suspend fun getRecordById(ownerId: String, recordId: RecordId): Record? =
        all.find { it.id == recordId && it.ownerId == ownerId }

    override suspend fun updateRecord(ownerId: String, recordId: RecordId, title: String?, content: String?): Record? {
        val idx = all.indexOfFirst { it.id == recordId && it.ownerId == ownerId && it.deletedAt == null }
        if (idx == -1) return null
        val current = all[idx]
        val updated = current.copy(
            title = if (title != null) RecordTitle(title) else current.title,
            content = content ?: current.content,
            updatedAt = Clock.System.now(),
        )
        all[idx] = updated
        return updated
    }

    override suspend fun deleteRecord(ownerId: String, recordId: RecordId): Boolean {
        val idx = all.indexOfFirst { it.id == recordId && it.ownerId == ownerId && it.deletedAt == null }
        if (idx == -1) return false
        val now = Clock.System.now()
        all[idx] = all[idx].copy(deletedAt = now, updatedAt = now)
        return true
    }
}
