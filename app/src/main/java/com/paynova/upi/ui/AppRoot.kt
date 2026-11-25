package com.paynova.upi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val vm: PayViewModel = viewModel()
    val online = rememberOnline()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Triple("home", "Home", Icons.Default.Home),
        Triple("recharge", "Recharge", Icons.Default.Phone),
        Triple("explore", "Explore", Icons.Default.Search),
        Triple("insurance", "Insurance", Icons.Default.Favorite),
        Triple("history", "History", Icons.Default.DateRange)
    )
    Scaffold(bottomBar = {
        if (tabs.any { it.first == route }) {
            NavigationBar {
                tabs.forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = {
                            nav.navigate(r) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (!online) {
                Box(Modifier.fillMaxWidth().background(Color(0xFF7A1F1F)).padding(6.dp)) {
                    Text("You're offline. Showing cached data; payments need internet.", color = Color.White, fontSize = 12.sp)
                }
            }
            NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") { HomeScreen(nav, vm) }
                composable("recharge") { RechargeScreen(nav) }
                composable("explore") { ExploreScreen(nav) }
                composable("insurance") { InsuranceScreen(nav) }
                composable("history") { HistoryScreen(nav) }
                composable("scan") { ScanScreen(nav, vm) }
                composable("contacts") { ContactsScreen(nav, vm) }
                composable("payupi") { PayUpiScreen(nav, vm) }
                composable("paybank") { PayBankScreen(nav, vm) }
                composable("balance") { BalanceScreen(nav) }
                composable("resolve") { ResolveScreen(nav, vm) }
                composable("amount") { AmountScreen(nav, vm) }
                composable("confirm") { ConfirmScreen(nav, vm) }
                composable("pin") { DemoPinScreen(nav, vm) }
                composable("receipt/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    ReceiptScreen(nav, vm, it.arguments?.getString("id").orEmpty())
                }
                composable("myqr") { MyQrScreen(nav, "My QR & Payment Link") }
                composable("request") { MyQrScreen(nav, "Request Money") }
                composable("split") { SplitScreen(nav) }
                composable("analytics") { AnalyticsScreen(nav) }
                composable("emi") { EmiScreen(nav) }
                composable("rewards") { RewardsScreen(nav) }
                composable("offers") { OffersScreen(nav) }
                composable("notifications") { NotificationsScreen(nav) }
                composable("service/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) {
                    ServiceScreen(nav, it.arguments?.getString("key").orEmpty())
                }
                composable("bill/{cat}", arguments = listOf(navArgument("cat") { type = NavType.StringType })) {
                    BillScreen(nav, vm, it.arguments?.getString("cat").orEmpty())
                }
                composable("settings") { SettingsScreen(nav) }
                composable("pinsetup") { PinSetupScreen(nav, false) }
                composable("changepin") { PinSetupScreen(nav, true) }
                composable("support") { SupportScreen(nav) }
            }
        }
    }
}
