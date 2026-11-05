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

class SystemAppClock(private val clock: Clock = Clock.systemUTC()) : AppClock {
    override fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    override fun now(): Instant = clock.instant()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
