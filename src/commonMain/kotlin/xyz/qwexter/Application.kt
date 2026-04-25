package xyz.qwexter

import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import xyz.qwexter.tat.repository.RecordsRepository
import xyz.qwexter.tat.repository.TasksRepository

fun main() {
    val config = loadConfig()
    embeddedServer(CIO, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = loadConfig()) {
    configureHTTP()
    configureSerialization()
    configureDatabases(dbPath = config.dbPath)
    configureRouting(
        tasksRepository = TasksRepository.create(db),
        recordsRepository = RecordsRepository.create(db),
        authMode = config.authMode,
        corsEnabled = config.staticPath == null,
    )
    config.staticPath?.let { configureStatic(it) }
}
