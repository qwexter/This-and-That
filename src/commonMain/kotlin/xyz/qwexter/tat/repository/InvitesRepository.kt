package xyz.qwexter.tat.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import xyz.qwexter.db.TatDatabase
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.models.SpaceInvite
import kotlin.random.Random

interface InvitesRepository {
    suspend fun createInvite(spaceId: SpaceId, createdBy: String, expiresAt: Instant?, maxUses: Int?): SpaceInvite
    suspend fun getInvite(token: String): SpaceInvite?
    suspend fun consumeInvite(token: String): SpaceInvite?

    companion object {
        fun create(
            db: TatDatabase,
            dbDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
        ): InvitesRepository = DbInvitesRepository(db = db, dbDispatcher = dbDispatcher)
    }
}

private class DbInvitesRepository(
    private val db: TatDatabase,
    private val dbDispatcher: CoroutineDispatcher,
) : InvitesRepository {

    override suspend fun createInvite(
        spaceId: SpaceId,
        createdBy: String,
        expiresAt: Instant?,
        maxUses: Int?,
    ): SpaceInvite = withContext(dbDispatcher) {
        val token = generateToken()
        val now = Clock.System.now()
        db.tatDatabaseQueries.insertSpaceInvite(
            token = token,
            space_id = spaceId.id,
            created_by = createdBy,
            created_at = now.toEpochMilliseconds(),
            expires_at = expiresAt?.toEpochMilliseconds(),
            max_uses = maxUses?.toLong(),
        )
        SpaceInvite(
            token = token,
            spaceId = spaceId,
            createdBy = createdBy,
            createdAt = now,
            expiresAt = expiresAt,
            maxUses = maxUses,
            useCount = 0,
        )
    }

    override suspend fun getInvite(token: String): SpaceInvite? = withContext(dbDispatcher) {
        db.tatDatabaseQueries.selectSpaceInviteByToken(token).executeAsOneOrNull()?.toModel()
    }

    override suspend fun consumeInvite(token: String): SpaceInvite? = withContext(dbDispatcher) {
        val invite = db.tatDatabaseQueries.selectSpaceInviteByToken(token).executeAsOneOrNull()?.toModel()
            ?: return@withContext null
        val now = Clock.System.now()
        if (invite.expiresAt != null && now > invite.expiresAt) return@withContext null
        if (invite.maxUses != null && invite.useCount >= invite.maxUses) return@withContext null
        db.tatDatabaseQueries.incrementSpaceInviteUseCount(token)
        invite
    }

    private fun generateToken(): String {
        val bytes = Random.nextBytes(24)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}

private fun xyz.qwexter.db.Space_invite.toModel() = SpaceInvite(
    token = token,
    spaceId = SpaceId(space_id),
    createdBy = created_by,
    createdAt = Instant.fromEpochMilliseconds(created_at),
    expiresAt = expires_at?.let { Instant.fromEpochMilliseconds(it) },
    maxUses = max_uses?.toInt(),
    useCount = use_count.toInt(),
)
