package com.paynova.upi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.data.title
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.UpiParser
import com.paynova.upi.util.Phones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceContact(val name: String, val number: String, val photo: String?)

/** Loads real device contacts. Everything stays on the phone - nothing is uploaded. */
suspend fun loadContacts(ctx: android.content.Context): List<DeviceContact> = withContext(Dispatchers.IO) {
    val out = LinkedHashMap<String, DeviceContact>()
    val c = ctx.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI),
        null, null, Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
    )
    c?.use {
        while (it.moveToNext()) {
            val name = it.getString(0) ?: ""
            val num = Phones.normalizeIndian(it.getString(1) ?: "")
            if (num != null && name.isNotBlank() && !out.containsKey(num)) {
                out[num] = DeviceContact(name, num, it.getString(2))
            }
        }
    }
    out.values.sortedBy { it.name.lowercase() }
}

@Composable
private fun ContactAvatar(c: DeviceContact) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, c.photo) {
        value = if (c.photo == null) null else withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.openInputStream(Uri.parse(c.photo))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (e: Exception) {
                null
            }
        }
    }
    Avatar(c.name, photo = bmp)
}

@Composable
fun ContactsScreen(nav: NavHostController, vm: PayViewModel) {
    val ctx = LocalContext.current
    val s = Graph.settings
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    var contacts by remember { mutableStateOf<List<DeviceContact>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }

    LaunchedEffect(granted, reload) {
        if (granted) contacts = loadContacts(ctx)
    }

    fun pick(c: DeviceContact) {
        vm.begin(Draft(method = "MOBILE", mobile = c.number, qrName = c.name))
        nav.navigate("resolve")
    }

    Screen("Pay Mobile Number", { nav.popBackStack() }, scroll = false) {
        if (!granted) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (asked) "Contacts permission was denied. You can still enter a number below, or allow contacts from app settings."
                    else "Allow contacts so you can pick who to pay. Your contacts stay on your phone.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.padding(8.dp))
                TextButton(onClick = { launcher.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts") }
                if (asked) {
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                    }) { Text("Open app settings") }
                }
                var manual by remember { mutableStateOf("") }
                OutlinedTextField(
                    manual, { if (it.length <= 13) manual = it }, label = { Text("Enter mobile number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = Phones.normalizeIndian(manual) != null,
                    onClick = { pick(DeviceContact("", Phones.normalizeIndian(manual)!!, null)) }
                ) { Text("Continue") }
            }
        } else {
            OutlinedTextField(
                q, { q = it }, label = { Text("Search name or number") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Favourites", favOnly) { favOnly = !favOnly }
                Pill("Refresh") { reload++ }
            }
            val recents = remember(txs) { txs.filter { it.direction == "SENT" }.distinctBy { it.counterpartyVpa }.take(6) }
            if (recents.isNotEmpty() && q.isBlank()) {
                Text("Recently paid", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.forEach { t ->
                        Pill(t.title().take(16)) {
                            vm.begin(Draft(method = "UPI_ID", vpa = t.counterpartyVpa, qrName = t.counterpartyName.ifBlank { null }))
                            nav.navigate("resolve")
                        }
                    }
                }
            }
            val all = contacts
            if (all == null) {
                Text("Loading contacts…")
            } else {
                val favs = s.favourites().also { s.favVersion }
                val list = all.filter {
                    (!favOnly || favs.contains(it.number)) &&
                        (q.isBlank() || it.name.contains(q, true) || it.number.contains(q.filter { c -> c.isDigit() }.ifEmpty { "\u0000" }))
                }
                if (list.isEmpty()) {
                    Text(if (all.isEmpty()) "No contacts with Indian mobile numbers found on this phone." else "No matching contacts.")
                }
                val grouped = list.groupBy { it.name.first().uppercaseChar().let { ch -> if (ch.isLetter()) ch.toString() else "#" } }
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (letter, group) ->
                        item(key = "h$letter") { Text(letter, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                        items(group, key = { it.number }) { c ->
                            Row(Modifier.fillMaxWidth().clickable { pick(c) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ContactAvatar(c)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text("+91 " + c.number, fontSize = 12.sp)
                                }
                                TextButton(onClick = { s.toggleFavourite(c.number) }) {
                                    Text(if (favs.contains(c.number)) "★" else "☆", fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PayUpiScreen(nav: NavHostController, vm: PayViewModel) {
    val s = Graph.settings
    val txs by Graph.repo.all.collectAsState(initial = emptyList())
    var vpa by remember { mutableStateOf("") }
    val valid = UpiParser.isValidVpa(vpa)
    Screen("Pay to UPI ID", { nav.popBackStack() }) {
        OutlinedTextField(
            vpa, { vpa = it.trim() }, label = { Text("UPI ID (e.g. name@bank)") }, singleLine = true,
            isError = vpa.isNotEmpty() && !valid, modifier = Modifier.fillMaxWidth()
        )
        BigButton("Verify & continue", enabled = valid) {
            vm.begin(Draft(method = "UPI_ID", vpa = vpa))
            nav.navigate("resolve")
        }
        val saved = s.beneficiaries().also { s.benVersion }
        if (saved.isNotEmpty()) {
            SectionTitle("Saved beneficiaries")
            saved.forEach { (name, id) ->
                Tile(Modifier.fillMaxWidth(), {
                    vm.begin(Draft(method = "UPI_ID", vpa = id, qrName = name.ifBlank { null }))
                    nav.navigate("resolve")
                }) { Text(name.ifBlank { id }, fontWeight = FontWeight.Medium); Text(id, fontSize = 12.sp) }
            }
        }
        val recents = txs.filter { it.direction == "SENT" && UpiParser.isValidVpa(it.counterpartyVpa) }.distinctBy { it.counterpartyVpa }.take(5)
        if (recents.isNotEmpty()) {
            SectionTitle("Recent")
            recents.forEach { t ->
                Tile(Modifier.fillMaxWidth(), {
                    vm.begin(Draft(method = "UPI_ID", vpa = t.counterpartyVpa, qrName = t.counterpartyName.ifBlank { null }))
                    nav.navigate("resolve")
                }) { Text(t.title(), fontWeight = FontWeight.Medium); Text(t.counterpartyVpa, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun PayBankScreen(nav: NavHostController, vm: PayViewModel) {
    var name by remember { mutableStateOf("") }
    var acc by remember { mutableStateOf("") }
    var acc2 by remember { mutableStateOf("") }
    var ifsc by remember { mutableStateOf("") }
    val ok = Regex("^\\d{9,18}$").matches(acc) && acc == acc2 && Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(ifsc) && name.isNotBlank()
    Screen("Pay to Bank Account", { nav.popBackStack() }) {
        OutlinedTextField(name, { name = it }, label = { Text("Account holder name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            acc, { if (it.all(Char::isDigit) && it.length <= 18) acc = it }, label = { Text("Account number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            acc2, { if (it.all(Char::isDigit) && it.length <= 18) acc2 = it }, label = { Text("Confirm account number") }, singleLine = true,
            isError = acc2.isNotEmpty() && acc != acc2, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            ifsc, { ifsc = it.uppercase().take(11) }, label = { Text("IFSC code") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        BigButton("Verify & continue", enabled = ok) {
            vm.begin(Draft(method = "BANK", account = acc, ifsc = ifsc, qrName = name))
            nav.navigate("resolve")
        }
    }
}

@Composable
fun BalanceScreen(nav: NavHostController) {
    Screen("Check Bank Balance", { nav.popBackStack() }) {
        if (Graph.isDemo) DemoBanner()
        Notice(
            "Your real bank balance is shown only inside your bank's secure UPI screen, after you enter your UPI PIN there. " +
                "PayNova never sees or stores your UPI PIN or balance."
        )
        Text(
            if (Graph.isDemo) "Balance check needs a connected UPI provider. In demo mode there is no real bank account to query."
            else "Balance check is handed off to the connected provider.",
            fontSize = 14.sp
        )
    }
}
