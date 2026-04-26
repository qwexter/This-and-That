package xyz.qwexter

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import xyz.qwexter.db.TatDatabase

private val DatabaseKey = AttributeKey<TatDatabase>("TatDatabase")

val Application.db: TatDatabase
    get() = attributes[DatabaseKey]

fun Application.configureDatabases(
    dbPath: String = "tat.db",
    driver: SqlDriver = NativeSqliteDriver(TatDatabase.Schema, dbPath),
) {
    val currentVersion = driver.getVersion()
    if (currentVersion == 0L) {
        TatDatabase.Schema.create(driver)
        driver.setVersion(TatDatabase.Schema.version)
    } else {
        TatDatabase.Schema.migrate(driver, currentVersion, TatDatabase.Schema.version)
        driver.setVersion(TatDatabase.Schema.version)
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
