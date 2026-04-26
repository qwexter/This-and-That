package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.GroupId
import xyz.qwexter.tat.models.GroupTitle
import xyz.qwexter.tat.models.RecordId
import xyz.qwexter.tat.models.RecordTitle
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskName

data class FeedPage(
    val items: List<FeedEntry>,
    val total: Long,
    val offset: Long,
    val limit: Long,
)

sealed class FeedEntry {
    data class GroupEntry(
        val id: GroupId,
        val title: GroupTitle,
        val createdAt: Instant,
        val children: List<FeedChild>,
    ) : FeedEntry()

    data class TaskEntry(
        val id: TaskId,
        val groupId: GroupId?,
        val name: TaskName,
        val description: String?,
        val status: String,
        val priority: String,
        val deadline: String?,
        val createdAt: Instant,
    ) : FeedEntry()

    data class RecordEntry(
        val id: RecordId,
        val groupId: GroupId?,
        val title: RecordTitle,
        val content: String?,
        val createdAt: Instant,
    ) : FeedEntry()
}

sealed class FeedChild {
    data class TaskChild(
        val id: TaskId,
        val name: TaskName,
        val description: String?,
        val status: String,
        val priority: String,
        val deadline: String?,
        val createdAt: Instant,
    ) : FeedChild()

    data class RecordChild(
        val id: RecordId,
        val title: RecordTitle,
        val content: String?,
        val createdAt: Instant,
    ) : FeedChild()
}

interface FeedRepository {
    suspend fun getFeedPage(ownerId: String, limit: Long, offset: Long): FeedPage

    companion object {
        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): FeedRepository = DbFeedRepository(db = db, dbDispatcher = dbDispatcher)
    }
}

private class DbFeedRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : FeedRepository {

    override suspend fun getFeedPage(ownerId: String, limit: Long, offset: Long): FeedPage =
        withContext(dbDispatcher) {
            val total = db.tatDatabaseQueries.countFeedEntries(ownerId).executeAsOne()
            val pageRows = db.tatDatabaseQueries.selectFeedPage(
                owner_id = ownerId,
                lim = limit,
                off = offset,
            ).executeAsList()

            val groupIds = pageRows.filter { it.kind == "group" }.map { it.id }
            val groupChildren = loadGroupChildren(groupIds, ownerId)
            val taskRows = loadTaskRows(pageRows.filter { it.kind == "task" }.map { it.id })
            val recordRows = loadRecordRows(pageRows.filter { it.kind == "record" }.map { it.id })
            val groupRows = loadGroupRows(groupIds)

            val entries = pageRows.mapNotNull { row ->
                mapFeedRow(row.kind, row.id, taskRows, recordRows, groupRows, groupChildren)
            }
            FeedPage(items = entries, total = total, offset = offset, limit = limit)
        }

    private fun loadGroupChildren(groupIds: List<String>, ownerId: String): Map<String, List<FeedChild>> {
        if (groupIds.isEmpty()) return emptyMap()
        val tasks = groupIds.flatMap { gid ->
            db.tatDatabaseQueries.selectActiveTasksByGroup(group_id = gid, owner_id = ownerId)
                .executeAsList().map { gid to it.toTaskChild() }
        }
        val records = groupIds.flatMap { gid ->
            db.tatDatabaseQueries.selectActiveRecordsByGroup(group_id = gid, owner_id = ownerId)
                .executeAsList().map { gid to it.toRecordChild() }
        }
        return (tasks + records)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, children) -> children.sortedByDescending { it.createdAt } }
    }

    private fun loadTaskRows(ids: List<String>): Map<String, xyz.qwexter.db.Task?> {
        if (ids.isEmpty()) return emptyMap()
        return ids.associateWith { db.tatDatabaseQueries.selectTaskById(it).executeAsOneOrNull() }
    }

    private fun loadRecordRows(ids: List<String>): Map<String, xyz.qwexter.db.Record?> {
        if (ids.isEmpty()) return emptyMap()
        return ids.associateWith { db.tatDatabaseQueries.selectRecordById(it).executeAsOneOrNull() }
    }

    private fun loadGroupRows(ids: List<String>): Map<String, xyz.qwexter.db.Tat_group?> {
        if (ids.isEmpty()) return emptyMap()
        return ids.associateWith { db.tatDatabaseQueries.selectGroupById(it).executeAsOneOrNull() }
    }

    private fun mapFeedRow(
        kind: String,
        id: String,
        taskRows: Map<String, xyz.qwexter.db.Task?>,
        recordRows: Map<String, xyz.qwexter.db.Record?>,
        groupRows: Map<String, xyz.qwexter.db.Tat_group?>,
        groupChildren: Map<String, List<FeedChild>>,
    ): FeedEntry? = when (kind) {
        "group" -> {
            val g = groupRows[id] ?: return null
            FeedEntry.GroupEntry(
                id = GroupId(g.id),
                title = GroupTitle(g.title),
                createdAt = Instant.fromEpochMilliseconds(g.created_at),
                children = groupChildren[g.id] ?: emptyList(),
            )
        }
        "task" -> {
            val t = taskRows[id] ?: return null
            FeedEntry.TaskEntry(
                id = TaskId(t.id),
                groupId = t.group_id?.let { GroupId(it) },
                name = TaskName(t.name),
                description = t.description,
                status = t.status,
                priority = t.priority,
                deadline = t.deadline?.let {
                    Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).toString()
                },
                createdAt = Instant.fromEpochMilliseconds(t.created_at),
            )
        }
        "record" -> {
            val r = recordRows[id] ?: return null
            FeedEntry.RecordEntry(
                id = RecordId(r.id),
                groupId = r.group_id?.let { GroupId(it) },
                title = RecordTitle(r.title),
                content = r.content,
                createdAt = Instant.fromEpochMilliseconds(r.created_at),
            )
        }
        else -> null
    }
}

private fun xyz.qwexter.db.Task.toTaskChild() = FeedChild.TaskChild(
    id = TaskId(id),
    name = TaskName(name),
    description = description,
    status = status,
    priority = priority,
    deadline = deadline?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).toString() },
    createdAt = Instant.fromEpochMilliseconds(created_at),
)

private fun xyz.qwexter.db.Record.toRecordChild() = FeedChild.RecordChild(
    id = RecordId(id),
    title = RecordTitle(title),
    content = content,
    createdAt = Instant.fromEpochMilliseconds(created_at),
)

private val FeedChild.createdAt: Instant
    get() = when (this) {
        is FeedChild.TaskChild -> createdAt
        is FeedChild.RecordChild -> createdAt
    }
