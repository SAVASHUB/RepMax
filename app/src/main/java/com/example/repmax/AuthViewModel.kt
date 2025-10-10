package com.example.repmax

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class UserStats(
    val totalReps: Int = 0,
    val squatReps: Int = 0,
    val pushUpReps: Int = 0,
    val pullUpReps: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

class AuthViewModel : ViewModel() {

    private val auth : FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _authState = MutableLiveData<AuthState>()
    val authState : LiveData<AuthState> = _authState

    private val _totalReps = MutableLiveData<Int>()
    val totalReps: LiveData<Int> = _totalReps

    init {
        checkAuthState()
    }

    fun checkAuthState () {
        if(auth.currentUser == null) {
            _authState.value = AuthState.Unauthenticated
        }else {
            _authState.value = AuthState.Authenticated
            loadUserStats() // Load stats when user is authenticated
        }
    }

    fun login(email : String,password : String){

        if(email.isEmpty() || password.isEmpty()){
            _authState.value = AuthState.Error("Email or password can't be empty")
            return
        }
        _authState.value = AuthState.LoadingAuth
        auth.signInWithEmailAndPassword(email,password)
            .addOnCompleteListener{task->
                if (task.isSuccessful){
                    _authState.value = AuthState.Authenticated
                    loadUserStats() // Load stats after successful login
                }else{
                    _authState.value = AuthState.Error(task.exception?.message?:"Something went wrong")
                }
            }
    }

    fun signUp(email : String,password : String){

        if(email.isEmpty() || password.isEmpty()){
            _authState.value = AuthState.Error("Email or password can't be empty")
            return
        }
        _authState.value = AuthState.LoadingAuth
        auth.createUserWithEmailAndPassword(email,password)
            .addOnCompleteListener{task->
                if (task.isSuccessful){
                    _authState.value = AuthState.Authenticated
                    // Initialize user stats for new user
                    initializeUserStats()
                }else{
                    _authState.value = AuthState.Error(task.exception?.message?:"Something went wrong")
                }
            }
    }

    fun signOut(){
        auth.signOut()
        _authState.value = AuthState.Unauthenticated
        _totalReps.value = 0 // Reset total reps on sign out
    }

    fun loadUserStats() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("stats")
                .document("totals")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val stats = document.toObject(UserStats::class.java)
                        _totalReps.value = stats?.totalReps ?: 0
                        Log.d("AuthViewModel", "Loaded user stats: ${stats?.totalReps} total reps")
                    } else {
                        _totalReps.value = 0
                        Log.d("AuthViewModel", "No user stats found, setting to 0")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AuthViewModel", "Error loading user stats", exception)
                    _totalReps.value = 0
                }
        }
    }

    private fun initializeUserStats() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val initialStats = UserStats()
            firestore.collection("users")
                .document(currentUser.uid)
                .collection("stats")
                .document("totals")
                .set(initialStats)
                .addOnSuccessListener {
                    _totalReps.value = 0
                    Log.d("AuthViewModel", "User stats initialized")
                }
                .addOnFailureListener { exception ->
                    Log.e("AuthViewModel", "Error initializing user stats", exception)
                }
        }
    }

    fun updateTotalReps(newReps: Int, exerciseType: ExerciseType) {
        val currentUser = auth.currentUser
        if (currentUser != null && newReps > 0) {
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
                        totalReps = currentStats.totalReps + newReps,
                        squatReps = currentStats.squatReps + newReps,
                        lastUpdated = System.currentTimeMillis()
                    )
                    ExerciseType.PUSH_UP -> currentStats.copy(
                        totalReps = currentStats.totalReps + newReps,
                        pushUpReps = currentStats.pushUpReps + newReps,
                        lastUpdated = System.currentTimeMillis()
                    )
                    ExerciseType.PULL_UP -> currentStats.copy(
                        totalReps = currentStats.totalReps + newReps,
                        pullUpReps = currentStats.pullUpReps + newReps,
                        lastUpdated = System.currentTimeMillis()
                    )
                }

                transaction.set(userStatsRef, updatedStats)
                updatedStats // Return the updated stats
            }.addOnSuccessListener { updatedStats ->
                _totalReps.value = updatedStats.totalReps
                Log.d("AuthViewModel", "Stats updated successfully: ${updatedStats.totalReps} total reps")
            }.addOnFailureListener { exception ->
                Log.e("AuthViewModel", "Error updating stats", exception)
            }
        }
    }
}

sealed class AuthState{
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object LoadingAuth : AuthState()
    data class Error( val message : String) : AuthState()
}