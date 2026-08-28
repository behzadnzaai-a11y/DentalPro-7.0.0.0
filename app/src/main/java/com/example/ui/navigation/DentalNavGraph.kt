package com.example.ui.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.ui.DentalViewModel
import com.example.feature.auth.UserRole
import com.example.ui.screens.AccessDeniedScreen
import com.example.ui.screens.AppointmentsScreen
import com.example.ui.screens.BankAccountsManagerScreen
import com.example.ui.screens.ChequesManagerScreen
import com.example.ui.screens.ClinicSettingsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExpensesScreen
import com.example.ui.screens.LabsScreen
import com.example.ui.screens.MaintenanceScreen
import com.example.ui.screens.MoreModulesGridScreen
import com.example.ui.screens.PatientsScreen
import com.example.ui.screens.PersonalScreen
import com.example.ui.screens.ReportsCenterScreen
import com.example.ui.screens.StaffManagementScreen
import com.example.ui.screens.TemplatesManagerScreen
import com.example.ui.screens.VendorsScreen

sealed class DentalRoute(val route: String) {
    object Dashboard : DentalRoute("dashboard")
    object Patients : DentalRoute("patients")
    object PatientDetail : DentalRoute("patient/{patientId}") {
        fun createRoute(patientId: Long) = "patient/$patientId"
    }
    object Appointments : DentalRoute("appointments")
    object More : DentalRoute("more")
    object Vendors : DentalRoute("vendors")
    object Labs : DentalRoute("labs")
    object Maintenance : DentalRoute("maintenance")
    object Expenses : DentalRoute("expenses")
    object Personal : DentalRoute("personal")
        object DrugTemplates : DentalRoute("drug_templates")
    object Templates : DentalRoute("templates")
    object Bank : DentalRoute("bank")
    object Cheques : DentalRoute("cheques")
    object Reports : DentalRoute("reports")
    object Settings : DentalRoute("settings")
    object ExclusiveSettings : DentalRoute("exclusive_settings")
    object StaffManagement : DentalRoute("staff_management")
    object Account : DentalRoute("account")
    object Onboarding : DentalRoute("onboarding")
}

private const val URI_SCHEME = "dental://app"
private const val WEB_SCHEME = "https://dentalpro.app"

@Composable
fun DentalNavGraph(
    navController: NavHostController,
    viewModel: DentalViewModel,
    modifier: Modifier = Modifier
) {
    val isPersian by viewModel.isPersian.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val selectedPatientId by viewModel.selectedPatientId.collectAsStateWithLifecycle()
    val userRole by viewModel.currentUserRole.collectAsStateWithLifecycle()

    // Sync ViewModel's currentTab changes to NavController
    LaunchedEffect(currentTab) {
        val currentDestination = navController.currentBackStackEntry?.destination?.route
        if (currentDestination != currentTab) {
            when (currentTab) {
                "dashboard" -> navController.navigate(DentalRoute.Dashboard.route) { launchSingleTop = true }
                "patients" -> {
                    if (selectedPatientId == null) {
                        navController.navigate(DentalRoute.Patients.route) { launchSingleTop = true }
                    }
                }
                "appointments" -> navController.navigate(DentalRoute.Appointments.route) { launchSingleTop = true }
                "more" -> navController.navigate(DentalRoute.More.route) { launchSingleTop = true }
                "vendors" -> navController.navigate(DentalRoute.Vendors.route) { launchSingleTop = true }
                "labs" -> navController.navigate(DentalRoute.Labs.route) { launchSingleTop = true }
                "maintenance" -> navController.navigate(DentalRoute.Maintenance.route) { launchSingleTop = true }
                "expenses" -> navController.navigate(DentalRoute.Expenses.route) { launchSingleTop = true }
                "personal" -> navController.navigate(DentalRoute.Personal.route) { launchSingleTop = true }
                                "drug_templates" -> navController.navigate(DentalRoute.DrugTemplates.route) { launchSingleTop = true }
                "templates" -> navController.navigate(DentalRoute.Templates.route) { launchSingleTop = true }
                "bank" -> navController.navigate(DentalRoute.Bank.route) { launchSingleTop = true }
                "cheques" -> navController.navigate(DentalRoute.Cheques.route) { launchSingleTop = true }
                "reports" -> navController.navigate(DentalRoute.Reports.route) { launchSingleTop = true }
                "staff_management" -> navController.navigate(DentalRoute.StaffManagement.route) { launchSingleTop = true }
                "settings" -> navController.navigate(DentalRoute.Settings.route) { launchSingleTop = true }
                "exclusive_settings" -> navController.navigate(DentalRoute.ExclusiveSettings.route) { launchSingleTop = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = DentalRoute.Dashboard.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        composable(
            route = DentalRoute.Dashboard.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/dashboard" },
                navDeepLink { uriPattern = "$WEB_SCHEME/dashboard" }
            )
        ) {
            viewModel.currentTab.value = "dashboard"
            viewModel.selectedPatientId.value = null
            
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            if (!settings.onboardingCompleted) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.navigate(DentalRoute.Onboarding.route) {
                        popUpTo(DentalRoute.Dashboard.route) { inclusive = true }
                    }
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Patients.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/patients" },
                navDeepLink { uriPattern = "$WEB_SCHEME/patients" }
            )
        ) {
            viewModel.currentTab.value = "patients"
            if (viewModel.hasModuleAccess("patients")) {
                PatientsScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.PatientDetail.route,
            arguments = listOf(navArgument("patientId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/patient/{patientId}" },
                navDeepLink { uriPattern = "$WEB_SCHEME/patient/{patientId}" }
            )
        ) { backStackEntry ->
            val pId = backStackEntry.arguments?.getLong("patientId") ?: -1L
            if (pId > 0) {
                viewModel.selectedPatientId.value = pId
            }
            if (viewModel.hasModuleAccess("patients")) {
                PatientsScreen(viewModel = viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Appointments.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/appointments" },
                navDeepLink { uriPattern = "$WEB_SCHEME/appointments" }
            )
        ) {
            viewModel.currentTab.value = "appointments"
            if (viewModel.hasModuleAccess("appointments")) {
                AppointmentsScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.More.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/more" },
                navDeepLink { uriPattern = "$WEB_SCHEME/more" }
            )
        ) {
            viewModel.currentTab.value = "more"
            MoreModulesGridScreen(viewModel)
        }

        composable(
            route = DentalRoute.Vendors.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/vendors" },
                navDeepLink { uriPattern = "$WEB_SCHEME/vendors" }
            )
        ) {
            viewModel.currentTab.value = "vendors"
            if (viewModel.hasModuleAccess("vendors")) {
                VendorsScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Labs.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/labs" },
                navDeepLink { uriPattern = "$WEB_SCHEME/labs" }
            )
        ) {
            viewModel.currentTab.value = "labs"
            if (viewModel.hasModuleAccess("labs")) {
                LabsScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Maintenance.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/maintenance" },
                navDeepLink { uriPattern = "$WEB_SCHEME/maintenance" }
            )
        ) {
            viewModel.currentTab.value = "maintenance"
            if (viewModel.hasModuleAccess("maintenance")) {
                MaintenanceScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Expenses.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/expenses" },
                navDeepLink { uriPattern = "$WEB_SCHEME/expenses" }
            )
        ) {
            viewModel.currentTab.value = "expenses"
            if (viewModel.hasModuleAccess("expenses")) {
                ExpensesScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Personal.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/personal" },
                navDeepLink { uriPattern = "$WEB_SCHEME/personal" }
            )
        ) {
            viewModel.currentTab.value = "personal"
            if (viewModel.hasModuleAccess("personal")) {
                PersonalScreen(viewModel)
            } else {
                DashboardScreen(viewModel)
            }
        }

        
        composable(
            route = DentalRoute.DrugTemplates.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/drug_templates" },
                navDeepLink { uriPattern = "$WEB_SCHEME/drug_templates" }
            )
        ) {
            viewModel.currentTab.value = "drug_templates"
            com.example.ui.screens.DrugTemplatesEditorScreen(viewModel = viewModel)
        }

        composable(
            route = DentalRoute.Templates.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/templates" },
                navDeepLink { uriPattern = "$WEB_SCHEME/templates" }
            )
        ) {
            viewModel.currentTab.value = "templates"
            if (viewModel.hasModuleAccess("settings")) {
                TemplatesManagerScreen(viewModel, isPersian) {
                    navController.popBackStack()
                    viewModel.currentTab.value = "more"
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Bank.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/bank" },
                navDeepLink { uriPattern = "$WEB_SCHEME/bank" }
            )
        ) {
            viewModel.currentTab.value = "bank"
            if (viewModel.hasModuleAccess("bank")) {
                BankAccountsManagerScreen(viewModel, isPersian) {
                    navController.popBackStack()
                    viewModel.currentTab.value = "more"
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Cheques.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/cheques" },
                navDeepLink { uriPattern = "$WEB_SCHEME/cheques" }
            )
        ) {
            viewModel.currentTab.value = "cheques"
            if (viewModel.hasModuleAccess("cheques")) {
                ChequesManagerScreen(viewModel, isPersian) {
                    navController.popBackStack()
                    viewModel.currentTab.value = "more"
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Reports.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/reports" },
                navDeepLink { uriPattern = "$WEB_SCHEME/reports" }
            )
        ) {
            viewModel.currentTab.value = "reports"
            if (viewModel.hasModuleAccess("reports")) {
                ReportsCenterScreen(viewModel, isPersian) {
                    navController.popBackStack()
                    viewModel.currentTab.value = "more"
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.Settings.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/settings" },
                navDeepLink { uriPattern = "$WEB_SCHEME/settings" }
            )
        ) {
            viewModel.currentTab.value = "settings"
            if (viewModel.hasModuleAccess("settings")) {
                ClinicSettingsScreen(
                    viewModel = viewModel,
                    isPersian = isPersian,
                    onNavigateToAccount = { navController.navigate(DentalRoute.Account.route) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.ExclusiveSettings.route,
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            viewModel.currentTab.value = "exclusive_settings"
            if (viewModel.hasModuleAccess("settings")) {
                com.example.ui.screens.ExclusiveSettingsScreen(
                    viewModel = viewModel,
                    isPersian = isPersian,
                    onBack = { navController.popBackStack() }
                )
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(
            route = DentalRoute.StaffManagement.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "$URI_SCHEME/staff_management" },
                navDeepLink { uriPattern = "$WEB_SCHEME/staff_management" }
            )
        ) {
            viewModel.currentTab.value = "staff_management"
            if (viewModel.hasModuleAccess("settings")) {
                StaffManagementScreen(viewModel, isPersian) {
                    navController.popBackStack()
                }
            } else {
                DashboardScreen(viewModel)
            }
        }

        composable(route = DentalRoute.Account.route) {
            com.example.ui.screens.AccountScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLogout = { navController.navigate(DentalRoute.Dashboard.route) { popUpTo(0) } }
            )
        }

        composable(route = DentalRoute.Onboarding.route) {
            com.example.ui.screens.OnboardingScreen(
                viewModel = viewModel,
                onComplete = { navController.navigate(DentalRoute.Dashboard.route) { popUpTo(0) } }
            )
        }
    }
}
