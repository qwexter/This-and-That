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
import xyz.qwexter.AuthMode
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.models.GroupId
import xyz.qwexter.tat.models.TaskId
import xyz.qwexter.tat.models.TaskPriority
import xyz.qwexter.tat.models.TaskStatus
import xyz.qwexter.tat.repository.TaskUpdateParams
import xyz.qwexter.tat.repository.TasksRepository

fun Application.tasksRouting(
    tasksRepository: TasksRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        route("/tasks") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@get
                call.getTasks(tasksRepository, ownerId)
            }
            get("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@get
                call.getTaskById(tasksRepository, ownerId)
            }
            patch("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@patch
                call.patchTask(tasksRepository, ownerId)
            }
            delete("/{taskId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@delete
                call.deleteTask(tasksRepository, ownerId)
            }
            post {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@post
                call.postTask(tasksRepository, ownerId)
            }
        }
    }
}

private suspend fun ApplicationCall.getTasks(repo: TasksRepository, ownerId: String) {
    respond(HttpStatusCode.OK, repo.allActiveTasks(ownerId).map { it.toApi() })
}

private suspend fun ApplicationCall.getTaskById(repo: TasksRepository, ownerId: String) {
    val taskId = TaskId(parameters["taskId"]!!)
    val task = repo.getTaskById(ownerId, taskId)
    if (task == null || task.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(task.toApi())
}

private suspend fun ApplicationCall.patchTask(repo: TasksRepository, ownerId: String) {
    val taskId = TaskId(parameters["taskId"]!!)
    val body = receive<UpdateTask>()
    if (body.name != null && body.name.isBlank()) throw BadRequestException("name must not be blank")
    val task = repo.updateTask(
        ownerId = ownerId,
        taskId = taskId,
        params = TaskUpdateParams(
            name = body.name?.trim(),
            description = body.description,
            status = body.status?.toDomain(),
            priority = body.priority?.toDomain(),
            deadline = body.deadline,
            groupId = body.groupId?.let { GroupId(it) },
            clearGroup = body.clearGroup,
        ),
    )
    if (task == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(task.toApi())
}

private suspend fun ApplicationCall.deleteTask(repo: TasksRepository, ownerId: String) {
    val taskId = TaskId(parameters["taskId"]!!)
    val deleted = repo.deleteTask(ownerId, taskId)
    if (!deleted) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}

private suspend fun ApplicationCall.postTask(repo: TasksRepository, ownerId: String) {
    val addTask = receive<AddTask>()
    if (addTask.name.isBlank()) throw BadRequestException("name must not be blank")
    val task = repo.createTask(
        ownerId = ownerId,
        name = addTask.name.trim(),
        description = addTask.description,
        status = addTask.status?.toDomain() ?: TaskStatus.Todo,
        priority = addTask.priority?.toDomain() ?: TaskPriority.Low,
        deadline = addTask.deadline,
        groupId = addTask.groupId?.let { GroupId(it) },
    )
    respond(HttpStatusCode.Created, task.toApi())
}
