package com.example.repmax.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import com.example.repmax.AuthViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.newSingleThreadContext
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.repmax.pages.SingUpPage
import com.example.repmax.AppNavigation

@Composable
fun LoginPage(modifier: Modifier = Modifier, navController: NavController, authViewModel: AuthViewModel) {

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    Column (
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text("RepMax", fontSize = 32.sp )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField( value = email,
            onValueChange = {
                email = it
            },
            label = {
                Text(text = "Email:")
            }
            )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField( value = password,
            onValueChange = {
                password = it
            },
            label = {
                Text(text = "Password:")
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {}) {
            Text( text = "Login")
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = {
            navController.navigate("SingUp")
        }) {
            Text( text = "Don't have an account? Sing up!")
        }

    }
}