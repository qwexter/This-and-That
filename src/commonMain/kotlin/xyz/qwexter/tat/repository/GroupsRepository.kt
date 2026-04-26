package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.Group
import xyz.qwexter.tat.models.GroupId
import xyz.qwexter.tat.models.GroupTitle
import xyz.qwexter.tat.models.Record
import xyz.qwexter.tat.models.RecordId
import xyz.qwexter.tat.models.RecordTitle
import xyz.qwexter.tat.models.Task
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import kotlin.uuid.Uuid

sealed class GroupItemInput {
    data class NewTask(
        val name: String,
        val description: String?,
        val priority: TaskPriority,
        val deadline: LocalDateTime?,
    ) : GroupItemInput()

    data class NewRecord(
        val title: String,
        val content: String?,
    ) : GroupItemInput()

    data class ExistingTask(val taskId: TaskId) : GroupItemInput()
    data class ExistingRecord(val recordId: RecordId) : GroupItemInput()
}

sealed class GroupItemResult {
    data class TaskResult(val task: Task) : GroupItemResult()
    data class RecordResult(val record: Record) : GroupItemResult()
}

sealed class AddItemsError {
    data class TaskNotFound(val taskId: TaskId) : AddItemsError()
    data class RecordNotFound(val recordId: RecordId) : AddItemsError()
    data class TaskInOtherGroup(val taskId: TaskId, val currentGroupId: GroupId) : AddItemsError()
    data class RecordInOtherGroup(val recordId: RecordId, val currentGroupId: GroupId) : AddItemsError()
}

interface GroupsRepository {
    suspend fun allActiveGroups(ownerId: String): List<Group>
    suspend fun createGroup(ownerId: String, title: String): Group
    suspend fun getGroupById(ownerId: String, groupId: GroupId): Group?
    suspend fun updateGroup(ownerId: String, groupId: GroupId, title: String): Group?
    suspend fun deleteGroup(ownerId: String, groupId: GroupId): Boolean
    suspend fun addItemsToGroup(
        ownerId: String,
        groupId: GroupId,
        items: List<GroupItemInput>,
    ): Either<AddItemsError, List<GroupItemResult>>

    companion object {
        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): GroupsRepository = DbGroupsRepository(db = db, dbDispatcher = dbDispatcher)
    }
}

sealed class Either<out E, out A> {
    data class Left<E>(val error: E) : Either<E, Nothing>()
    data class Right<A>(val value: A) : Either<Nothing, A>()
}

private class DbGroupsRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : GroupsRepository {

    override suspend fun allActiveGroups(ownerId: String): List<Group> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectAllActiveGroupsByOwner(ownerId).executeAsList().map { it.toModel() }
    }

    override suspend fun createGroup(ownerId: String, title: String): Group = withContext(dbDispatcher) {
        val group = Group(
            id = GroupId(Uuid.random().toString()),
            ownerId = ownerId,
            title = GroupTitle(title),
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
        )
        db.tatDatabaseQueries.insertGroup(
            id = group.id.id,
            owner_id = ownerId,
            title = group.title.title,
            created_at = group.createdAt.toEpochMilliseconds(),
        )
        group
    }

    override suspend fun getGroupById(ownerId: String, groupId: GroupId): Group? = withContext(dbDispatcher) {
        val row = db.tatDatabaseQueries.selectGroupById(groupId.id).executeAsOneOrNull() ?: return@withContext null
        if (row.owner_id != ownerId) return@withContext null
        row.toModel()
    }

    override suspend fun updateGroup(ownerId: String, groupId: GroupId, title: String): Group? =
        withContext(dbDispatcher) {
            val existing = db.tatDatabaseQueries.selectGroupById(groupId.id).executeAsOneOrNull()
                ?: return@withContext null
            if (existing.owner_id != ownerId) return@withContext null
            db.tatDatabaseQueries.updateGroup(
                title = title,
                updated_at = Clock.System.now().toEpochMilliseconds(),
                id = groupId.id,
                owner_id = ownerId,
            )
            db.tatDatabaseQueries.selectGroupById(groupId.id).executeAsOneOrNull()?.toModel()
        }

    override suspend fun addItemsToGroup(
        ownerId: String,
        groupId: GroupId,
        items: List<GroupItemInput>,
    ): Either<AddItemsError, List<GroupItemResult>> = withContext(dbDispatcher) {
        for (item in items) {
            val err = validateExistingItem(item, ownerId, groupId)
            if (err != null) return@withContext Either.Left(err)
        }
        val results = mutableListOf<GroupItemResult>()
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.transaction {
            items.forEach { results += insertItem(it, ownerId, groupId, now) }
        }
        Either.Right(results)
    }

    private fun validateExistingItem(
        item: GroupItemInput,
        ownerId: String,
        groupId: GroupId,
    ): AddItemsError? = when (item) {
        is GroupItemInput.ExistingTask -> validateExistingTask(item, ownerId, groupId)
        is GroupItemInput.ExistingRecord -> validateExistingRecord(item, ownerId, groupId)
        is GroupItemInput.NewTask, is GroupItemInput.NewRecord -> null
    }

    private fun validateExistingTask(
        item: GroupItemInput.ExistingTask,
        ownerId: String,
        groupId: GroupId,
    ): AddItemsError? {
        val task = db.tatDatabaseQueries.selectTaskById(item.taskId.id).executeAsOneOrNull()
        return when {
            task == null || task.owner_id != ownerId || task.task_deleted_at != null ->
                AddItemsError.TaskNotFound(item.taskId)
            task.group_id != null && task.group_id != groupId.id ->
                AddItemsError.TaskInOtherGroup(item.taskId, GroupId(task.group_id))
            else -> null
        }
    }

    private fun validateExistingRecord(
        item: GroupItemInput.ExistingRecord,
        ownerId: String,
        groupId: GroupId,
    ): AddItemsError? {
        val record = db.tatDatabaseQueries.selectRecordById(item.recordId.id).executeAsOneOrNull()
        return when {
            record == null || record.owner_id != ownerId || record.record_deleted_at != null ->
                AddItemsError.RecordNotFound(item.recordId)
            record.group_id != null && record.group_id != groupId.id ->
                AddItemsError.RecordInOtherGroup(item.recordId, GroupId(record.group_id))
            else -> null
        }
    }

    private fun insertItem(
        item: GroupItemInput,
        ownerId: String,
        groupId: GroupId,
        now: Long,
    ): GroupItemResult = when (item) {
        is GroupItemInput.NewTask -> insertNewTask(item, ownerId, groupId)
        is GroupItemInput.NewRecord -> insertNewRecord(item, ownerId, groupId)
        is GroupItemInput.ExistingTask -> {
            db.tatDatabaseQueries.assignTaskToGroup(
                group_id = groupId.id,
                updated_at = now,
                id = item.taskId.id,
                owner_id = ownerId,
            )
            GroupItemResult.TaskResult(
                db.tatDatabaseQueries.selectTaskById(item.taskId.id).executeAsOne().toTaskModel(),
            )
        }

        is GroupItemInput.ExistingRecord -> {
            db.tatDatabaseQueries.assignRecordToGroup(
                group_id = groupId.id,
                updated_at = now,
                id = item.recordId.id,
                owner_id = ownerId,
            )
            GroupItemResult.RecordResult(
                db.tatDatabaseQueries.selectRecordById(item.recordId.id).executeAsOne().toRecordModel(),
            )
        }
    }

    private fun insertNewTask(
        item: GroupItemInput.NewTask,
        ownerId: String,
        groupId: GroupId,
    ): GroupItemResult.TaskResult {
        val id = TaskId(Uuid.random().toString())
        val createdAt = Clock.System.now()
        db.tatDatabaseQueries.insertTask(
            id = id.id, owner_id = ownerId, group_id = groupId.id,
            name = item.name, description = item.description,
            priority = item.priority.toDb(), status = TaskStatus.Todo.toDb(),
            deadline = item.deadline?.toInstant(TimeZone.UTC)?.toEpochMilliseconds(),
            created_at = createdAt.toEpochMilliseconds(), updated_at = null,
        )
        return GroupItemResult.TaskResult(
            Task(
                id = id, ownerId = ownerId, groupId = groupId, name = TaskName(item.name),
                description = item.description, status = TaskStatus.Todo, priority = item.priority,
                deadline = item.deadline, createdAt = createdAt, updatedAt = null, deletedAt = null,
            ),
        )
    }

    private fun insertNewRecord(
        item: GroupItemInput.NewRecord,
        ownerId: String,
        groupId: GroupId,
    ): GroupItemResult.RecordResult {
        val id = RecordId(Uuid.random().toString())
        val createdAt = Clock.System.now()
        db.tatDatabaseQueries.insertRecord(
            id = id.id,
            owner_id = ownerId,
            group_id = groupId.id,
            title = item.title,
            content = item.content,
            created_at = createdAt.toEpochMilliseconds(),
            updated_at = null,
        )
        return GroupItemResult.RecordResult(
            Record(
                id = id,
                ownerId = ownerId,
                groupId = groupId,
                title = RecordTitle(item.title),
                content = item.content,
                createdAt = createdAt,
                updatedAt = null,
                deletedAt = null,
            ),
        )
    }

    override suspend fun deleteGroup(ownerId: String, groupId: GroupId): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectGroupById(groupId.id).executeAsOneOrNull()
        if (existing == null || existing.group_deleted_at != null || existing.owner_id != ownerId) {
            return@withContext false
        }
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.nullifyTasksGroup(updated_at = now, group_id = groupId.id, owner_id = ownerId)
        db.tatDatabaseQueries.nullifyRecordsGroup(updated_at = now, group_id = groupId.id, owner_id = ownerId)
        db.tatDatabaseQueries.softDeleteGroup(
            group_deleted_at = now,
            updated_at = now,
            id = groupId.id,
            owner_id = ownerId,
        )
        true
    }
}

private fun xyz.qwexter.db.Tat_group.toModel() = Group(
    id = GroupId(id),
    ownerId = owner_id,
    title = GroupTitle(title),
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = group_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)

private fun xyz.qwexter.db.Task.toTaskModel() = Task(
    id = TaskId(id),
    ownerId = owner_id,
    groupId = group_id?.let { GroupId(it) },
    name = TaskName(name),
    description = description,
    status = status.toTaskStatus(),
    priority = priority.toTaskPriority(),
    deadline = deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC) },
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = task_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)

private fun xyz.qwexter.db.Record.toRecordModel() = Record(
    id = RecordId(id),
    ownerId = owner_id,
    groupId = group_id?.let { GroupId(it) },
    title = RecordTitle(title),
    content = content,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = record_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)
