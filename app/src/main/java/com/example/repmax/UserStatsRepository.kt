package com.example.repmax

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object UserStatsRepository {

    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    fun loadUserStats(
        onSuccess: (UserStats) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("stats")
                .document("totals")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val stats = document.toObject(UserStats::class.java) ?: UserStats()
                        onSuccess(stats)
                    } else {
                        onSuccess(UserStats())
                    }
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        } else {
            onFailure(IllegalStateException("No authenticated user"))
        }
    }

    fun initializeUserStats(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val initialStats = UserStats()
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("stats")
                .document("totals")
                .set(initialStats)
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                }
        } else {
            onFailure(IllegalStateException("No authenticated user"))
        }
    }

    fun updateTotalReps(
        reps: Int,
        exerciseType: ExerciseType,
        onSuccess: (UserStats) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null || reps <= 0) return

        val userStatsRef = firestore.collection("users")
            .document(currentUser.uid)
            .collection("stats")
            .document("totals")

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userStatsRef)
            val currentStats = if (snapshot.exists()) {
                snapshot.toObject(UserStats::class.java) ?: UserStats()
            } else {
                UserStats()
            }

            val updatedStats = when (exerciseType) {
                ExerciseType.SQUAT -> currentStats.copy(
                    totalReps = currentStats.totalReps + reps,
                    squatReps = currentStats.squatReps + reps,
                    lastUpdated = System.currentTimeMillis()
                )
                ExerciseType.PUSH_UP -> currentStats.copy(
                    totalReps = currentStats.totalReps + reps,
                    pushUpReps = currentStats.pushUpReps + reps,
                    lastUpdated = System.currentTimeMillis()
                )
                ExerciseType.PULL_UP -> currentStats.copy(
                    totalReps = currentStats.totalReps + reps,
                    pullUpReps = currentStats.pullUpReps + reps,
                    lastUpdated = System.currentTimeMillis()
                )
            }

            transaction.set(userStatsRef, updatedStats)
            updatedStats
        }.addOnSuccessListener { updatedStats ->
            Log.d("UserStatsRepository", "Stats updated successfully: ${updatedStats.totalReps} total reps")
            onSuccess(updatedStats)
        }.addOnFailureListener { exception ->
            Log.e("UserStatsRepository", "Error updating stats", exception)
            onFailure(exception)
        }
    }
}
