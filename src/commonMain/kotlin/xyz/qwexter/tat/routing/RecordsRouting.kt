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
import xyz.qwexter.tat.models.RecordId
import xyz.qwexter.tat.repository.RecordsRepository

private const val TITLE_MAX_LENGTH = 200
private const val CONTENT_MAX_LENGTH = 5000

fun Application.recordsRouting(
    recordsRepository: RecordsRepository,
    authMode: AuthMode = AuthMode.NONE,
    corsEnabled: Boolean = false,
) {
    routing {
        route("/records") {
            get {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@get
                call.respond(HttpStatusCode.OK, recordsRepository.allActiveRecords(ownerId).map { it.toApi() })
            }
            get("/{recordId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@get
                call.getRecord(recordsRepository, ownerId)
            }
            post {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@post
                call.postRecord(recordsRepository, ownerId)
            }
            patch("/{recordId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@patch
                call.patchRecord(recordsRepository, ownerId)
            }
            delete("/{recordId}") {
                if (corsEnabled) call.addCORSHeaders()
                val ownerId = call.resolveOwnerId(authMode) ?: return@delete
                call.deleteRecord(recordsRepository, ownerId)
            }
        }
    }
}

private suspend fun ApplicationCall.getRecord(repo: RecordsRepository, ownerId: String) {
    val recordId = RecordId(parameters["recordId"]!!)
    val record = repo.getRecordById(ownerId, recordId)
    if (record == null || record.deletedAt != null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(record.toApi())
}

private suspend fun ApplicationCall.postRecord(repo: RecordsRepository, ownerId: String) {
    val body = receive<AddRecord>()
    when {
        body.title.isBlank() -> "title must not be blank"
        body.title.length > TITLE_MAX_LENGTH -> {
            "title must not exceed $TITLE_MAX_LENGTH characters"
        }

        body.content != null && body.content.length > CONTENT_MAX_LENGTH -> {
            "content must not exceed $CONTENT_MAX_LENGTH characters"
        }

        else -> null
    }?.let { throw BadRequestException(it) }
    val record = repo.createRecord(
        ownerId = ownerId,
        title = body.title.trim(),
        content = body.content,
        groupId = body.groupId?.let { GroupId(it) },
    )
    respond(HttpStatusCode.Created, record.toApi())
}

private suspend fun ApplicationCall.patchRecord(repo: RecordsRepository, ownerId: String) {
    val recordId = RecordId(parameters["recordId"]!!)
    val body = receive<UpdateRecord>()
    when {
        body.title != null && body.title.isBlank() -> "title must not be blank"
        body.title != null && body.title.length > TITLE_MAX_LENGTH -> {
            "title must not exceed $TITLE_MAX_LENGTH characters"
        }

        body.content != null && body.content.length > CONTENT_MAX_LENGTH -> {
            "content must not exceed $CONTENT_MAX_LENGTH characters"
        }

        else -> null
    }?.let { throw BadRequestException(it) }
    val record = repo.updateRecord(
        ownerId = ownerId,
        recordId = recordId,
        title = body.title?.trim(),
        content = body.content,
        groupId = body.groupId?.let { GroupId(it) },
        clearGroup = body.clearGroup,
    )
    if (record == null) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(record.toApi())
}

private suspend fun ApplicationCall.deleteRecord(repo: RecordsRepository, ownerId: String) {
    val recordId = RecordId(parameters["recordId"]!!)
    val deleted = repo.deleteRecord(ownerId, recordId)
    if (!deleted) {
        respond(HttpStatusCode.NotFound)
        return
    }
    respond(HttpStatusCode.NoContent)
}
