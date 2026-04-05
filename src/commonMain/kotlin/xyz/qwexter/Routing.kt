package xyz.qwexter

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
