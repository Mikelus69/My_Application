package de.touchgrass

import de.touchgrass.db.DatabaseFactory
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object TestDatabase {
    private val appDatabase by lazy { DatabaseFactory.startForTests() }
    val database get() = appDatabase.database

    fun reset() {
        transaction(database) { exec("TRUNCATE users RESTART IDENTITY CASCADE") }
    }
}
