package com.mathmilo.domain.daily

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Injected time source so date logic can be tested deterministically. */
interface TimeProvider {
    fun nowMillis(): Long
    fun today(): LocalDate
}

class SystemTimeProvider(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now(zone())
}

/** Rules for which daily set a child may answer. Uses the device's local calendar date only. */
object DayRules {
    fun key(date: LocalDate): String = date.toString() // ISO yyyy-MM-dd

    fun parse(key: String): LocalDate = LocalDate.parse(key)

    /**
     * A question may be answered only in the session that opened it on the set's own date.
     * If midnight passes while the question is open, that question can still be finished.
     */
    fun canAnswer(setDate: LocalDate, sessionOpenedOn: LocalDate): Boolean = setDate == sessionOpenedOn

    /** After finishing a question, a newer local date means a new set is ready. */
    fun isNewDayReady(setDate: LocalDate, today: LocalDate): Boolean = today != setDate

    fun localDateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
