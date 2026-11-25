package com.paynova.upi.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.data.title

@Composable
fun HistoryScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf("All") }
    var q by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }
    var dateF by remember { mutableStateOf("Any time") }
    var minA by remember { mutableStateOf("") }
    var maxA by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Graph.repo.syncRemote()
        Graph.repo.refreshPending()
    }

    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val minP = minA.toDoubleOrNull()?.let { (it * 100).toLong() }
    val maxP = maxA.toDoubleOrNull()?.let { (it * 100).toLong() }
    val list = txs.filter { t ->
        val tabOk = when (tab) {
            "Received" -> t.direction == "RECEIVED"
            "Sent" -> t.direction == "SENT"
            "Successful" -> t.status == "SUCCESS"
            "Failed" -> t.status == "FAILED"
            "Pending" -> t.status == "PENDING"
            "Refunds" -> t.direction == "REFUND" || t.status == "REVERSED"
            else -> true
        }
        val qOk = q.isBlank() || t.title().contains(q, true) || t.counterpartyVpa.contains(q, true) || t.providerRef.contains(q, true)
        val dOk = when (dateF) {
            "Today" -> now - t.createdAt < day
            "7 days" -> now - t.createdAt < 7 * day
            "30 days" -> now - t.createdAt < 30 * day
            else -> true
        }
        val aOk = (minP == null || t.amountPaise >= minP) && (maxP == null || t.amountPaise <= maxP)
        tabOk && qOk && dOk && aOk
    }

    val pdfSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Receipts.statementPdf(list)) } }.isSuccess
            toast(ctx, if (ok) "Statement saved" else "Could not save statement")
        }
    }

    Screen("History", null, scroll = false) {
        OutlinedTextField(q, { q = it }, label = { Text("Search name, UPI ID or reference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Received", "Sent", "Successful", "Failed", "Pending", "Refunds").forEach { Pill(it, tab == it) { tab = it } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (showFilters) "Hide filters" else "Date & amount filters") { showFilters = !showFilters }
            Pill("Export CSV") { Receipts.shareFile(ctx, "PayNova-transactions.csv", "text/csv", Receipts.csv(list).toByteArray()) }
            Pill("Statement") { pdfSaver.launch("PayNova-statement.pdf") }
        }
        if (showFilters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Any time", "Today", "7 days", "30 days").forEach { Pill(it, dateF == it) { dateF = it } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minA, { minA = it }, label = { Text("Min ₹") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(maxA, { maxA = it }, label = { Text("Max ₹") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
        if (list.isEmpty()) {
            Text(
                if (txs.isEmpty()) "No transactions yet. Payments you make will appear here and stay available offline."
                else "No transactions match your filters.",
                fontSize = 14.sp
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { t -> TxRow(t) { nav.navigate("receipt/${t.id}") } }
        }
    }
}
