package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class FirebaseUserRepository : UserRepository {

    private val auth      = Firebase.auth
    private val firestore = Firebase.firestore

    // Single callbackFlow that handles both auth state and Firestore doc changes.
    override val currentUser: Flow<UserProfile?> = callbackFlow {
        var firestoreReg: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreReg?.remove()
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                trySend(null)
                firestoreReg = null
            } else {
                firestoreReg = firestore.collection("users").document(uid)
                    .addSnapshotListener { snap, _ ->
                        trySend(snap?.toUserProfile(uid))
                    }
            }
        }

        auth.addAuthStateListener(authListener)
        awaitClose {
            auth.removeAuthStateListener(authListener)
            firestoreReg?.remove()
        }
    }

    override suspend fun signIn(email: String, password: String): Result<UserProfile> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: error("UID nulo")
            fetchOrCreateUserProfile(uid, email, email.substringBefore("@").replaceFirstChar { it.uppercase() })
        }

    override suspend fun register(name: String, email: String, password: String): Result<UserProfile> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: error("UID nulo")
            val profile = UserProfile(uid = uid, displayName = name, email = email)
            firestore.collection("users").document(uid).set(profile.toMap()).await()
            profile
        }

    override suspend fun signOut() { auth.signOut() }

    override suspend fun toggleFavorite(meditationId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = firestore.collection("users").document(uid)
        firestore.runTransaction { tx ->
            @Suppress("UNCHECKED_CAST")
            val favorites = (tx.get(ref).get("favorites") as? List<String>)?.toMutableList() ?: mutableListOf()
            if (meditationId in favorites) favorites.remove(meditationId) else favorites.add(meditationId)
            tx.update(ref, "favorites", favorites)
        }.await()
    }

    override suspend fun recordSession(durationMinutes: Int) {
        val uid = auth.currentUser?.uid ?: return
        val ref = firestore.collection("users").document(uid)
        val today     = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val lastDate = snap.getString("lastSessionDate") ?: ""
            val streak   = snap.getLong("streak")?.toInt() ?: 0
            val newStreak = when (lastDate) {
                today     -> streak
                yesterday -> streak + 1
                else      -> 1
            }
            tx.update(ref, mapOf(
                "totalSessions"   to (snap.getLong("totalSessions") ?: 0) + 1,
                "totalMinutes"    to (snap.getLong("totalMinutes") ?: 0) + durationMinutes,
                "streak"          to newStreak,
                "lastSessionDate" to today
            ))
        }.await()
    }

    override suspend fun updateNotificationSettings(enabled: Boolean, time: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .set(mapOf("notificationsEnabled" to enabled, "notificationTime" to time), SetOptions.merge())
            .await()
    }

    private suspend fun fetchOrCreateUserProfile(uid: String, email: String, name: String): UserProfile {
        val ref  = firestore.collection("users").document(uid)
        val snap = ref.get().await()
        return if (snap.exists()) {
            snap.toUserProfile(uid) ?: UserProfile(uid = uid, displayName = name, email = email)
        } else {
            val profile = UserProfile(uid = uid, displayName = name, email = email)
            ref.set(profile.toMap()).await()
            profile
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUserProfile(uid: String): UserProfile? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        return UserProfile(
            uid                  = uid,
            displayName          = getString("displayName") ?: "",
            email                = getString("email") ?: "",
            favorites            = (get("favorites") as? List<String>) ?: emptyList(),
            totalSessions        = getLong("totalSessions")?.toInt() ?: 0,
            totalMinutes         = getLong("totalMinutes")?.toInt() ?: 0,
            streak               = getLong("streak")?.toInt() ?: 0,
            lastSessionDate      = getString("lastSessionDate") ?: "",
            notificationsEnabled = getBoolean("notificationsEnabled") ?: false,
            notificationTime     = getString("notificationTime") ?: "08:00"
        )
    }

    private fun UserProfile.toMap() = mapOf(
        "displayName"          to displayName,
        "email"                to email,
        "favorites"            to favorites,
        "totalSessions"        to totalSessions,
        "totalMinutes"         to totalMinutes,
        "streak"               to streak,
        "lastSessionDate"      to lastSessionDate,
        "notificationsEnabled" to notificationsEnabled,
        "notificationTime"     to notificationTime
    )
}
