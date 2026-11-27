package app.goalwidgets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Migration Room 6 → 7 (réglages de notification par objectif) rejouée sur un vrai SQLite (JDBC,
 * en JVM) : aucune donnée perdue, réglages vides pour les objectifs existants, table identique à une
 * installation neuve en v7. Vérifie aussi la chaîne complète 1 → 7.
 */
class Migration6To7Test {

    private val connections = mutableListOf<Connection>()

    @AfterEach
    fun close() = connections.forEach(Connection::close)

    private fun memoryDb(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { connections += it }

    private fun schema(version: Int): JsonObject {
        val dir = System.getProperty("roomSchemasDir") ?: error("Propriété système roomSchemasDir absente")
        val file = File(dir, "app.goalwidgets.data.GoalDatabase/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    private fun Connection.createFromSchema(version: Int) {
        val entity = schema(version).getValue("entities").jsonArray.single().jsonObject
        exec(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "goals"))
        entity["indices"]?.jsonArray?.forEach {
            exec(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", "goals"))
        }
    }

    private fun Connection.exec(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.rows(sql: String): List<List<Any?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val n = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..n).map { rs.getObject(it) }) }
        }
    }

    private fun Connection.migrate() = Migration6To7.STATEMENTS.forEach { exec(it) }

    private fun Connection.tableInfo() = rows("PRAGMA table_info(goals)").sortedBy { it[1] as String }.map { it.drop(1) }

    private fun Connection.indexes() = rows("PRAGMA index_list(goals)").map { row ->
        listOf(row[1], row[2]) + rows("PRAGMA index_info(`${row[1]}`)").map { it[2] }
    }.sortedBy { it[0] as String }

    private val v6Columns = "id, title, kind, startEpochDay, targetEpochDay, createdAtMillis, uid, color, emoji, notificationsEnabled, chartUnit, streak, streakHistory, repeatYearly, archived"

    @Test
    fun `aucune donnée perdue, réglages vides pour les objectifs existants`() {
        val db = memoryDb()
        db.createFromSchema(6)
        db.exec(
            "INSERT INTO goals ($v6Columns) VALUES " +
                "(1, 'Anniversaire', 'countdown', 20454, 20619, 1767225600000, 'a', 'pink', '🎂', 1, NULL, 0, '', 1, 0), " +
                "(2, 'Sans fumer', 'since', 19782, NULL, 1709236800000, 'b', NULL, NULL, 1, 'week', 1, '2024-01-01/2024-02-01', 0, 1)",
        )
        val before = db.rows("SELECT $v6Columns FROM goals ORDER BY id")

        db.migrate()

        assertEquals(before, db.rows("SELECT $v6Columns FROM goals ORDER BY id"))
        assertEquals(listOf(listOf<Any?>(""), listOf<Any?>("")), db.rows("SELECT notificationPrefs FROM goals ORDER BY id"))
    }

    @Test
    fun `table migrée identique à une installation neuve en v7`() {
        val migrated = memoryDb().apply { createFromSchema(6); migrate() }
        val fresh = memoryDb().apply { createFromSchema(7) }

        assertEquals(fresh.tableInfo(), migrated.tableInfo())
        assertEquals(fresh.indexes(), migrated.indexes())
    }

    @Test
    fun `chaîne complète 1 vers 7 identique à une installation neuve en v7`() {
        val migrated = memoryDb().apply {
            createFromSchema(1)
            Migration1To2.STATEMENTS.forEach { exec(it) }
            Migration2To3.STATEMENTS.forEach { exec(it) }
            Migration3To4.STATEMENTS.forEach { exec(it) }
            Migration4To5.STATEMENTS.forEach { exec(it) }
            Migration5To6.STATEMENTS.forEach { exec(it) }
            migrate()
        }
        val fresh = memoryDb().apply { createFromSchema(7) }

        assertEquals(fresh.tableInfo(), migrated.tableInfo())
        assertEquals(fresh.indexes(), migrated.indexes())
    }
}
