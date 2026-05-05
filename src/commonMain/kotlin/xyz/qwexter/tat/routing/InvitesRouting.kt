package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import xyz.qwexter.AuthMode
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.models.SpaceId
import xyz.qwexter.tat.repository.InvitesRepository
import xyz.qwexter.tat.repository.SpacesRepository

fun Application.invitesRouting(
    invitesRepository: InvitesRepository,
    spacesRepository: SpacesRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        route("/spaces/{spaceId}/invites") {
            post {
                if (corsEnabled) call.addCORSHeaders()
                call.handleCreateInvite(invitesRepository, spacesRepository, authMode)
            }
        }

        route("/invites/{token}") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                call.handleGetInvite(invitesRepository, spacesRepository)
            }

            post("/accept") {
                if (corsEnabled) call.addCORSHeaders()
                call.handleAcceptInvite(invitesRepository, spacesRepository, authMode)
            }
        }
    }
}

private suspend fun ApplicationCall.handleCreateInvite(
    invitesRepository: InvitesRepository,
    spacesRepository: SpacesRepository,
    authMode: AuthMode,
) {
    val callerId = resolveOwnerId(authMode) ?: return
    val spaceId = SpaceId(parameters["spaceId"]!!)
    val space = spacesRepository.getSpaceById(spaceId)
    if (space == null || space.deletedAt != null || space.ownerId != callerId) {
        respond(HttpStatusCode.NotFound)
        return
    }
    if (space.isPrivate) throw BadRequestException("cannot invite to private space")
    val invite = invitesRepository.createInvite(
        spaceId = spaceId,
        createdBy = callerId,
        expiresAt = null,
        maxUses = null,
    )
    respond(HttpStatusCode.Created, SpaceInviteResponse(token = invite.token))
}

private suspend fun ApplicationCall.handleGetInvite(
    invitesRepository: InvitesRepository,
    spacesRepository: SpacesRepository,
) {
    val token = parameters["token"]!!
    val invite = invitesRepository.getInvite(token)
    if (invite == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    val space = spacesRepository.getSpaceById(invite.spaceId)
    if (space == null || space.deletedAt != null) {
        respond(HttpStatusCode.Gone)
        return
    }
    respond(InviteInfoResponse(spaceTitle = space.title.title))
}

private suspend fun ApplicationCall.handleAcceptInvite(
    invitesRepository: InvitesRepository,
    spacesRepository: SpacesRepository,
    authMode: AuthMode,
) {
    val callerId = resolveOwnerId(authMode) ?: return
    val token = parameters["token"]!!
    val invite = invitesRepository.consumeInvite(token)
    if (invite == null) {
        respond(HttpStatusCode.Gone)
        return
    }
    val space = spacesRepository.getSpaceById(invite.spaceId)
    when {
        space == null || space.deletedAt != null -> {
            respond(HttpStatusCode.Gone)
        }
        space.ownerId == callerId -> {
            respond(HttpStatusCode.OK)
        }
        else -> {
            spacesRepository.addMember(spaceId = invite.spaceId, userId = callerId)
            respond(HttpStatusCode.OK)
        }
    }
}
