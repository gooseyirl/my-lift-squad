package com.gooseco.myliftsquad

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gooseco.myliftsquad.ui.SearchAthleteScreen
import com.gooseco.myliftsquad.ui.SettingsScreen
import com.gooseco.myliftsquad.ui.SquadDetailScreen
import com.gooseco.myliftsquad.ui.SquadsScreen
import com.gooseco.myliftsquad.ui.theme.MyLiftSquadTheme
import com.gooseco.myliftsquad.ui.viewmodel.SquadDetailViewModel

class MainActivity : ComponentActivity() {

    private lateinit var billingManager: BillingManager
    private val isDonatedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("myliftsquad_prefs", Context.MODE_PRIVATE)
        val quote = getNextQuote(prefs)

        isDonatedState.value = BillingManager.isDonated(this)
        billingManager = BillingManager(this) {
            isDonatedState.value = true
        }

        setContent {
            val isDonated by isDonatedState

            MyLiftSquadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MyLiftSquadNavGraph(
                        isDonated = isDonated,
                        quote = quote,
                        onSupportDeveloper = { billingManager.launchPurchaseFlow() }
                    )
                }
            }
        }

        billingManager.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.disconnect()
    }
}

@Composable
fun MyLiftSquadNavGraph(
    isDonated: Boolean,
    quote: String,
    onSupportDeveloper: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "squads"
    ) {
        composable("squads") {
            SquadsScreen(
                onSquadClick = { squadId ->
                    navController.navigate("squad/$squadId")
                },
                isDonated = isDonated,
                quote = quote,
                onSupportDeveloper = onSupportDeveloper,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "squad/{squadId}",
            arguments = listOf(navArgument("squadId") { type = NavType.IntType })
        ) { backStackEntry ->
            val squadId = backStackEntry.arguments?.getInt("squadId") ?: return@composable
            // Share the same SquadDetailViewModel instance between detail and search screens
            // by scoping it to the back-stack entry of the detail route.
            val detailViewModel: SquadDetailViewModel = viewModel(backStackEntry)
            SquadDetailScreen(
                squadId = squadId,
                onBack = { navController.popBackStack() },
                onAddAthlete = { navController.navigate("search/$squadId") },
                viewModel = detailViewModel
            )
        }

        composable(
            route = "search/{squadId}",
            arguments = listOf(navArgument("squadId") { type = NavType.IntType })
        ) { backStackEntry ->
            val squadId = backStackEntry.arguments?.getInt("squadId") ?: return@composable
            // Re-use the SquadDetailViewModel scoped to the squad detail back-stack entry,
            // so addAthlete() writes to the correct squad.
            val detailEntry = remember(backStackEntry) {
                navController.getBackStackEntry("squad/$squadId")
            }
            val detailViewModel: SquadDetailViewModel = viewModel(detailEntry)
            SearchAthleteScreen(
                squadId = squadId,
                onBack = { navController.popBackStack() },
                squadDetailViewModel = detailViewModel
            )
        }
    }
}
