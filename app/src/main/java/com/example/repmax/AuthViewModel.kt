package com.example.repmax

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

data class UserStats(
    val totalReps: Int = 0,
    val squatReps: Int = 0,
    val pushUpReps: Int = 0,
    val pullUpReps: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

class AuthViewModel : ViewModel() {

    private val auth : FirebaseAuth = FirebaseAuth.getInstance()

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
        UserStatsRepository.loadUserStats(
            onSuccess = { stats ->
                _totalReps.value = stats.totalReps
                Log.d("AuthViewModel", "Loaded user stats: ${stats.totalReps} total reps")
            },
            onFailure = { exception ->
                Log.e("AuthViewModel", "Error loading user stats", exception)
                _totalReps.value = 0
            }
        )
    }

    private fun initializeUserStats() {
        UserStatsRepository.initializeUserStats(
            onSuccess = {
                _totalReps.value = 0
                Log.d("AuthViewModel", "User stats initialized")
            },
            onFailure = { exception ->
                Log.e("AuthViewModel", "Error initializing user stats", exception)
            }
        )
    }

    fun updateTotalReps(newReps: Int, exerciseType: ExerciseType) {
        UserStatsRepository.updateTotalReps(
            reps = newReps,
            exerciseType = exerciseType,
            onSuccess = { updatedStats ->
                _totalReps.value = updatedStats.totalReps
                Log.d("AuthViewModel", "Stats updated successfully: ${updatedStats.totalReps} total reps")
            },
            onFailure = { exception ->
                Log.e("AuthViewModel", "Error updating stats", exception)
            }
        )
    }
}

sealed class AuthState{
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object LoadingAuth : AuthState()
    data class Error( val message : String) : AuthState()
}