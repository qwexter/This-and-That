package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import xyz.qwexter.AuthMode
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.repository.SpacesRepository

private const val TITLE_MAX_LEN = 200

fun Application.spacesRouting(
    spacesRepository: SpacesRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        route("/spaces") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@get
                call.respond(HttpStatusCode.OK, spacesRepository.allActiveSpaces(callerId).map { it.toApi() })
            }
            get("/{spaceId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@get
                call.getSpace(spacesRepository, callerId)
            }
            post {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@post
                call.postSpace(spacesRepository, callerId)
            }
            patch("/{spaceId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@patch
                call.patchSpace(spacesRepository, callerId)
            }
            delete("/{spaceId}") {
                if (corsEnabled) call.addCORSHeaders()
                val callerId = call.resolveOwnerId(authMode) ?: return@delete
                call.deleteSpace(spacesRepository, callerId)
            }
            spaceMemberRoutes(spacesRepository, authMode, corsEnabled)
        }
    }
}

private fun Route.spaceMemberRoutes(
    spacesRepository: SpacesRepository,
    authMode: AuthMode,
    corsEnabled: Boolean,
) {
    get("/{spaceId}/members") {
        if (corsEnabled) call.addCORSHeaders()
        val callerId = call.resolveOwnerId(authMode) ?: return@get
        call.listMembers(spacesRepository, callerId)
    }
    post("/{spaceId}/members") {
        if (corsEnabled) call.addCORSHeaders()
        val callerId = call.resolveOwnerId(authMode) ?: return@post
        call.addMember(spacesRepository, callerId)
    }
    delete("/{spaceId}/members/{userId}") {
        if (corsEnabled) call.addCORSHeaders()
        val callerId = call.resolveOwnerId(authMode) ?: return@delete
        call.removeMember(spacesRepository, callerId)
    }
}

private suspend fun ApplicationCall.getSpace(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = repo.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    if (!repo.hasAccess(spaceId, callerId)) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(space.toApi())
}

private suspend fun ApplicationCall.postSpace(repo: SpacesRepository, callerId: String) {
    val body = receive<AddSpace>()
    if (body.title.isBlank()) throw BadRequestException("title must not be blank")
    if (body.title.length > TITLE_MAX_LEN) throw BadRequestException("title must not exceed $TITLE_MAX_LEN characters")
    val space = repo.createSpace(ownerId = callerId, title = body.title.trim())
    respond(HttpStatusCode.Created, space.toApi())
}

private suspend fun ApplicationCall.patchSpace(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val body = receive<UpdateSpace>()
    if (body.title.isBlank()) throw BadRequestException("title must not be blank")
    if (body.title.length > TITLE_MAX_LEN) throw BadRequestException("title must not exceed $TITLE_MAX_LEN characters")
    val space = repo.updateSpace(ownerId = callerId, spaceId = spaceId, title = body.title.trim())
    if (space == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(space.toApi())
}

private suspend fun ApplicationCall.deleteSpace(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = repo.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null || space.ownerId != callerId) {
        respond(HttpStatusCode.NotFound)
        return
    }
    if (space.isPrivate) throw BadRequestException("cannot delete private space")
    repo.deleteSpace(ownerId = callerId, spaceId = spaceId)
    respond(HttpStatusCode.NoContent)
}

private suspend fun ApplicationCall.listMembers(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = repo.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null || space.ownerId != callerId) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.OK, repo.listMembers(spaceId).map { it.toApi() })
}

private suspend fun ApplicationCall.addMember(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = repo.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null || space.ownerId != callerId) {
        respond(HttpStatusCode.NotFound)
        return
    }
    val body = receive<AddSpaceMember>()
    validateAddMember(space, body.userId, callerId)
    repo.addMember(spaceId = spaceId, userId = body.userId)
    respond(HttpStatusCode.Created, ActiveSpaceMember(userId = body.userId))
}

private fun validateAddMember(space: xyz.qwexter.tat.models.Space, userId: String, callerId: String) {
    val error = when {
        space.isPrivate -> "cannot add members to private space"
        userId.isBlank() -> "userId must not be blank"
        userId == callerId -> "cannot add yourself as member"
        else -> null
    }
    if (error != null) throw BadRequestException(error)
}

private suspend fun ApplicationCall.removeMember(repo: SpacesRepository, callerId: String) {
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = repo.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null || space.ownerId != callerId) {
        respond(HttpStatusCode.NotFound)
        return
    }
    val userId = parameters["userId"]!!
    val removed = repo.removeMember(spaceId = spaceId, userId = userId)
    if (!removed) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}
