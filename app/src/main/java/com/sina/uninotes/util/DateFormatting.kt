package com.sina.uninotes.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormatting {
    private val englishLocale = Locale.ENGLISH
    private val headerFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", englishLocale)
    private val olderFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", englishLocale)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", englishLocale)

    fun todayLocalDate(zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.now(zoneId)

    fun localDateKey(date: LocalDate): String = date.toString()

    fun localDateKeyFromEpoch(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().toString()

    fun galleryHeader(
        localDate: LocalDate,
        today: LocalDate = todayLocalDate(),
    ): String = when (localDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> localDate.format(headerFormatter)
    }

    fun noteListDate(
        localDate: LocalDate,
        today: LocalDate = todayLocalDate(),
    ): String = when (localDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> localDate.format(olderFormatter)
    }

    fun photoDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(dateTimeFormatter)
}
