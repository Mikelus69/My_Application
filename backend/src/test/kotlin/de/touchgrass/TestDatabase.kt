package de.touchgrass

import de.touchgrass.db.DatabaseFactory

object TestDatabase {
    private val appDatabase by lazy { DatabaseFactory.startForTests() }
    val database get() = appDatabase.database
}
