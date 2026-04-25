package xyz.qwexter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import xyz.qwexter.tat.repository.TasksRepository
import xyz.qwexter.tat.routing.tasksRouting

fun Application.configureRouting(
    tasksRepository: TasksRepository,
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
    if (corsEnabled) {
        routing {
            options("/tasks") { call.respondCORSPreflight() }
            options("/tasks/{...}") { call.respondCORSPreflight() }
        }
    }
    tasksRouting(tasksRepository = tasksRepository, authMode = authMode, corsEnabled = corsEnabled)
}
