package xyz.qwexter

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.server.application.*
import io.ktor.util.*
import xyz.qwexter.db.TatDatabase

private val DatabaseKey = AttributeKey<TatDatabase>("TatDatabase")

val Application.db: TatDatabase
    get() = attributes[DatabaseKey]

fun Application.configureDatabases() {
    val driver = NativeSqliteDriver(TatDatabase.Schema, "tat.db")
    attributes.put(DatabaseKey, TatDatabase(driver))
}
