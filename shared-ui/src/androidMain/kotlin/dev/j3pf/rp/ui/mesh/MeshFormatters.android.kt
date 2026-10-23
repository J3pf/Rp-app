package dev.j3pf.rp.ui.mesh

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatMeshDateTime(
    at: Long,
    locale: Locale,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(at))

fun formatMeshTime(
    at: Long,
    locale: Locale,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(at))
