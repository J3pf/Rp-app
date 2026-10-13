package dev.j3pf.rp.di

import javax.inject.Qualifier

/** Scope de corrutinas con la vida del proceso. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
