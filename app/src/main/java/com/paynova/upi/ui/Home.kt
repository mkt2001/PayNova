package com.paynova.upi.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.UpiParser
import kotlinx.coroutines.delay

private data class Banner(val title: String, val sub: String, val route: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: NavHostController, vm: PayViewModel) {
    val ctx = LocalContext.current
    val s = Graph.settings
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    val coins = remember(txs) { Rewards.coins(txs) }
    val unread = txs.count { it.createdAt > s.lastSeenNotif }
    val banners = remember {
        listOf(
            Banner("Personal loan offers", "Check eligibility with lending partners", "service/loans"),
            Banner("EMI calculator", "Plan your loan EMI in seconds", "emi"),
            Banner("Special rewards", "Earn coins on every payment", "rewards"),
            Banner("Insurance", "Bike, car & health cover", "insurance")
        )
    }
    val pager = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(pager) {
        while (true) {
            delay(4000)
            pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
        }
    }

    fun withOwnVpa(action: (String) -> Unit) {
        if (s.ownVpa.isBlank()) {
            toast(ctx, "Set your UPI ID first")
            nav.navigate("myqr")
        } else action(s.ownVpa)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable { nav.navigate("settings") }) {
                Avatar(s.displayName.ifBlank { "U" })
            }
            Spacer(Modifier.width(8.dp))
            Pill("UPI profile") { nav.navigate("myqr") }
            Spacer(Modifier.weight(1f))
            Pill("🪙 $coins") { nav.navigate("rewards") }
            IconButton(onClick = { nav.navigate("notifications") }) {
                BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("🏷 Check Offers") { nav.navigate("offers") }
            Pill("⚙ Settings") { nav.navigate("settings") }
        }
        if (Graph.isDemo) DemoBanner()

        HorizontalPager(pager, pageSpacing = 12.dp) { i ->
            val b = banners[i]
            Box(
                Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFF7EEFC), Color(0xFFDDB8F2))))
                    .clickable { nav.navigate(b.route) }.padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Text(b.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF26002F))
                    Text(b.sub, fontSize = 13.sp, color = Color(0xFF4A2A57))
                }
            }
        }

        Button(
            onClick = { nav.navigate("scan") },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("📷  Scan & Pay", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), { nav.navigate("contacts") }) { Text("📱", fontSize = 24.sp); Text("Pay Mobile Number", fontSize = 13.sp) }
            Tile(Modifier.weight(1f), { nav.navigate("payupi") }) { Text("@", fontSize = 24.sp); Text("Pay to UPI ID", fontSize = 13.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), { nav.navigate("paybank") }) { Text("🏦", fontSize = 24.sp); Text("Pay to Bank Account", fontSize = 13.sp) }
            Tile(Modifier.weight(1f), { nav.navigate("balance") }) { Text("💰", fontSize = 24.sp); Text("Check Bank Balance", fontSize = 13.sp) }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("My QR") { nav.navigate("myqr") }
            Pill("Copy UPI ID") { withOwnVpa { copyText(ctx, "UPI ID", it) } }
            Pill("Share Payment Link") { withOwnVpa { shareText(ctx, UpiParser.buildLink(it, s.displayName, null, null)) } }
            if (Graph.provider.supportsUpiLite) Pill("UPI Lite") { nav.navigate("service/upilite") }
            Pill("Request Money") { nav.navigate("request") }
            Pill("Split Bills") { nav.navigate("split") }
            Pill("Spending") { nav.navigate("analytics") }
        }

        SectionTitle("Next for you")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(onClick = { nav.navigate("insurance") }) { Text("🛡 Insurance", fontWeight = FontWeight.Medium) }
            Tile(onClick = { nav.navigate("recharge") }) { Text("📲 Recharge offers", fontWeight = FontWeight.Medium) }
            Tile(onClick = { nav.navigate("rewards") }) { Text("🎁 Rewards", fontWeight = FontWeight.Medium) }
            Tile(onClick = { nav.navigate("explore") }) { Text("📈 Financial products", fontWeight = FontWeight.Medium) }
        }

        SectionTitle("Recent transactions")
        if (txs.isEmpty()) Text("No transactions yet. Make your first payment with Scan & Pay.", fontSize = 13.sp)
        txs.take(3).forEach { t -> TxRow(t) { nav.navigate("receipt/${t.id}") } }
    }
}
