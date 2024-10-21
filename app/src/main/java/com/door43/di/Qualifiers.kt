package com.door43.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Production

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Development