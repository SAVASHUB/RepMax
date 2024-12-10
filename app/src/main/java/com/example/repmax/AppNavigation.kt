package com.example.repmax

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.repmax.pages.HomePage
import com.example.repmax.pages.LoginPage
import com.example.repmax.pages.SingUpPage

@Composable
fun AppNavigation(modifier: Modifier = Modifier, authViewModel: AuthViewModel) {

    val navController =  rememberNavController()

    NavHost(navController =  navController, startDestination = "Login", builder = {
        composable("Login"){
            LoginPage(modifier,navController,authViewModel)
        }
        composable("SingUp"){
            SingUpPage(modifier,navController,authViewModel)
        }
        composable("Home"){
            HomePage(modifier,navController,authViewModel)
        }
    })

}


