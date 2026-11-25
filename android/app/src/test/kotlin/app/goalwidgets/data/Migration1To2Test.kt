package app.goalwidgets.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Migration Room 1 → 2 rejouée sur un vrai SQLite (JDBC, en JVM) : on crée la table du schéma
 * exporté v1, on y met des objectifs, on exécute [Migration1To2.STATEMENTS], puis on vérifie
 * (1) qu'aucune donnée n'est perdue et (2) que la table obtenue est identique à celle que Room
 * crée pour une installation neuve en v2 (`schemas/.../2.json`) : colonnes, types, NOT NULL,
 * défauts, index — c'est ce que Room valide à l'ouverture de la base.
 */
class Migration1To2Test {

    private val connections = mutableListOf<Connection>()

    @AfterEach
    fun close() = connections.forEach(Connection::close)

    private fun memoryDb(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:").also { connections += it }

    private fun schema(version: Int): JsonObject {
        val dir = System.getProperty("roomSchemasDir") ?: error("Propriété système roomSchemasDir absente")
        val file = File(dir, "app.goalwidgets.data.GoalDatabase/$version.json")
        return Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
    }

    /** Crée la table `goals` (et ses index) exactement comme Room pour ce schéma. */
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

    private fun Connection.migrate() = Migration1To2.STATEMENTS.forEach { exec(it) }

    @Test
    fun `aucune donnée perdue et nouvelles colonnes initialisées`() {
        val db = memoryDb()
        db.createFromSchema(1)
        db.exec(
            "INSERT INTO goals (id, title, kind, startEpochDay, targetEpochDay, createdAtMillis) VALUES " +
                "(1, 'Examen', 'countdown', 20454, 20619, 1767225600000), " +
                "(2, 'Arrêt du tabac 🚭', 'since', 19782, NULL, 1709236800000), " +
                "(7, 'Voyage', 'countdown', 20454, 20500, 1767225600001)",
        )
        val before = db.rows("SELECT id, title, kind, startEpochDay, targetEpochDay, createdAtMillis FROM goals ORDER BY id")

        db.migrate()

        assertEquals(before, db.rows("SELECT id, title, kind, startEpochDay, targetEpochDay, createdAtMillis FROM goals ORDER BY id"))
        val added = db.rows("SELECT uid, color, emoji FROM goals ORDER BY id")
        val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        added.forEach { (uid, color, emoji) ->
            assertTrue(uuidV4.matches(uid as String), "uid n'est pas un UUID v4 minuscule : $uid")
            assertNull(color)
            assertNull(emoji)
        }
        assertEquals(added.size, added.map { it[0] }.toSet().size, "uid en double")
    }

    @Test
    fun `table migrée identique à une installation neuve en v2`() {
        val migrated = memoryDb().apply { createFromSchema(1); migrate() }
        val fresh = memoryDb().apply { createFromSchema(2) }

        // cid, name, type, notnull, dflt_value, pk
        assertEquals(fresh.rows("PRAGMA table_info(goals)").sortedBy { it[1] as String }.map { it.drop(1) },
            migrated.rows("PRAGMA table_info(goals)").sortedBy { it[1] as String }.map { it.drop(1) })
        // seq, name, unique, origin, partial
        fun indexes(c: Connection) = c.rows("PRAGMA index_list(goals)").map { row ->
            listOf(row[1], row[2]) + c.rows("PRAGMA index_info(`${row[1]}`)").map { it[2] }
        }.sortedBy { it[0] as String }
        assertEquals(indexes(fresh), indexes(migrated))
    }

    @Test
    fun `l'index unique interdit deux objectifs de même uid après migration`() {
        val db = memoryDb().apply { createFromSchema(1); migrate() }
        db.exec("INSERT INTO goals (title, kind, startEpochDay, createdAtMillis, uid) VALUES ('A', 'since', 0, 0, 'x')")
        val duplicate = runCatching {
            db.exec("INSERT INTO goals (title, kind, startEpochDay, createdAtMillis, uid) VALUES ('B', 'since', 0, 0, 'x')")
        }
        assertTrue(duplicate.isFailure)
    }
}
