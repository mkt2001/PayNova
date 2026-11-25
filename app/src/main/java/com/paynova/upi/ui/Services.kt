package com.paynova.upi.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.paynova.upi.Graph
import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.title
import com.paynova.upi.payment.UpiParser
import com.paynova.upi.util.Money
import java.util.Calendar
import kotlin.math.pow

object Rewards {
    /** Demo/local coins: 1 per successful payment + 1 per ₹100. Real rewards come from the backend (GET /api/rewards). */
    fun coins(txs: List<TxEntity>): Int =
        txs.filter { it.status == "SUCCESS" && it.direction == "SENT" }.sumOf { (it.amountPaise / 10_000).toInt() + 1 }
}

private val SERVICES = listOf(
    Triple("bike", "Bike Insurance", "🏍"), Triple("car", "Car Insurance", "🚗"), Triple("health", "Health Insurance", "🩺"),
    Triple("mf", "Mutual Funds", "📊"), Triple("gold", "Digital Gold", "🪙"), Triple("score", "Credit Score", "📈"),
    Triple("finance", "Personal Finance", "💼"), Triple("loans", "Loan Offers", "🏦"),
    Triple("rewards", "Rewards", "🎁"), Triple("offers", "Offer Store", "🏷")
)

private fun routeFor(key: String) = when (key) {
    "rewards" -> "rewards"
    "offers" -> "offers"
    "finance" -> "analytics"
    else -> "service/$key"
}

@Composable
fun ExploreScreen(nav: NavHostController) {
    Screen("Explore", null) {
        SERVICES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, label, emoji) ->
                    Tile(Modifier.weight(1f), { nav.navigate(routeFor(k)) }) {
                        Text(emoji, fontSize = 26.sp); Text(label, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun InsuranceScreen(nav: NavHostController) {
    Screen("Insurance", null) {
        listOf("bike", "car", "health").forEach { k ->
            val (_, label, emoji) = SERVICES.first { it.first == k }
            Tile(Modifier.fillMaxWidth(), { nav.navigate("service/$k") }) { Text("$emoji  $label", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
        Notice("Insurance is sold only through IRDAI-registered partners. No partner is connected to this build, so no quotes or premiums are shown.")
    }
}

@Composable
fun ServiceScreen(nav: NavHostController, key: String) {
    val item = SERVICES.firstOrNull { it.first == key }
    val title = item?.second ?: if (key == "upilite") "UPI Lite" else "Service"
    val need = when (key) {
        "bike", "car", "health" -> "an IRDAI-registered insurer or aggregator"
        "mf" -> "a SEBI-registered mutual fund distributor / RTA"
        "gold" -> "a licensed digital-gold partner"
        "score" -> "a credit bureau (CIBIL / Experian / Equifax / CRIF)"
        "loans" -> "RBI-regulated lending partners"
        "upilite" -> "a PSP that supports UPI Lite"
        else -> "an authorized partner"
    }
    Screen(title, { nav.popBackStack() }) {
        Notice("$title needs $need. No partner is connected to this build, so nothing is shown here — no scores, approvals, quotes or returns are invented.")
        if (key == "loans") BigButton("Open EMI calculator") { nav.navigate("emi") }
    }
}

@Composable
fun EmiScreen(nav: NavHostController) {
    var p by remember { mutableStateOf("500000") }
    var r by remember { mutableStateOf("10.5") }
    var n by remember { mutableStateOf("36") }
    val P = p.toDoubleOrNull(); val R = r.toDoubleOrNull(); val N = n.toIntOrNull()
    Screen("EMI calculator", { nav.popBackStack() }) {
        OutlinedTextField(p, { p = it }, label = { Text("Loan amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(r, { r = it }, label = { Text("Interest rate (% per year)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(n, { n = it }, label = { Text("Tenure (months)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (P != null && R != null && N != null && P > 0 && N > 0 && R >= 0) {
            val mr = R / 12 / 100
            val emi = if (mr == 0.0) P / N else P * mr * (1 + mr).pow(N) / ((1 + mr).pow(N) - 1)
            val total = emi * N
            Tile(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monthly EMI", fontSize = 13.sp)
                    Text(rupees(Math.round(emi * 100)), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    InfoRow("Total interest", rupees(Math.round((total - P) * 100)))
                    InfoRow("Total payable", rupees(Math.round(total * 100)))
                }
            }
            Text("Estimate only. Actual EMI depends on the lender's terms and fees.", fontSize = 12.sp)
        }
    }
}

@Composable
fun AnalyticsScreen(nav: NavHostController) {
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    val ctx = LocalContext.current
    var range by remember { mutableStateOf("This month") }
    val cal = Calendar.getInstance()
    val thisM = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
    val list = txs.filter {
        if (it.status != "SUCCESS" || it.direction != "SENT") false else {
            val c = Calendar.getInstance().apply { timeInMillis = it.createdAt }
            val m = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
            when (range) { "This month" -> m == thisM; "Last month" -> m == thisM - 1; else -> true }
        }
    }
    val total = list.sumOf { it.amountPaise }
    val byCat = list.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountPaise } }.entries.sortedByDescending { it.value }
    Screen("Spending analytics", { nav.popBackStack() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("This month", "Last month", "All time").forEach { Pill(it, range == it) { range = it } }
        }
        Tile(Modifier.fillMaxWidth()) { Text("Total spent", fontSize = 13.sp); Text(rupees(total), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        if (byCat.isEmpty()) Text("No successful payments in this period.", fontSize = 14.sp)
        byCat.forEach { (cat, amt) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(cat); Text(rupees(amt)) }
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((amt.toFloat() / total.toFloat()).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Violet))
                }
            }
        }
        if (byCat.isNotEmpty()) BigButton("Share $range report") {
            shareText(ctx, "PayNova spending report ($range)\nTotal: ${rupees(total)}\n" + byCat.joinToString("\n") { "${it.key}: ${rupees(it.value)}" })
        }
    }
}

@Composable
fun RewardsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    val code = remember { Graph.settings.referralCode() }
    Screen("Rewards & referrals", { nav.popBackStack() }) {
        Tile(Modifier.fillMaxWidth()) {
            Text("Reward coins", fontSize = 13.sp)
            Text(if (Graph.isDemo) "${Rewards.coins(txs)} (demo)" else "Not available", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Notice(if (Graph.isDemo) "Demo coins are counted on this phone only. Real reward balances come from the backend rewards ledger."
        else "Reward balance is provided by the backend (GET /api/rewards).")
        Tile(Modifier.fillMaxWidth()) { Text("Your referral code", fontSize = 13.sp); Text(code, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        BigButton("Share referral code") { shareText(ctx, "Join me on PayNova! Use my referral code $code.") }
    }
}

@Composable
fun OffersScreen(nav: NavHostController) {
    Screen("Offers", { nav.popBackStack() }) {
        Notice("No offers are loaded. Offers are created in the admin dashboard and delivered by the backend (GET /api/offers); nothing is hardcoded here.")
    }
}

@Composable
fun NotificationsScreen(nav: NavHostController) {
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    DisposableEffect(Unit) { onDispose { Graph.settings.updateLastSeen(System.currentTimeMillis()) } }
    Screen("Notifications", { nav.popBackStack() }) {
        if (txs.isEmpty()) Text("No notifications yet.")
        txs.take(30).forEach { t ->
            Tile(Modifier.fillMaxWidth(), { nav.navigate("receipt/${t.id}") }) {
                Text(
                    when (t.status) { "SUCCESS" -> "Payment successful"; "PENDING" -> "Payment pending"; "FAILED" -> "Payment failed"; else -> "Payment reversed" },
                    fontWeight = FontWeight.SemiBold, color = statusColor(t.status)
                )
                Text("${rupees(t.amountPaise)} to ${t.title()}", fontSize = 13.sp)
                Text(fmtDateTime(t.createdAt), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun SplitScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var total by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("2") }
    val paise = Money.parsePaise(total)
    val n = people.toIntOrNull()
    Screen("Split bills", { nav.popBackStack() }) {
        OutlinedTextField(total, { total = it }, label = { Text("Total bill (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(people, { people = it }, label = { Text("Number of people (including you)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        if (paise != null && n != null && n in 2..50) {
            val each = (paise + n - 1) / n
            Tile(Modifier.fillMaxWidth()) { Text("Each person pays", fontSize = 13.sp); Text(rupees(each), fontSize = 30.sp, fontWeight = FontWeight.Bold) }
            val own = Graph.settings.ownVpa
            BigButton("Share payment request") {
                val link = if (own.isBlank()) "" else "\n" + UpiParser.buildLink(own, Graph.settings.displayName, Money.plain(each), "Split bill")
                shareText(ctx, "Please pay ${rupees(each)} for our split bill.$link")
            }
        }
    }
}

@Composable
fun MyQrScreen(nav: NavHostController, title: String) {
    val ctx = LocalContext.current
    val s = Graph.settings
    var vpa by remember { mutableStateOf(s.ownVpa) }
    var name by remember { mutableStateOf(s.displayName) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val ok = UpiParser.isValidVpa(vpa)
    val paise = if (amount.isBlank()) null else Money.parsePaise(amount)
    val link = if (ok) UpiParser.buildLink(vpa, name, paise?.let { Money.plain(it) }, note) else ""
    val bmp = remember(link) { if (link.isEmpty()) null else qrBitmap(link) }
    Screen(title, { nav.popBackStack() }) {
        OutlinedTextField(name, { name = it; s.updateName(it) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            vpa, { vpa = it.trim(); if (UpiParser.isValidVpa(vpa)) s.updateOwnVpa(vpa) },
            label = { Text("Your UPI ID") }, singleLine = true, isError = vpa.isNotEmpty() && !ok, modifier = Modifier.fillMaxWidth()
        )
        Text("Your UPI ID is created by your bank / UPI app. Enter the one you already have; PayNova cannot create one in demo mode.", fontSize = 12.sp)
        OutlinedTextField(amount, { amount = it }, label = { Text("Request amount (optional ₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { if (it.length <= 50) note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (bmp != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), "QR code", Modifier.size(240.dp).background(Color.White).padding(8.dp))
            }
            BigButton("Share payment link") { shareText(ctx, link) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Copy UPI ID") { copyText(ctx, "UPI ID", vpa) }
                Pill("Copy link") { copyText(ctx, "Payment link", link) }
            }
        }
    }
}

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bmp.setPixel(x, y, if (m.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}

private val FAQ = listOf(
    "Is my UPI PIN safe?" to "PayNova never asks for, sees, logs or stores your UPI PIN. It is entered only in your bank's secure UPI screen. The demo PIN screen is clearly labelled DEMO.",
    "Why is my payment pending?" to "Banks sometimes take a few minutes. The app re-checks the status with the provider automatically and updates the receipt.",
    "Was I charged twice?" to "Each payment uses a unique idempotency key, so retries and double taps cannot create a second payment.",
    "What does DEMO mean?" to "Demo mode uses a simulator with fake money. Production stays disabled until a provider is configured.",
    "Where is my data?" to "Contacts stay on your phone. Transaction history is cached locally for offline viewing."
)

@Composable
fun SupportScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(-1) }
    var report by remember { mutableStateOf("") }
    Screen("Help & support", { nav.popBackStack() }) {
        SectionTitle("FAQ")
        FAQ.forEachIndexed { i, (q, a) ->
            Tile(Modifier.fillMaxWidth(), { open = if (open == i) -1 else i }) {
                Text(q, fontWeight = FontWeight.Medium)
                if (open == i) Text(a, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        SectionTitle("Report a problem")
        OutlinedTextField(report, { report = it }, label = { Text("Describe the problem") }, modifier = Modifier.fillMaxWidth().height(120.dp))
        Text("Do not include your PIN, OTP or card details.", fontSize = 12.sp)
        BigButton("Send report", enabled = report.length >= 10) {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (com.paynova.upi.payment.ProviderConfig.SUPPORT_EMAIL.isNotBlank()) putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(com.paynova.upi.payment.ProviderConfig.SUPPORT_EMAIL))
                putExtra(android.content.Intent.EXTRA_SUBJECT, "PayNova problem report")
                putExtra(android.content.Intent.EXTRA_TEXT, report + "\n\nApp version 1.0.0 (" + (if (Graph.isDemo) "demo" else "production") + ")")
            }
            ctx.startActivity(android.content.Intent.createChooser(i, "Send report"))
        }
        if (com.paynova.upi.payment.ProviderConfig.SUPPORT_EMAIL.isBlank()) Text("No support email is configured yet; the report opens your share sheet.", fontSize = 12.sp)
    }
}
