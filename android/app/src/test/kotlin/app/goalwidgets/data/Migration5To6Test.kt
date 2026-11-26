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
 * Migration Room 5 → 6 (comptes à rebours « chaque année ») rejouée sur un vrai SQLite (JDBC, en JVM),
 * sur le modèle de [Migration4To5Test] : aucune donnée perdue, répétition désactivée pour les objectifs
 * existants, table identique à une installation neuve en v6. Vérifie aussi la chaîne complète 1 → 6.
 */
class Migration5To6Test {

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

    private fun Connection.migrate() = Migration5To6.STATEMENTS.forEach { exec(it) }

    private fun Connection.tableInfo() = rows("PRAGMA table_info(goals)").sortedBy { it[1] as String }.map { it.drop(1) }

    private fun Connection.indexes() = rows("PRAGMA index_list(goals)").map { row ->
        listOf(row[1], row[2]) + rows("PRAGMA index_info(`${row[1]}`)").map { it[2] }
    }.sortedBy { it[0] as String }

    private val v5Columns = "id, title, kind, startEpochDay, targetEpochDay, createdAtMillis, uid, color, emoji, notificationsEnabled, chartUnit, streak, streakHistory"

    @Test
    fun `aucune donnée perdue, pas de répétition pour les objectifs existants`() {
        val db = memoryDb()
        db.createFromSchema(5)
        db.exec(
            "INSERT INTO goals ($v5Columns) VALUES " +
                "(1, 'Examen', 'countdown', 20454, 20619, 1767225600000, 'a', 'blue', '📚', 1, 'week', 0, ''), " +
                "(2, 'Arrêt du tabac', 'since', 19782, NULL, 1709236800000, 'b', NULL, NULL, 0, NULL, 1, '2024-01-01/2024-02-01')",
        )
        val before = db.rows("SELECT $v5Columns FROM goals ORDER BY id")

        db.migrate()

        assertEquals(before, db.rows("SELECT $v5Columns FROM goals ORDER BY id"))
        assertEquals(listOf(listOf<Any?>(0), listOf<Any?>(0)), db.rows("SELECT repeatYearly FROM goals ORDER BY id"))
    }

    @Test
    fun `table migrée identique à une installation neuve en v6`() {
        val migrated = memoryDb().apply { createFromSchema(5); migrate() }
        val fresh = memoryDb().apply { createFromSchema(6) }

        assertEquals(fresh.tableInfo(), migrated.tableInfo())
        assertEquals(fresh.indexes(), migrated.indexes())
    }

    @Test
    fun `chaîne complète 1 vers 6 identique à une installation neuve en v6`() {
        val migrated = memoryDb().apply {
            createFromSchema(1)
            Migration1To2.STATEMENTS.forEach { exec(it) }
            Migration2To3.STATEMENTS.forEach { exec(it) }
            Migration3To4.STATEMENTS.forEach { exec(it) }
            Migration4To5.STATEMENTS.forEach { exec(it) }
            migrate()
        }
        val fresh = memoryDb().apply { createFromSchema(6) }

        assertEquals(fresh.tableInfo(), migrated.tableInfo())
        assertEquals(fresh.indexes(), migrated.indexes())
    }
}
