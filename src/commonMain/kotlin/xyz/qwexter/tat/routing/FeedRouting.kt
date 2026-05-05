package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import xyz.qwexter.AuthMode
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.repository.FeedChild
import xyz.qwexter.tat.repository.FeedEntry
import xyz.qwexter.tat.repository.FeedPage
import xyz.qwexter.tat.repository.FeedRepository

private const val DEFAULT_LIMIT = 20L
private const val MAX_LIMIT = 100L

fun Application.feedRouting(
    feedRepository: FeedRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        get("/feed") {
            if (corsEnabled) call.addCORSHeaders()
            val ownerId = call.resolveOwnerId(authMode) ?: return@get
            val limit = call.request.queryParameters["limit"]?.toLongOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
            val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
            val spaceId = call.request.queryParameters["spaceId"]?.takeIf { it.isNotBlank() }
            val page = feedRepository.getFeedPage(ownerId, limit, offset, spaceId)
            call.respond(HttpStatusCode.OK, page.toApi())
        }
    }
}

private fun FeedPage.toApi() = ApiFeedPage(
    items = items.map { it.toApi() },
    total = total,
    offset = offset,
    limit = limit,
)

private fun FeedEntry.toApi(): ApiFeedEntry = when (this) {
    is FeedEntry.GroupEntry -> ApiFeedEntry.ApiGroupEntry(
        id = id.id,
        title = title.title,
        createdAt = createdAt.toString(),
        children = children.map { it.toApi() },
    )
    is FeedEntry.TaskEntry -> ApiFeedEntry.ApiTaskEntry(
        id = id.id,
        groupId = groupId?.id,
        name = name.name,
        description = description,
        status = status,
        priority = priority,
        deadline = deadline,
        createdAt = createdAt.toString(),
    )
    is FeedEntry.RecordEntry -> ApiFeedEntry.ApiRecordEntry(
        id = id.id,
        groupId = groupId?.id,
        title = title.title,
        content = content,
        createdAt = createdAt.toString(),
    )
}

private fun FeedChild.toApi(): ApiFeedChild = when (this) {
    is FeedChild.TaskChild -> ApiFeedChild.ApiTaskChild(
        id = id.id,
        name = name.name,
        description = description,
        status = status,
        priority = priority,
        deadline = deadline,
        createdAt = createdAt.toString(),
    )
    is FeedChild.RecordChild -> ApiFeedChild.ApiRecordChild(
        id = id.id,
        title = title.title,
        content = content,
        createdAt = createdAt.toString(),
    )
}

@Serializable
data class ApiFeedPage(
    val items: List<ApiFeedEntry>,
    val total: Long,
    val offset: Long,
    val limit: Long,
)

@Serializable
sealed class ApiFeedEntry {
    @Serializable
    @SerialName("group")
    data class ApiGroupEntry(
        val id: String,
        val title: String,
        val createdAt: String,
        val children: List<ApiFeedChild>,
    ) : ApiFeedEntry()

    @Serializable
    @SerialName("task")
    data class ApiTaskEntry(
        val id: String,
        val groupId: String?,
        val name: String,
        val description: String?,
        val status: String,
        val priority: String,
        val deadline: String?,
        val createdAt: String,
    ) : ApiFeedEntry()

    @Serializable
    @SerialName("record")
    data class ApiRecordEntry(
        val id: String,
        val groupId: String?,
        val title: String,
        val content: String?,
        val createdAt: String,
    ) : ApiFeedEntry()
}

@Serializable
sealed class ApiFeedChild {
    @Serializable
    @SerialName("task")
    data class ApiTaskChild(
        val id: String,
        val name: String,
        val description: String?,
        val status: String,
        val priority: String,
        val deadline: String?,
        val createdAt: String,
    ) : ApiFeedChild()

    @Serializable
    @SerialName("record")
    data class ApiRecordChild(
        val id: String,
        val title: String,
        val content: String?,
        val createdAt: String,
    ) : ApiFeedChild()
}
