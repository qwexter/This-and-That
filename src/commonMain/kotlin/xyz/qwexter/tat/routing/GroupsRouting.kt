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
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.repository.AddItemsError
import xyz.qwexter.tat.repository.Either
import xyz.qwexter.tat.repository.GroupItemInput
import xyz.qwexter.tat.repository.GroupItemResult
import xyz.qwexter.tat.repository.GroupUpdateParams
import xyz.qwexter.tat.repository.GroupsRepository
import xyz.qwexter.tat.repository.SpacesRepository

private const val TITLE_MAX_LENGTH = 200

fun Application.groupsRouting(
    groupsRepository: GroupsRepository,
    spacesRepository: SpacesRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        route("/groups") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@get
                call.respond(HttpStatusCode.OK, groupsRepository.allActiveGroups(callerId).map { it.toApi() })
            }
            get("/{groupId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@get
                call.getGroup(groupsRepository, callerId)
            }
            post {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@post
                call.postGroup(groupsRepository, spacesRepository, callerId)
            }
            patch("/{groupId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@patch
                call.patchGroup(groupsRepository, spacesRepository, callerId)
            }
            delete("/{groupId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@delete
                call.deleteGroup(groupsRepository, callerId)
            }
            post("/{groupId}/items") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@post
                call.addGroupItems(groupsRepository, callerId)
            }
        }
    }
}

private suspend fun ApplicationCall.getGroup(repo: GroupsRepository, callerId: String) {
    val groupId = GroupId(parameters["groupId"]!!)
    val group = repo.getGroupById(callerId, groupId)
    if (group == null || group.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(group.toApi())
}

private suspend fun ApplicationCall.postGroup(
    repo: GroupsRepository,
    spacesRepo: SpacesRepository,
    callerId: String,
) {
    val body = receive<AddGroup>()
    if (body.title.isBlank()) throw BadRequestException("title must not be blank")
    if (body.title.length > TITLE_MAX_LENGTH) {
        throw BadRequestException("title must not exceed $TITLE_MAX_LENGTH characters")
    }
    val spaceId = body.spaceId?.let { SpaceId(it) }
        ?: spacesRepo.getOrCreatePrivateSpace(callerId).id
    val group = repo.createGroup(ownerId = callerId, title = body.title.trim(), spaceId = spaceId)
    respond(HttpStatusCode.Created, group.toApi())
}

private suspend fun ApplicationCall.patchGroup(
    repo: GroupsRepository,
    spacesRepo: SpacesRepository,
    callerId: String,
) {
    val groupId = GroupId(parameters["groupId"]!!)
    val body = receive<UpdateGroup>()
    if (body.title != null) {
        if (body.title.isBlank()) throw BadRequestException("title must not be blank")
        if (body.title.length > TITLE_MAX_LENGTH) {
            throw BadRequestException("title must not exceed $TITLE_MAX_LENGTH characters")
        }
    }
    val resolvedSpaceId = when {
        body.spaceId != null -> SpaceId(body.spaceId)
        body.clearSpace -> spacesRepo.getOrCreatePrivateSpace(callerId).id
        else -> null
    }
    val params = GroupUpdateParams(
        title = body.title?.trim(),
        spaceId = resolvedSpaceId,
        clearSpace = false,
    )
    val group = repo.updateGroup(ownerId = callerId, groupId = groupId, params = params)
    if (group == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(group.toApi())
}

private suspend fun ApplicationCall.deleteGroup(repo: GroupsRepository, callerId: String) {
    val groupId = GroupId(parameters["groupId"]!!)
    val deleted = repo.deleteGroup(callerId, groupId)
    if (!deleted) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}

private suspend fun ApplicationCall.addGroupItems(repo: GroupsRepository, callerId: String) {
    val groupId = GroupId(parameters["groupId"]!!)
    val group = repo.getGroupById(callerId, groupId)
    if (group == null || group.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    val body = receive<AddGroupItemsRequest>()
    val domainItems = body.items.map { it.toDomain() }
    when (val result = repo.addItemsToGroup(callerId, groupId, domainItems)) {
        is Either.Left -> throw BadRequestException(result.error.toMessage())
        is Either.Right -> respond(HttpStatusCode.OK, AddGroupItemsResponse(result.value.map { it.toApi() }))
    }
}

private fun AddItemsError.toMessage(): String = when (this) {
    is AddItemsError.TaskNotFound -> "task not found: ${taskId.id}"
    is AddItemsError.RecordNotFound -> "record not found: ${recordId.id}"
    is AddItemsError.TaskInOtherGroup -> "task ${taskId.id} already belongs to group ${currentGroupId.id}"
    is AddItemsError.RecordInOtherGroup -> "record ${recordId.id} already belongs to group ${currentGroupId.id}"
}

private fun AddGroupItem.toDomain(): GroupItemInput = when (this) {
    is AddGroupItem.NewTask -> GroupItemInput.NewTask(
        name = name.trim().also { if (it.isBlank()) throw BadRequestException("task name must not be blank") },
        description = description,
        priority = priority.toDomain(),
        deadline = deadline,
    )
    is AddGroupItem.NewRecord -> GroupItemInput.NewRecord(
        title = title.trim().also { if (it.isBlank()) throw BadRequestException("record title must not be blank") },
        content = content,
    )
    is AddGroupItem.ExistingTask -> GroupItemInput.ExistingTask(
        taskId = xyz.qwexter.tat.models.TaskId(id)
    )
    is AddGroupItem.ExistingRecord -> GroupItemInput.ExistingRecord(
        recordId = xyz.qwexter.tat.models.RecordId(id)
    )
}

private fun GroupItemResult.toApi(): GroupItemResponse = when (this) {
    is GroupItemResult.TaskResult -> GroupItemResponse.TaskResponse(
        id = task.id.id,
        groupId = task.groupId!!.id,
        name = task.name.name,
        description = task.description,
        status = task.status.toApi(),
        priority = task.priority.toApi(),
        deadline = task.deadline,
    )
    is GroupItemResult.RecordResult -> GroupItemResponse.RecordResponse(
        id = record.id.id,
        groupId = record.groupId!!.id,
        title = record.title.title,
        content = record.content,
    )
}
