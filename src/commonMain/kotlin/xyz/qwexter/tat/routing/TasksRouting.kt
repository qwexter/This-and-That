package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
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
                if (task == null || task.deletedAt != null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }

                call.respond(task.toApi())
            }
            patch("/{taskId}") {
                val taskId = TaskId(call.parameters["taskId"]!!)
                val body = call.receive<UpdateTask>()
                if (body.name != null && body.name.isBlank()) throw BadRequestException("name must not be blank")
                val task = tasksRepository.updateTask(
                    taskId = taskId,
                    name = body.name?.trim(),
                    description = body.description,
                    status = body.status?.toDomain(),
                    priority = body.priority?.toDomain(),
                    deadline = body.deadline,
                )
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@patch
                }
                call.respond(task.toApi())
            }
            delete("/{taskId}") {
                val taskId = TaskId(call.parameters["taskId"]!!)
                val deleted = tasksRepository.deleteTask(taskId)
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound)
                    return@delete
                }
                call.respond(HttpStatusCode.NoContent)
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
