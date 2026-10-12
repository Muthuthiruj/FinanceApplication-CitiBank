package com.example.financeapplication.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun database(): FirebaseDatabase = FirebaseDatabase.getInstance().apply {
        // Offline cache: reads work without a connection and writes are queued and synced later.
        // Must be called before the first database reference is created.
        try { setPersistenceEnabled(true) } catch (_: Exception) { /* already configured */ }
    }
}
