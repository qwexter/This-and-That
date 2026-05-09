package xyz.qwexter

import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri

val RequestLogging = createApplicationPlugin("RequestLogging") {
    onCallRespond { call, _ ->
        val status = call.response.status()?.value ?: 0
        val method = call.request.httpMethod.value
        val uri = call.request.uri
        Log.info("request", "method" to method, "uri" to uri, "status" to status)
    }
}

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
    install(RequestLogging)
}
