package app.goalwidgets.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

class LocalMidnightTest {
    @Test
    fun `en journée, le prochain minuit est le début du lendemain`() {
        assertEquals(
            ZonedDateTime.parse("2026-01-07T00:00+01:00[Europe/Paris]"),
            LocalMidnight.next(ZonedDateTime.parse("2026-01-06T15:30+01:00[Europe/Paris]")),
        )
    }

    @Test
    fun `pile à minuit, le prochain minuit est celui du lendemain`() {
        assertEquals(
            ZonedDateTime.parse("2026-01-07T00:00+01:00[Europe/Paris]"),
            LocalMidnight.next(ZonedDateTime.parse("2026-01-06T00:00+01:00[Europe/Paris]")),
        )
    }

    @Test
    fun `une seconde avant minuit, le prochain minuit est dans une seconde`() {
        assertEquals(
            ZonedDateTime.parse("2026-01-01T00:00+01:00[Europe/Paris]"),
            LocalMidnight.next(ZonedDateTime.parse("2025-12-31T23:59:59+01:00[Europe/Paris]")),
        )
    }

    @Test
    fun `passage à l'heure d'été à minuit, le jour commence à 1 h`() {
        // São Paulo, 4 novembre 2018 : minuit n'existe pas, l'horloge saute de 0 h à 1 h.
        assertEquals(
            ZonedDateTime.parse("2018-11-04T01:00-02:00[America/Sao_Paulo]"),
            LocalMidnight.next(ZonedDateTime.parse("2018-11-03T12:00-03:00[America/Sao_Paulo]")),
        )
    }
}
