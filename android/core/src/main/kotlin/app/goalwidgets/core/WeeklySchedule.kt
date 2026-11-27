package app.goalwidgets.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Résumé hebdomadaire : prochain envoi, strictement après [now], le jour [day] à l'heure [time] (heure locale). */
object WeeklySchedule {
    fun next(now: LocalDateTime, day: DayOfWeek, time: LocalTime): LocalDateTime {
        val thisWeek = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(time)
        return if (thisWeek.isAfter(now)) thisWeek else thisWeek.plusWeeks(1)
    }
}
