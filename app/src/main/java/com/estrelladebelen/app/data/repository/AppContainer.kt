package com.estrelladebelen.app.data.repository

import android.content.Context
import com.estrelladebelen.app.data.local.AppDatabase

// Simple service locator — replaced by proper DI (Hilt/Koin) if needed later.
object AppContainer {
    lateinit var meditationRepository: MeditationRepository
        private set
    lateinit var userRepository: UserRepository
        private set
    lateinit var subscriptionRepository: SubscriptionRepository
        private set

    fun init(context: Context) {
        val dao = AppDatabase.getInstance(context).meditationDao()
        meditationRepository   = FirebaseMeditationRepository(dao)
        userRepository         = FirebaseUserRepository(context)
        subscriptionRepository = StubSubscriptionRepository()
    }
}
