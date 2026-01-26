package com.xinya.dtx.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xinya.dtx.feature.agent.ui.AgentScreen
import com.xinya.dtx.feature.education.ui.EducationScreen
import com.xinya.dtx.feature.home.ui.HomeScreen
import com.xinya.dtx.feature.hopetree.ui.HopeTreeScreen
import com.xinya.dtx.feature.meditation.ui.MeditationScreen
import com.xinya.dtx.feature.pro.ui.ProScreen

/**
 * 心芽DTx导航路由
 */
object XinyaRoutes {
    const val HOME = "home"
    const val AGENT = "agent/{agentType}"
    const val HOPE_TREE = "hope_tree"
    const val EDUCATION = "education"
    const val PRO = "pro"
    const val MEDITATION = "meditation"
    
    fun agentRoute(agentType: String) = "agent/$agentType"
}

@Composable
fun XinyaNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = XinyaRoutes.HOME
    ) {
        composable(XinyaRoutes.HOME) {
            HomeScreen(
                onNavigateToAgent = { agentType ->
                    navController.navigate(XinyaRoutes.agentRoute(agentType))
                },
                onNavigateToHopeTree = {
                    navController.navigate(XinyaRoutes.HOPE_TREE)
                },
                onNavigateToEducation = {
                    navController.navigate(XinyaRoutes.EDUCATION)
                },
                onNavigateToPro = {
                    navController.navigate(XinyaRoutes.PRO)
                },
                onNavigateToMeditation = {
                    navController.navigate(XinyaRoutes.MEDITATION)
                }
            )
        }
        
        composable(XinyaRoutes.AGENT) { backStackEntry ->
            val agentType = backStackEntry.arguments?.getString("agentType") ?: "psych"
            AgentScreen(
                agentType = agentType,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(XinyaRoutes.HOPE_TREE) {
            HopeTreeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(XinyaRoutes.EDUCATION) {
            EducationScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(XinyaRoutes.PRO) {
            ProScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(XinyaRoutes.MEDITATION) {
            MeditationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
