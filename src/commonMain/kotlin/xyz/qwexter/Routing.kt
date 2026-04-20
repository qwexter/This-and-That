package xyz.qwexter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import xyz.qwexter.tat.repository.TasksRepository
import xyz.qwexter.tat.routing.tasksRouting

fun Application.configureRouting(tasksRepository: TasksRepository) {
    install(Resources)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    tasksRouting(tasksRepository = tasksRepository)
}
