package app.goalwidgets

import android.app.Application
import app.goalwidgets.data.GoalDatabase

class GoalWidgetsApplication : Application() {
    val database: GoalDatabase by lazy { GoalDatabase.create(this) }
}
