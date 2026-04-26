package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.GroupId
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
        groupId: GroupId? = null,
    ): Record

    suspend fun getRecordById(ownerId: String, recordId: RecordId): Record?

    suspend fun updateRecord(
        ownerId: String,
        recordId: RecordId,
        title: String? = null,
        content: String? = null,
        groupId: GroupId? = null,
        clearGroup: Boolean = false,
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
        db.tatDatabaseQueries.selectAllActiveRecordsByOwner(ownerId).executeAsList().map { it.toModel() }
    }

    override suspend fun createRecord(ownerId: String, title: String, content: String?, groupId: GroupId?): Record =
        withContext(dbDispatcher) {
            val record = Record(
                id = RecordId(Uuid.random().toString()),
                ownerId = ownerId,
                groupId = groupId,
                title = RecordTitle(title),
                content = content,
                createdAt = Clock.System.now(),
                updatedAt = null,
                deletedAt = null,
            )
            db.tatDatabaseQueries.insertRecord(
                id = record.id.id,
                owner_id = ownerId,
                group_id = groupId?.id,
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
        dbItem.toModel()
    }

    override suspend fun updateRecord(
        ownerId: String,
        recordId: RecordId,
        title: String?,
        content: String?,
        groupId: GroupId?,
        clearGroup: Boolean,
    ): Record? = withContext(dbDispatcher) {
        val current = db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull()
            ?: return@withContext null
        if (current.owner_id != ownerId) return@withContext null
        val resolvedGroupId = when {
            clearGroup -> null
            groupId != null -> groupId.id
            else -> current.group_id
        }
        db.tatDatabaseQueries.updateRecord(
            title = title ?: current.title,
            content = content ?: current.content,
            group_id = resolvedGroupId,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = recordId.id,
            owner_id = ownerId,
        )
        db.tatDatabaseQueries.selectRecordById(recordId.id).executeAsOneOrNull()?.toModel()
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

private fun xyz.qwexter.db.Record.toModel() = Record(
    id = RecordId(id),
    ownerId = owner_id,
    groupId = group_id?.let { GroupId(it) },
    title = RecordTitle(title),
    content = content,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = record_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)

private class InMemoryRecords(initial: List<Record>) : RecordsRepository {

    private val all = initial.toMutableList()

    override suspend fun allActiveRecords(ownerId: String): List<Record> =
        all.filter { it.ownerId == ownerId && it.deletedAt == null }.toList()

    override suspend fun createRecord(ownerId: String, title: String, content: String?, groupId: GroupId?): Record {
        val record = Record(
            id = RecordId(Uuid.random().toString()),
            ownerId = ownerId,
            groupId = groupId,
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

    override suspend fun updateRecord(
        ownerId: String,
        recordId: RecordId,
        title: String?,
        content: String?,
        groupId: GroupId?,
        clearGroup: Boolean,
    ): Record? {
        val idx = all.indexOfFirst { it.id == recordId && it.ownerId == ownerId && it.deletedAt == null }
        if (idx == -1) return null
        val current = all[idx]
        val resolvedGroupId = when {
            clearGroup -> null
            groupId != null -> groupId
            else -> current.groupId
        }
        val updated = current.copy(
            title = if (title != null) RecordTitle(title) else current.title,
            content = content ?: current.content,
            groupId = resolvedGroupId,
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
