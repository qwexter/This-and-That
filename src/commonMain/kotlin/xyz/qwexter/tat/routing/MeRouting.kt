package xyz.qwexter.tat.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import xyz.qwexter.AuthMode
import xyz.qwexter.addCORSHeaders
import xyz.qwexter.tat.repository.SpacesRepository

fun Application.meRouting(
    spacesRepository: SpacesRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        get("/me") {
            if (corsEnabled) call.addCORSHeaders()
            val userId = call.resolveOwnerIdWithPrivateSpace(authMode, spacesRepository) ?: return@get
            val displayName = call.resolveDisplayName(authMode, userId)
            call.respond(HttpStatusCode.OK, MeResponse(userId = userId, displayName = displayName))
        }
    }
}

@Serializable
data class MeResponse(
    val userId: String,
    val displayName: String,
)
