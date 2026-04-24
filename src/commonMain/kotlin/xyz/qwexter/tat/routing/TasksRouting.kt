package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TasksRepository

fun Application.tasksRouting(tasksRepository: TasksRepository, corsEnabled: Boolean = false) {
    routing {
        route("/tasks") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                call.getTasks(tasksRepository)
            }
            get("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                call.getTaskById(tasksRepository)
            }
            patch("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                call.patchTask(tasksRepository)
            }
            delete("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                call.deleteTask(tasksRepository)
            }
            post {
                if (corsEnabled) call.addCORSHeaders()
                call.postTask(tasksRepository)
            }
        }
    }
}

private suspend fun ApplicationCall.getTasks(repo: TasksRepository) {
    respond(HttpStatusCode.OK, repo.allActiveTasks().map { it.toApi() })
}

private suspend fun ApplicationCall.getTaskById(repo: TasksRepository) {
    val taskId = TaskId(parameters["taskId"]!!)
    val task = repo.getTaskById(taskId)
    if (task == null || task.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(task.toApi())
}

private suspend fun ApplicationCall.patchTask(repo: TasksRepository) {
    val taskId = TaskId(parameters["taskId"]!!)
    val body = receive<UpdateTask>()
    if (body.name != null && body.name.isBlank()) throw BadRequestException("name must not be blank")
    val task = repo.updateTask(
        taskId = taskId,
        name = body.name?.trim(),
        description = body.description,
        status = body.status?.toDomain(),
        priority = body.priority?.toDomain(),
        deadline = body.deadline,
    )
    if (task == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(task.toApi())
}

private suspend fun ApplicationCall.deleteTask(repo: TasksRepository) {
    val taskId = TaskId(parameters["taskId"]!!)
    val deleted = repo.deleteTask(taskId)
    if (!deleted) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}

private suspend fun ApplicationCall.postTask(repo: TasksRepository) {
    val addTask = receive<AddTask>()
    if (addTask.name.isBlank()) throw BadRequestException("name must not be blank")
    val task = repo.createTask(
        name = addTask.name.trim(),
        description = addTask.description,
        status = addTask.status?.toDomain() ?: TaskStatus.Todo,
        priority = addTask.priority?.toDomain() ?: TaskPriority.Low,
        deadline = addTask.deadline,
    )
    respond(HttpStatusCode.Created, task.toApi())
}
