package com.example.repmax

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

class AuthViewModel : ViewModel() {

    private val auth : FirebaseAuth = FirebaseAuth.getInstance()

    private  val _authState = MutableLiveData<AuthState>()
    val authState : LiveData<AuthState> = _authState

    init {
        checkAuthState()
    }

    fun checkAuthState () {
        if(auth.currentUser == null) {
            _authState.value = AuthState.Unauthenticated
        }else {
            _authState.value = AuthState.Authenticated
        }
    }
}

sealed class AuthState{
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
    object LoadingAuth : AuthState()
    data class Error( val message : String) : AuthState()
}