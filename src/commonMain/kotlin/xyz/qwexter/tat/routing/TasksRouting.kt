package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import xyz.qwexter.tat.repository.TasksRepository

fun Application.tasksRouting(tasksRepository: TasksRepository) {
    routing {
        route("/tasks") {
            get {
                val tasks = tasksRepository.allActiveTasks()
                call.respond(status = HttpStatusCode.OK, message = tasks)
            }
        }
    }
}
