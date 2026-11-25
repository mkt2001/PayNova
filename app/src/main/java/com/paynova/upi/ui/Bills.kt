package com.paynova.upi.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.payment.Bill
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.Recipient
import com.paynova.upi.util.Money
import kotlinx.coroutines.launch

private val BILL_CATEGORIES = listOf(
    "mobile" to "Mobile recharge", "dth" to "DTH recharge", "electricity" to "Electricity bill",
    "water" to "Water bill", "gas" to "Gas bill", "broadband" to "Broadband",
    "landline" to "Landline", "creditcard" to "Credit card bill", "fastag" to "FASTag", "other" to "Other bills"
)

private fun categoryOf(key: String) = when (key) {
    "mobile", "dth", "broadband", "landline" -> "Recharge"
    "fastag" -> "Travel"
    else -> "Bills"
}

@Composable
fun RechargeScreen(nav: NavHostController) {
    Screen("Recharge & Bills", null) {
        if (Graph.isDemo) DemoBanner()
        BILL_CATEGORIES.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (k, label) ->
                    Tile(Modifier.weight(1f), { nav.navigate("bill/$k") }) { Text(label, fontWeight = FontWeight.Medium) }
                }
                if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
        Notice("Bill fetching and payment need an authorized BBPS operator. Until one is connected, this section runs in clearly-labelled demo mode.")
        BigButton("Bill payment history") { nav.navigate("history") }
    }
}

@Composable
fun BillScreen(nav: NavHostController, vm: PayViewModel, key: String) {
    val label = BILL_CATEGORIES.firstOrNull { it.first == key }?.second ?: "Bill"
    val manual = key == "mobile" || key == "dth"
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var bill by remember { mutableStateOf<Bill?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val operators = if (key == "mobile") listOf("Airtel", "Jio", "Vi", "BSNL") else listOf("Tata Play", "Airtel Digital", "Dish TV", "Sun Direct")

    Screen(label, { nav.popBackStack() }) {
        if (Graph.isDemo) DemoBanner()
        OutlinedTextField(
            id, { id = it.trim().take(20) },
            label = { Text(if (key == "mobile") "Mobile number" else if (key == "dth") "Subscriber ID" else "Customer / consumer number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (key == "mobile") KeyboardType.Phone else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        if (manual) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                operators.forEach { o -> Pill(o, operator == o) { operator = o } }
            }
            OutlinedTextField(
                amount, { if (it.length <= 8) amount = it }, label = { Text("Recharge amount (₹)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            Text("Plan browsing needs an operator integration and is not available yet.", fontSize = 12.sp)
            val paise = Money.parsePaise(amount)
            BigButton("Continue", enabled = id.length >= 6 && operator.isNotEmpty() && paise != null) {
                vm.begin(
                    Draft(
                        method = "BBPS", amountPaise = paise ?: 0L, amountLocked = true, category = categoryOf(key),
                        note = "$label $id",
                        resolved = Recipient("$operator $label", "bbps:$key:$id", false, "DEMO biller")
                    )
                )
                nav.navigate("confirm")
            }
        } else {
            BigButton("Fetch bill", enabled = id.length >= 3 && !loading) {
                loading = true; error = null; bill = null
                scope.launch {
                    Graph.provider.fetchBill(label, id)
                        .onSuccess { bill = it }
                        .onFailure { error = it.message ?: "Could not fetch bill" }
                    loading = false
                }
            }
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            bill?.let { b ->
                Tile(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Biller", b.billerName)
                        InfoRow("Customer ID", b.customerId)
                        InfoRow("Customer name", b.customerName)
                        InfoRow("Due date", b.dueDate)
                        InfoRow("Outstanding amount", rupees(b.amountPaise))
                    }
                }
                BigButton("Pay ${rupees(b.amountPaise)}") {
                    vm.begin(
                        Draft(
                            method = "BBPS", amountPaise = b.amountPaise, amountLocked = true, category = categoryOf(key),
                            note = "$label ${b.customerId}",
                            resolved = Recipient(b.billerName, "bbps:$key:${b.customerId}", !b.demo, if (b.demo) "DEMO biller" else null)
                        )
                    )
                    nav.navigate("confirm")
                }
            }
        }
    }
}
