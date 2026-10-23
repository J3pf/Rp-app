package dev.j3pf.rp.ui.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.timeIntervalSince1970

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun formatPlatformDateTime(epochMilli: Long): String {
    val date = NSDate(timeIntervalSinceReferenceDate = (epochMilli / 1000.0) - 978307200.0)
    val formatter =
        NSDateFormatter().apply {
            dateStyle = NSDateFormatterShortStyle
            timeStyle = NSDateFormatterShortStyle
        }
    return formatter.stringFromDate(date)
}

actual fun formatPlatformTime(epochMilli: Long): String {
    val date = NSDate(timeIntervalSinceReferenceDate = (epochMilli / 1000.0) - 978307200.0)
    val formatter =
        NSDateFormatter().apply {
            dateStyle = 0u
            timeStyle = NSDateFormatterShortStyle
        }
    return formatter.stringFromDate(date)
}

actual fun parseIsoEpochSeconds(isoString: String): Long? {
    val formatter = NSISO8601DateFormatter()
    return formatter.dateFromString(isoString)?.timeIntervalSince1970?.toLong()
}
