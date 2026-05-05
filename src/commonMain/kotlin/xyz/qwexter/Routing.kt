package xyz.qwexter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import xyz.qwexter.tat.repository.FeedRepository
import xyz.qwexter.tat.repository.GroupsRepository
import xyz.qwexter.tat.repository.InvitesRepository
import xyz.qwexter.tat.repository.RecordsRepository
import xyz.qwexter.tat.repository.SpacesRepository
import xyz.qwexter.tat.repository.TasksRepository
import xyz.qwexter.tat.routing.feedRouting
import xyz.qwexter.tat.routing.groupsRouting
import xyz.qwexter.tat.routing.invitesRouting
import xyz.qwexter.tat.routing.meRouting
import xyz.qwexter.tat.routing.recordsRouting
import xyz.qwexter.tat.routing.spacesRouting
import xyz.qwexter.tat.routing.tasksRouting

fun Application.configureRouting(
    repositories: Repositories,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    install(Resources)
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respondText(text = cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/health") {
            call.respondText("ok", status = HttpStatusCode.OK)
        }
    }
    if (corsEnabled) {
        routing {
            options("/health") { call.respondCORSPreflight() }
            options("/me") { call.respondCORSPreflight() }
            options("/tasks") { call.respondCORSPreflight() }
            options("/tasks/{...}") { call.respondCORSPreflight() }
            options("/records") { call.respondCORSPreflight() }
            options("/records/{...}") { call.respondCORSPreflight() }
            options("/groups") { call.respondCORSPreflight() }
            options("/groups/{...}") { call.respondCORSPreflight() }
            options("/groups/{groupId}/items") { call.respondCORSPreflight() }
            options("/spaces") { call.respondCORSPreflight() }
            options("/spaces/{...}") { call.respondCORSPreflight() }
            options("/invites/{...}") { call.respondCORSPreflight() }
            options("/feed") { call.respondCORSPreflight() }
        }
    }
    meRouting(authMode = authMode, corsEnabled = corsEnabled)
    tasksRouting(tasksRepository = repositories.tasks, authMode = authMode, corsEnabled = corsEnabled)
    recordsRouting(recordsRepository = repositories.records, authMode = authMode, corsEnabled = corsEnabled)
    groupsRouting(
        groupsRepository = repositories.groups,
        spacesRepository = repositories.spaces,
        authMode = authMode,
        corsEnabled = corsEnabled,
    )
    spacesRouting(spacesRepository = repositories.spaces, authMode = authMode, corsEnabled = corsEnabled)
    invitesRouting(
        invitesRepository = repositories.invites,
        spacesRepository = repositories.spaces,
        authMode = authMode,
        corsEnabled = corsEnabled,
    )
    feedRouting(feedRepository = repositories.feed, authMode = authMode, corsEnabled = corsEnabled)
}

data class Repositories(
    val tasks: TasksRepository,
    val records: RecordsRepository,
    val groups: GroupsRepository,
    val feed: FeedRepository,
    val spaces: SpacesRepository,
    val invites: InvitesRepository,
)
