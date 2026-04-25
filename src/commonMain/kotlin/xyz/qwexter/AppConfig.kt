package xyz.qwexter

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

enum class AuthMode {
    NONE, // dev: hardcoded owner, no auth check
    HEADER, // prod: trust X-User-Id header injected by reverse proxy
}

data class AppConfig(
    val host: String,
    val port: Int,
    val dbPath: String,
    val staticPath: String?,
    val authMode: AuthMode,
)

@OptIn(ExperimentalForeignApi::class)
fun loadConfig(): AppConfig {
    fun env(key: String): String? = getenv(key)?.toKString()

    val port = env("PORT")?.toIntOrNull() ?: 8080
    val host = env("HOST") ?: "0.0.0.0"
    val dbPath = env("DB_PATH") ?: "tat.db"
    val staticPath = env("STATIC_PATH")
    val authMode = env("AUTH_MODE")?.uppercase()?.let {
        runCatching { AuthMode.valueOf(it) }.getOrNull()
    } ?: AuthMode.NONE

    return AppConfig(host = host, port = port, dbPath = dbPath, staticPath = staticPath, authMode = authMode)
}
