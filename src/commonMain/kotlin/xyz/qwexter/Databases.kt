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
    TatDatabase.Schema.create(driver)
    attributes.put(DatabaseKey, TatDatabase(driver))
}
