package app.goalwidgets.core

import java.time.ZonedDateTime

/** Changement de jour local : instant où les widgets doivent être redessinés. */
object LocalMidnight {
    /** Premier instant du jour suivant [now], dans le fuseau de [now]. */
    fun next(now: ZonedDateTime): ZonedDateTime =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
}
