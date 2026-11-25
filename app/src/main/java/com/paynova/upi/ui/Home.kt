package com.paynova.upi.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.payment.UpiParser
import kotlinx.coroutines.delay

private val Blue1 = Color(0xFF2D6BF0)
private val Blue2 = Color(0xFF5EC8FF)
private val Orange1 = Color(0xFFFF8A00)
private val Orange2 = Color(0xFFFFC04D)
private val Pink1 = Color(0xFFE91E8C)
private val Pink2 = Color(0xFFFF7BB5)
private val Green1 = Color(0xFF079B55)
private val Green2 = Color(0xFF5FD39A)
private val Violet1 = Color(0xFF7B2CBF)
private val Violet2 = Color(0xFFC77DFF)
private val Teal1 = Color(0xFF00A99D)
private val Teal2 = Color(0xFF5EE0D3)
private val Red1 = Color(0xFFE53935)
private val Red2 = Color(0xFFFF8A80)

private data class Banner(val title: String, val sub: String, val emoji: String, val route: String, val c1: Color, val c2: Color)

@Composable
private fun ActionCircle(emoji: String, label: String, c1: Color, c2: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c1, c2))),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 26.sp) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SoftCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().shadow(4.dp, shape).clip(shape)
            .background(MaterialTheme.colorScheme.surface).padding(12.dp)
    ) { content() }
}

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
            Banner("Personal loan offers", "Check eligibility with lending partners", "💸", "service/loans", Violet1, Violet2),
            Banner("EMI calculator", "Plan your loan EMI in seconds", "🧮", "emi", Blue1, Blue2),
            Banner("Special rewards", "Earn coins on every payment", "🎁", "rewards", Orange1, Pink1),
            Banner("Insurance", "Bike, car & health cover", "🛡", "insurance", Green1, Green2)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // ---------- Colourful gradient header ----------
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF26002F), Color(0xFF5B1A86), Color(0xFF8C35C9))),
                    RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
                )
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { nav.navigate("settings") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            s.displayName.trim().firstOrNull()?.uppercase() ?: "U",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.clickable { nav.navigate("myqr") }) {
                        Text(
                            if (s.displayName.isBlank()) "Welcome" else "Hi, ${s.displayName}",
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                        Text("UPI profile  ›", color = Color(0xFFE9D3F7), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))
                            .clickable { nav.navigate("rewards") }.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("🪙 $coins", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    IconButton(onClick = { nav.navigate("notifications") }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                        }
                    }
                }
                // Big Scan & Pay card
                Row(
                    Modifier.fillMaxWidth().padding(end = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .clickable { nav.navigate("scan") }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Violet1, Violet2))),
                        contentAlignment = Alignment.Center
                    ) { Text("📷", fontSize = 22.sp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Scan & Pay", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF26002F))
                        Text("Scan any UPI QR code", fontSize = 12.sp, color = Color(0xFF6B5A75))
                    }
                    Text("›", fontSize = 26.sp, color = Violet1, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (Graph.isDemo) DemoBanner()

            // ---------- Pay ----------
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pay", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Row {
                        ActionCircle("📱", "Pay Mobile Number", Blue1, Blue2, Modifier.weight(1f)) { nav.navigate("contacts") }
                        ActionCircle("@", "Pay to UPI ID", Violet1, Violet2, Modifier.weight(1f)) { nav.navigate("payupi") }
                        ActionCircle("🏦", "Pay to Bank Account", Green1, Green2, Modifier.weight(1f)) { nav.navigate("paybank") }
                        ActionCircle("💰", "Check Bank Balance", Orange1, Orange2, Modifier.weight(1f)) { nav.navigate("balance") }
                    }
                }
            }

            // ---------- More shortcuts ----------
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("More", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    Row {
                        ActionCircle("🔳", "My QR", Violet1, Pink2, Modifier.weight(1f)) { nav.navigate("myqr") }
                        ActionCircle("📋", "Copy UPI ID", Teal1, Teal2, Modifier.weight(1f)) { withOwnVpa { copyText(ctx, "UPI ID", it) } }
                        ActionCircle("🔗", "Share Payment Link", Blue1, Violet2, Modifier.weight(1f)) {
                            withOwnVpa { shareText(ctx, UpiParser.buildLink(it, s.displayName, null, null)) }
                        }
                        ActionCircle("🙏", "Request Money", Pink1, Pink2, Modifier.weight(1f)) { nav.navigate("request") }
                    }
                    Row {
                        ActionCircle("🧾", "Split Bills", Orange1, Pink1, Modifier.weight(1f)) { nav.navigate("split") }
                        ActionCircle("📊", "Spending", Green1, Teal2, Modifier.weight(1f)) { nav.navigate("analytics") }
                        ActionCircle("🏷", "Offers", Red1, Red2, Modifier.weight(1f)) { nav.navigate("offers") }
                        if (Graph.provider.supportsUpiLite) {
                            ActionCircle("⚡", "UPI Lite", Blue1, Blue2, Modifier.weight(1f)) { nav.navigate("service/upilite") }
                        } else {
                            ActionCircle("⚙", "Settings", Color(0xFF546E7A), Color(0xFF90A4AE), Modifier.weight(1f)) { nav.navigate("settings") }
                        }
                    }
                }
            }

            // ---------- Colourful promo carousel ----------
            HorizontalPager(pager, pageSpacing = 12.dp) { i ->
                val b = banners[i]
                Box(
                    Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(b.c1, b.c2)))
                        .clickable { nav.navigate(b.route) }.padding(18.dp)
                ) {
                    Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.72f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(b.sub, fontSize = 13.sp, color = Color(0xFFFFFFE6))
                    }
                    Text(b.emoji, fontSize = 54.sp, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                banners.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 18.dp else 7.dp, 7.dp)
                            .clip(CircleShape).background(if (pager.currentPage == i) Violet1 else Color(0xFFD9C7E8))
                    )
                }
            }

            // ---------- Next for you ----------
            Text("Next for you", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    Triple("🛡 Insurance", Color(0xFFE3F6EC), "insurance"),
                    Triple("📲 Recharge offers", Color(0xFFE4EEFF), "recharge"),
                    Triple("🎁 Rewards", Color(0xFFFFEFD9), "rewards"),
                    Triple("📈 Financial products", Color(0xFFFFE4F0), "explore")
                ).forEach { (label, bg, route) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(bg)
                            .clickable { nav.navigate(route) }.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) { Text(label, fontWeight = FontWeight.SemiBold, color = Color(0xFF171717)) }
                }
            }

            // ---------- Recent ----------
            Text("Recent transactions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (txs.isEmpty()) Text("No transactions yet. Make your first payment with Scan & Pay.", fontSize = 13.sp)
            txs.take(3).forEach { t -> TxRow(t) { nav.navigate("receipt/${t.id}") } }
        }
    }
}
