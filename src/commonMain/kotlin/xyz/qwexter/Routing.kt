package xyz.qwexter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.resources.Resources
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    install(Resources)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        get("/todos") {
            val todos = db.tatDatabaseQueries.selectAllTodos().executeAsList()
            call.respondText(todos.joinToString("\n") { "#${it.id} ${it.title} done=${it.done}" })
        }
        post("/todos") {
            val title = call.receiveText()
            db.tatDatabaseQueries.insertTodo(title, kotlinx.datetime.Clock.System.now().epochSeconds)
            call.respondText("inserted: $title", status = HttpStatusCode.Created)
        }
    }
}
