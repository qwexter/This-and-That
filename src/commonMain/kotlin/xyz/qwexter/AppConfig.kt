package xyz.qwexter

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

data class AppConfig(
    val host: String,
    val port: Int,
    val dbPath: String,
)

@OptIn(ExperimentalForeignApi::class)
fun loadConfig(): AppConfig {
    fun env(key: String): String? = getenv(key)?.toKString()

    val port = env("PORT")?.toIntOrNull() ?: 8080
    val host = env("HOST") ?: "0.0.0.0"
    val dbPath = env("DB_PATH") ?: "tat.db"

    return AppConfig(host = host, port = port, dbPath = dbPath)
}
