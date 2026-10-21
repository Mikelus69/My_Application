package de.touchgrass.sports

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object SportTypesTable : Table("sport_types") {
    val code = varchar("code", 30)
    val name = varchar("name", 50)
    val emoji = varchar("emoji", 10)
    val met = decimal("met", 4, 1)
    override val primaryKey = PrimaryKey(code)
}

@Serializable
data class SportDto(val code: String, val name: String, val emoji: String, val met: Double)

class SportRepository(private val database: Database) {
    fun all(): List<SportDto> = transaction(database) {
        SportTypesTable.selectAll().orderBy(SportTypesTable.name).map { it.toDto() }
    }

    fun find(code: String): SportDto? = transaction(database) {
        SportTypesTable.selectAll().where { SportTypesTable.code eq code }.singleOrNull()?.toDto()
    }

    private fun ResultRow.toDto() = SportDto(
        code = this[SportTypesTable.code],
        name = this[SportTypesTable.name],
        emoji = this[SportTypesTable.emoji],
        met = this[SportTypesTable.met].toDouble(),
    )
}
