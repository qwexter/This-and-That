package xyz.qwexter

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import xyz.qwexter.db.TatDatabase

private val DatabaseKey = AttributeKey<TatDatabase>("TatDatabase")

val Application.db: TatDatabase
    get() = attributes[DatabaseKey]

private fun createDriver(dbPath: String): SqlDriver {
    val lastSep = dbPath.lastIndexOf('/')
    return if (lastSep >= 0) {
        val dir = dbPath.substring(0, lastSep)
        val name = dbPath.substring(lastSep + 1)
        NativeSqliteDriver(
            DatabaseConfiguration(
                name = name,
                version = TatDatabase.Schema.version.toInt(),
                create = { conn -> wrapConnection(conn) { TatDatabase.Schema.create(it) } },
                upgrade = { conn, oldVersion, newVersion ->
                    wrapConnection(conn) { TatDatabase.Schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
                },
                extendedConfig = DatabaseConfiguration.Extended(basePath = dir),
            )
        )
    } else {
        NativeSqliteDriver(TatDatabase.Schema, dbPath)
    }
}

fun Application.configureDatabases(
    dbPath: String = "tat.db",
    driver: SqlDriver = createDriver(dbPath),
) {
    // DatabaseConfiguration handles schema lifecycle when path contains '/';
    // for bare filenames NativeSqliteDriver doesn't run migrations automatically.
    if (!dbPath.contains('/')) {
        val currentVersion = driver.getVersion()
        if (currentVersion == 0L) {
            TatDatabase.Schema.create(driver)
            driver.setVersion(TatDatabase.Schema.version)
        } else {
            TatDatabase.Schema.migrate(driver, currentVersion, TatDatabase.Schema.version)
            driver.setVersion(TatDatabase.Schema.version)
        }
    }
    attributes.put(DatabaseKey, TatDatabase(driver))
}

private fun SqlDriver.getVersion(): Long {
    val result = executeQuery(null, "PRAGMA user_version", { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
    }, 0)
    return result.value ?: 0L
}

private fun SqlDriver.setVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}
