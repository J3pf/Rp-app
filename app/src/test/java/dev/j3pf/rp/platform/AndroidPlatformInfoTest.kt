package dev.j3pf.rp.platform

import dev.j3pf.rp.BuildConfig
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidPlatformInfoTest {
    @Test
    fun reportsDirectFlavorBuildConfigValues() {
        val info = AndroidPlatformInfo()

        assertEquals(BuildConfig.VERSION_NAME, info.versionName)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), info.versionCode)
        assertEquals(BuildConfig.FLAVOR, info.flavor)
        assertEquals("direct", info.flavor)
    }
}
