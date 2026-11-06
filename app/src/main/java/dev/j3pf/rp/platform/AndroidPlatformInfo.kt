package dev.j3pf.rp.platform

import dev.j3pf.rp.BuildConfig
import dev.j3pf.rp.data.ports.PlatformInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPlatformInfo
    @Inject
    constructor() : PlatformInfo {
        override val versionName: String = BuildConfig.VERSION_NAME
        override val versionCode: Long = BuildConfig.VERSION_CODE.toLong()
        override val flavor: String = BuildConfig.FLAVOR
    }
