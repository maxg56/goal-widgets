package app.goalwidgets.data

import app.goalwidgets.core.ExportedGoal
import app.goalwidgets.core.Goal
import app.goalwidgets.core.GoalKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** `notificationsEnabled` dans le format d'export : écrit tel quel, `null` à l'import = défaut (désactivé). */
class GoalEntityNotificationsExportTest {

    private fun exported(notifications: Boolean?) = ExportedGoal(
        id = "0b6f8f9e-2f4a-4c1e-9a7b-1d2e3f4a5b6c",
        goal = Goal(GoalKind.SINCE, LocalDate.parse("2025-01-01"), title = "Arrêt du tabac"),
        notificationsEnabled = notifications,
        createdAt = Instant.parse("2025-01-01T08:00:00Z"),
    )

    @Test
    fun `import - valeur du fichier appliquée, null donne désactivé`() {
        assertEquals(true, GoalEntity.fromExported(exported(true)).notificationsEnabled)
        assertEquals(false, GoalEntity.fromExported(exported(false)).notificationsEnabled)
        assertEquals(false, GoalEntity.fromExported(exported(null)).notificationsEnabled)
    }

    @Test
    fun `export - l'état stocké est écrit`() {
        assertEquals(true, GoalEntity.fromExported(exported(true)).toExported().notificationsEnabled)
        assertEquals(false, GoalEntity.fromExported(exported(null)).toExported().notificationsEnabled)
    }
}
