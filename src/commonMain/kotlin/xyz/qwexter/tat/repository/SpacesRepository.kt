package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.Space
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.models.SpaceMember
import xyz.qwexter.tat.models.SpaceTitle
import kotlin.uuid.Uuid

interface SpacesRepository {
    suspend fun allActiveSpaces(ownerId: String): List<Space>
    suspend fun createSpace(ownerId: String, title: String): Space
    suspend fun getOrCreatePrivateSpace(ownerId: String): Space
    suspend fun getSpaceById(spaceId: SpaceId): Space?
    suspend fun updateSpace(ownerId: String, spaceId: SpaceId, title: String): Space?
    suspend fun deleteSpace(ownerId: String, spaceId: SpaceId): Boolean

    suspend fun listMembers(spaceId: SpaceId): List<SpaceMember>
    suspend fun addMember(spaceId: SpaceId, userId: String): Boolean
    suspend fun removeMember(spaceId: SpaceId, userId: String): Boolean

    suspend fun hasAccess(spaceId: SpaceId, userId: String): Boolean

    companion object {
        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): SpacesRepository = DbSpacesRepository(db = db, dbDispatcher = dbDispatcher)
    }
}

private class DbSpacesRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : SpacesRepository {

    override suspend fun allActiveSpaces(ownerId: String): List<Space> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectAllActiveSpacesByOwner(ownerId).executeAsList().map { it.toModel() }
    }

    override suspend fun createSpace(ownerId: String, title: String): Space = withContext(dbDispatcher) {
        val space = Space(
            id = SpaceId(Uuid.random().toString()),
            ownerId = ownerId,
            title = SpaceTitle(title),
            isPrivate = false,
            createdAt = Clock.System.now(),
            updatedAt = null,
            deletedAt = null,
        )
        db.tatDatabaseQueries.insertSpace(
            id = space.id.id,
            owner_id = ownerId,
            title = space.title.title,
            created_at = space.createdAt.toEpochMilliseconds(),
        )
        space
    }

    override suspend fun getOrCreatePrivateSpace(ownerId: String): Space = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectPrivateSpaceByOwner(ownerId).executeAsOneOrNull()
        if (existing != null) return@withContext existing.toModel()
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.insertPrivateSpace(owner_id = ownerId, created_at = now)
        db.tatDatabaseQueries.selectPrivateSpaceByOwner(ownerId).executeAsOne().toModel()
    }

    override suspend fun getSpaceById(spaceId: SpaceId): Space? = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectSpaceById(spaceId.id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun updateSpace(ownerId: String, spaceId: SpaceId, title: String): Space? =
        withContext(dbDispatcher) {
            val existing = db.tatDatabaseQueries.selectSpaceById(spaceId.id).executeAsOneOrNull()
                ?: return@withContext null
            if (existing.owner_id != ownerId) return@withContext null
            db.tatDatabaseQueries.updateSpace(
                title = title,
                updated_at = Clock.System.now().toEpochMilliseconds(),
                id = spaceId.id,
                owner_id = ownerId,
            )
            db.tatDatabaseQueries.selectSpaceById(spaceId.id).executeAsOneOrNull()?.toModel()
        }

    override suspend fun deleteSpace(ownerId: String, spaceId: SpaceId): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectSpaceById(spaceId.id).executeAsOneOrNull()
        val deletable = existing != null &&
            existing.space_deleted_at == null &&
            existing.owner_id == ownerId &&
            existing.is_private == 0L
        if (!deletable) return@withContext false
        val now = Clock.System.now().toEpochMilliseconds()
        db.tatDatabaseQueries.softDeleteSpace(
            space_deleted_at = now,
            updated_at = now,
            id = spaceId.id,
            owner_id = ownerId,
        )
        true
    }

    override suspend fun listMembers(spaceId: SpaceId): List<SpaceMember> = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectSpaceMembers(spaceId.id).executeAsList().map {
            SpaceMember(userId = it.user_id, createdAt = Instant.fromEpochMilliseconds(it.created_at))
        }
    }

    override suspend fun addMember(spaceId: SpaceId, userId: String): Boolean = withContext(dbDispatcher) {
        val space = db.tatDatabaseQueries.selectSpaceById(spaceId.id).executeAsOneOrNull()
        if (space == null || space.space_deleted_at != null || space.is_private != 0L) return@withContext false
        db.tatDatabaseQueries.insertSpaceMember(
            space_id = spaceId.id,
            user_id = userId,
            created_at = Clock.System.now().toEpochMilliseconds(),
        )
        true
    }

    override suspend fun removeMember(spaceId: SpaceId, userId: String): Boolean = withContext(dbDispatcher) {
        val existing = db.tatDatabaseQueries.selectSpaceMember(spaceId.id, userId).executeAsOneOrNull()
        if (existing == null) return@withContext false
        db.tatDatabaseQueries.deleteSpaceMember(space_id = spaceId.id, user_id = userId)
        true
    }

    override suspend fun hasAccess(spaceId: SpaceId, userId: String): Boolean = withContext(dbDispatcher) {
        val count = db.tatDatabaseQueries.hasSpaceAccess(space_id = spaceId.id, user_id = userId)
            .executeAsOne()
        count > 0
    }
}

private fun xyz.qwexter.db.Space.toModel() = Space(
    id = SpaceId(id),
    ownerId = owner_id,
    title = SpaceTitle(title),
    isPrivate = is_private != 0L,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    updatedAt = updated_at?.let { Instant.fromEpochMilliseconds(it) },
    deletedAt = space_deleted_at?.let { Instant.fromEpochMilliseconds(it) },
)
