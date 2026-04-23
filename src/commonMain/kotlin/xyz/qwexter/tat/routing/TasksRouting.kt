package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TasksRepository

fun Application.tasksRouting(tasksRepository: TasksRepository) {
    routing {
        route("/tasks") {
            get {
                call.respond(HttpStatusCode.OK, tasksRepository.allActiveTasks().map { it.toApi() })
            }
            get("/{taskId}") {
                val taskId = TaskId(call.parameters["taskId"]!!)
                val task = tasksRepository.getTaskById(taskId)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                call.respond(task.toApi())
            }
            post {
                val addTask = call.receive<AddTask>()

                if (addTask.name.isBlank()) throw BadRequestException("name must not be blank")

                val task = tasksRepository.createTask(
                    name = addTask.name.trim(),
                    description = addTask.description,
                    status = addTask.status?.toDomain() ?: TaskStatus.Todo,
                    priority = addTask.priority?.toDomain() ?: TaskPriority.Low,
                    deadline = addTask.deadline,
                )

                call.respond(HttpStatusCode.Created, task.toApi())
            }
        }
    }
}
