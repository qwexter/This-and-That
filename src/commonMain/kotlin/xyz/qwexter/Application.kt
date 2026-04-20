package xyz.qwexter

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import xyz.qwexter.tat.repository.TasksRepository

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureHTTP()
    configureSerialization()
    configureDatabases()
    configureRouting(
        tasksRepository = TasksRepository.buildInMemory(emptyList()),
    )
}
