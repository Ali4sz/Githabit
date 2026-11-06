package com.githabit.prototype.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Injectable time source for "today" and instants. Production uses the system clock;
 * tests inject fixed clocks so date-boundary behavior is deterministic.
 */
interface AppClock {
    fun today(): LocalDate
    fun now(): Instant
    fun zone(): ZoneId
}

/** One observation: the local date is derived from this instant in this zone. */
data class CommandTime(val instant: Instant, val zone: ZoneId) {
    val date: LocalDate = instant.atZone(zone).toLocalDate()
}

fun AppClock.capture(): CommandTime {
    val zone = zone()
    return CommandTime(now(), zone)
}

class SystemAppClock(private val clock: Clock = Clock.systemUTC()) : AppClock {
    override fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    override fun now(): Instant = clock.instant()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
