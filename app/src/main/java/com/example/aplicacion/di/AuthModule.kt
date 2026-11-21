package com.example.aplicacion.di

import com.example.aplicacion.data.auth.AuthRepository
import com.example.aplicacion.data.auth.FirebaseAuthRepository
import com.example.aplicacion.data.cash.FirebaseInitialBalanceRepository
import com.example.aplicacion.data.cash.InitialBalanceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Dependencias compartidas de la cuenta y su efectivo, construidas por Hilt. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    /** Asocia el contrato remoto con su implementación Firebase. */
    @Binds @Singleton
    abstract fun bindAuthRepository(repository: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun bindInitialBalanceRepository(repository: FirebaseInitialBalanceRepository): InitialBalanceRepository

    companion object {
        /** Recupera Authentication, que conserva la sesión entre aperturas. */
        @Provides @Singleton
        fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        /** Evita dejar datos privados en la caché persistente del dispositivo. */
        @Provides @Singleton
        fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }
}
