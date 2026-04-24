package xyz.qwexter

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.routing
import okio.FileSystem
import okio.Path.Companion.toPath

fun Application.configureStatic(staticPath: String) {
    val base = staticPath.toPath().normalized()
    val fs = FileSystem.SYSTEM

    routing {
        options("{...}") {
            call.respondCORSPreflight()
        }

        get("{...}") {
            call.addCORSHeaders()

            val segments = call.parameters.getAll("...") ?: emptyList()
            val relative = segments.joinToString("/")
            val target = (base / relative).normalized()

            if (!target.toString().startsWith(base.toString())) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val resolvedPath = when {
                fs.metadataOrNull(target)?.isDirectory == true -> target / "index.html"
                else -> target
            }

            if (fs.exists(resolvedPath)) {
                val bytes = fs.read(resolvedPath) { readByteArray() }
                call.respondBytes(bytes, contentType = resolvedPath.name.toContentType())
            } else {
                val index = base / "index.html"
                if (fs.exists(index)) {
                    val bytes = fs.read(index) { readByteArray() }
                    call.respondBytes(bytes, contentType = ContentType.Text.Html)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun String.toContentType(): ContentType = when {
    endsWith(".html") -> ContentType.Text.Html
    endsWith(".js") || endsWith(".mjs") -> ContentType.Application.JavaScript
    endsWith(".css") -> ContentType.Text.CSS
    endsWith(".json") -> ContentType.Application.Json
    endsWith(".png") -> ContentType.Image.PNG
    endsWith(".jpg") || endsWith(".jpeg") -> ContentType.Image.JPEG
    endsWith(".svg") -> ContentType.Image.SVG
    endsWith(".ico") -> ContentType("image", "x-icon")
    endsWith(".woff") -> ContentType("font", "woff")
    endsWith(".woff2") -> ContentType("font", "woff2")
    endsWith(".webmanifest") -> ContentType.Application.Json
    else -> ContentType.Application.OctetStream
}
