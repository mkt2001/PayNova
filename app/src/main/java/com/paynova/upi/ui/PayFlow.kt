package com.paynova.upi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.data.title
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.Recipient
import com.paynova.upi.payment.UpiParser
import com.paynova.upi.util.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

val CATEGORIES = listOf("Food", "Shopping", "Bills", "Travel", "Recharge", "Entertainment", "Health", "Others")

@Composable
fun ResolveScreen(nav: NavHostController, vm: PayViewModel) {
    val d = vm.draft
    if (d == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var result by remember { mutableStateOf<Result<Recipient>?>(null) }
    LaunchedEffect(d.method, d.vpa, d.mobile, d.account) {
        result = null
        val p = Graph.provider
        result = try {
            when (d.method) {
                "MOBILE" -> p.resolveMobile(d.mobile.orEmpty(), d.qrName)
                "BANK" -> p.resolveBank(d.account.orEmpty(), d.ifsc.orEmpty(), d.qrName)
                else -> p.resolveVpa(d.vpa.orEmpty(), d.qrName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    Screen("Recipient details", { nav.popBackStack() }) {
        if (Graph.isDemo) DemoBanner()
        val r = result
        if (r == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val rec = r.getOrNull()
            Tile(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoRow("Name", rec?.name ?: d.qrName)
                    InfoRow(
                        when (d.method) { "MOBILE" -> "Mobile / UPI route"; "BANK" -> "Bank account"; else -> "UPI ID / VPA" },
                        rec?.vpa ?: d.vpa ?: d.mobile
                    )
                    InfoRow("Amount in QR", d.qr?.amount?.let { "₹$it" })
                    InfoRow("Currency", d.qr?.currency)
                    InfoRow("Transaction note", d.qr?.note)
                    InfoRow("Merchant info (from provider)", rec?.merchantInfo)
                    InfoRow("QR type", d.qr?.type?.label ?: "Not scanned")
                    InfoRow("Payment status", "Not initiated")
                    InfoRow(
                        "Verification",
                        if (rec == null) "Failed" else if (rec.verified) "Verified by payment provider" else "Not verified"
                    )
                }
            }
            if (rec == null) {
                Text(r.exceptionOrNull()?.message ?: "Could not resolve recipient.", color = MaterialTheme.colorScheme.error)
            }
            Notice("Scanning a QR or typing a UPI ID does not prove who the receiver is. Check the name before you pay.")
            BigButton("Continue", enabled = rec != null) {
                vm.draft = d.copy(resolved = rec)
                nav.navigate("amount")
            }
        }
    }
}

@Composable
fun AmountScreen(nav: NavHostController, vm: PayViewModel) {
    val d = vm.draft
    val rcp = d?.resolved
    if (d == null || rcp == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    var amt by remember { mutableStateOf(if (d.amountPaise > 0) Money.plain(d.amountPaise) else "") }
    var note by remember { mutableStateOf(d.note) }
    var cat by remember { mutableStateOf(d.category) }
    val paise = Money.parsePaise(amt)
    Screen("Enter amount", { nav.popBackStack() }) {
        if (Graph.isDemo) DemoBanner()
        Text("Paying ${rcp.name ?: rcp.vpa}", fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = amt,
            onValueChange = { if (!d.amountLocked && it.length <= 10 && it.all { c -> c.isDigit() || c == '.' }) amt = it },
            label = { Text("Amount (₹)") },
            readOnly = d.amountLocked,
            singleLine = true,
            isError = amt.isNotEmpty() && paise == null,
            textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (d.amountLocked) Text("Amount is fixed by the QR code.", fontSize = 12.sp)
        if (amt.isNotEmpty() && paise == null) Text("Enter an amount between ₹0.01 and ₹1,00,000.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        OutlinedTextField(note, { if (it.length <= 50) note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Category", fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CATEGORIES.forEach { c -> Pill(c, cat == c) { cat = c } }
        }
        BigButton("Continue", enabled = paise != null) {
            vm.draft = d.copy(amountPaise = paise ?: 0L, note = note, category = cat)
            nav.navigate("confirm")
        }
    }
}

@Composable
fun ConfirmScreen(nav: NavHostController, vm: PayViewModel) {
    val d = vm.draft
    val r = d?.resolved
    if (d == null || r == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    LaunchedEffect(Unit) {
        vm.ensureKey()
        Graph.announcer   // warm up text-to-speech
    }
    Screen("Confirm payment", { nav.popBackStack() }) {
        if (Graph.isDemo) DemoBanner()
        Tile(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(rupees(d.amountPaise), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                InfoRow("To", r.name)
                InfoRow("UPI ID / account", r.vpa)
                InfoRow("Note", d.note.ifBlank { null })
                InfoRow("Category", d.category)
                InfoRow("Method", d.method)
                InfoRow("Verification", if (r.verified) "Verified by provider" else "Not verified")
            }
        }
        Notice(
            if (Graph.isDemo) "Next you will see a simulated PIN screen. No real money moves."
            else "You will confirm this payment in your bank's secure UPI screen. PayNova never sees your UPI PIN."
        )
        vm.payError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.paying) Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        BigButton("Pay ${rupees(d.amountPaise)}", enabled = !vm.paying) {
            if (Graph.isDemo) nav.navigate("pin")
            else vm.pay { id -> nav.navigate("receipt/$id") { popUpTo("home") } }
        }
    }
}

/** DEMO ONLY. Simulates a PIN screen with fake money; nothing entered here is stored or sent anywhere. */
@Composable
fun DemoPinScreen(nav: NavHostController, vm: PayViewModel) {
    var pin by remember { mutableStateOf("") }
    Screen("Authorize (DEMO)", { nav.popBackStack() }) {
        DemoBanner()
        Text("This is a simulated UPI PIN screen. Enter any 4–6 digits. Never type your real UPI PIN here.")
        OutlinedTextField(
            pin, { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("DEMO PIN") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        vm.payError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.paying) CircularProgressIndicator()
        BigButton("Authorize (DEMO)", enabled = pin.length >= 4 && !vm.paying) {
            vm.pay { id -> nav.navigate("receipt/$id") { popUpTo("home") } }
        }
    }
}

@Composable
fun ReceiptScreen(nav: NavHostController, vm: PayViewModel, id: String) {
    val ctx = LocalContext.current
    val tx by Graph.repo.observe(id).collectAsState(initial = null)
    val t = tx
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && t != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Receipts.receiptPdf(t)) } }.isSuccess
            toast(ctx, if (ok) "Receipt saved" else "Could not save receipt")
        }
    }
    if (t == null) {
        Screen("Receipt", { nav.popBackStack() }) { Text("Loading…") }
        return
    }
    LaunchedEffect(t.status) {
        if (t.status == "PENDING") repeat(60) {
            delay(3000)
            Graph.repo.refresh(id)
        }
    }
    val col = statusColor(t.status)
    Screen(Receipts.receiptTitle(t), { nav.navigate("home") { popUpTo("home") { inclusive = true } } }) {
        if (t.demo) DemoBanner()
        Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(col), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    when (t.status) { "SUCCESS" -> "✓"; "PENDING" -> "…"; else -> "✕" },
                    color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(Receipts.receiptTitle(t), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = col)
            Text(rupees(t.amountPaise), fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (t.status == "PENDING") Text("Waiting for the bank to confirm. This page updates automatically.", fontSize = 12.sp)
        }
        Tile(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow("Recipient", t.counterpartyName)
                InfoRow("UPI ID / account", t.counterpartyVpa)
                InfoRow("Date & time", fmtDateTime(t.createdAt))
                InfoRow("Payment method", t.method)
                InfoRow("Bank account", t.maskedAccount)
                InfoRow("UPI transaction ID", t.upiTxnId)
                InfoRow("Provider reference", t.providerRef)
                InfoRow("Status", statusLabel(t.status))
            }
        }
        val canRepeat = UpiParser.isValidVpa(t.counterpartyVpa)
        if (canRepeat) BigButton("Pay Again") {
            vm.begin(Draft(method = "UPI_ID", vpa = t.counterpartyVpa, qrName = t.counterpartyName.ifBlank { null }))
            nav.navigate("resolve")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Receipts.shareFile(ctx, "PayNova-receipt-${t.id.take(8)}.pdf", "application/pdf", Receipts.receiptPdf(t)) }, modifier = Modifier.weight(1f)) { Text("Share Receipt") }
            OutlinedButton(onClick = { saver.launch("PayNova-receipt-${t.id.take(8)}.pdf") }, modifier = Modifier.weight(1f)) { Text("Download") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("history") }, modifier = Modifier.weight(1f)) { Text("View History") }
            if (canRepeat && t.direction == "SENT") OutlinedButton(
                onClick = { Graph.settings.saveBeneficiary(t.counterpartyName, t.counterpartyVpa); toast(ctx, "Beneficiary saved") },
                modifier = Modifier.weight(1f)
            ) { Text("Save beneficiary") }
        }
    }
}
