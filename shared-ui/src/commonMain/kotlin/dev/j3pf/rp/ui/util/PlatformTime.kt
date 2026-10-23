package dev.j3pf.rp.ui.util

expect fun currentTimeMillis(): Long

expect fun formatPlatformDateTime(epochMilli: Long): String

expect fun formatPlatformTime(epochMilli: Long): String

expect fun parseIsoEpochSeconds(isoString: String): Long?
