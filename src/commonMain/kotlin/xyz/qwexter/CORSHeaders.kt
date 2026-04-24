package xyz.qwexter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal fun ApplicationCall.addCORSHeaders() {
    response.headers.append("Access-Control-Allow-Origin", "*")
    response.headers.append("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
    response.headers.append("Access-Control-Allow-Headers", "Content-Type")
}

internal suspend fun ApplicationCall.respondCORSPreflight() {
    addCORSHeaders()
    respond(HttpStatusCode.NoContent)
}
