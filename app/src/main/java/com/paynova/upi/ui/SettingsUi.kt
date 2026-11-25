package com.paynova.upi.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.paynova.upi.Graph
import com.paynova.upi.payment.BackendPaymentProvider
import com.paynova.upi.payment.ProviderConfig
import kotlinx.coroutines.launch

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val s = Graph.settings
    val scope = rememberCoroutineScope()
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { s.updateNotify(it) }
    var voiceTick by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loginMsg by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Screen("Settings", { nav.popBackStack() }) {
        SectionTitle("Profile")
        OutlinedTextField(s.displayName, { s.updateName(it) }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Pill("UPI ID, My QR & payment link") { nav.navigate("myqr") }

        SectionTitle("Security")
        SwitchRow("App lock", "Protect PayNova with your own app PIN (not your UPI PIN)", s.lockEnabled && s.hasPin) { on ->
            if (on) nav.navigate("pinsetup")
            else Biometrics.prompt(ctx.activity(), "Verify it's you to turn off app lock", true, { s.updateLock(false); s.updateBiometric(false) }, { toast(ctx, it) })
        }
        if (s.lockEnabled && s.hasPin) {
            SwitchRow("Biometric unlock", if (Biometrics.canUse(ctx)) "Fingerprint / face" else "No biometrics enrolled on this phone", s.biometric, Biometrics.canUse(ctx)) { s.updateBiometric(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Change PIN") { nav.navigate("changepin") }
            }
            Text("Auto-lock after", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Instantly", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (sec, label) -> Pill(label, s.autoLockSec == sec) { s.updateAutoLock(sec) } }
            }
        }

        SectionTitle("Appearance")
        SwitchRow("Dark mode", null, s.darkMode) { s.updateDark(it) }

        SectionTitle("Payment voice")
        SwitchRow("Announce successful payments", "Spoken only after the provider confirms success", s.ttsEnabled) { s.updateTtsEnabled(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("English", s.ttsLang == "en") { s.updateTtsLang("en"); voiceTick++ }
            Pill("हिन्दी", s.ttsLang == "hi") { s.updateTtsLang("hi"); voiceTick++ }
        }
        val voices = remember(s.ttsLang, voiceTick) { Graph.announcer.voices(s.ttsLang) }
        Pill(if (voices.isEmpty()) "Voice: device default" else "Voice ${s.ttsVoice.coerceIn(0, voices.size - 1) + 1} of ${voices.size} (tap to change)") {
            if (voices.isNotEmpty()) s.updateTtsVoice((s.ttsVoice + 1) % voices.size)
        }
        Text("Speed", fontSize = 13.sp)
        Slider(s.ttsRate, { s.updateTtsRate(it) }, valueRange = 0.5f..1.5f)
        Text("Volume", fontSize = 13.sp)
        Slider(s.ttsVol, { s.updateTtsVol(it) }, valueRange = 0.1f..1f)
        Pill("Test voice") { Graph.announcer.test() }
        if (s.ttsLang == "hi" && voices.isEmpty()) Text("No Hindi voice found. Install Hindi in your phone's text-to-speech settings.", fontSize = 12.sp)

        SectionTitle("Notifications")
        SwitchRow("Transaction notifications", null, s.notify) { on ->
            if (!on) s.updateNotify(false)
            else if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            else s.updateNotify(true)
        }

        SectionTitle("Payment mode")
        Text(if (Graph.isDemo) "DEMO — simulated payments with fake money." else "PRODUCTION — real provider connected.", fontWeight = FontWeight.SemiBold)
        SwitchRow(
            "Use production provider",
            if (ProviderConfig.isConfigured) "Backend: ${ProviderConfig.baseUrl}" else "Disabled: build has no HTTPS backend URL / provider credentials yet",
            s.prod && ProviderConfig.isConfigured, ProviderConfig.isConfigured
        ) { s.updateProd(it) }
        if (ProviderConfig.isConfigured && s.prod) {
            OutlinedTextField(email, { email = it.trim() }, label = { Text("Backend email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pass, { pass = it }, label = { Text("Backend password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Pill(if (s.authToken.isBlank()) "Sign in" else "Signed in (tap to refresh)") {
                scope.launch {
                    BackendPaymentProvider.login(email, pass)
                        .onSuccess { s.updateToken(it); loginMsg = "Signed in"; pass = "" }
                        .onFailure { loginMsg = it.message ?: "Sign-in failed" }
                }
            }
            loginMsg?.let { Text(it, fontSize = 12.sp) }
        }

        SectionTitle("Privacy")
        Text("Contacts are read on this phone only and are never uploaded. Your UPI PIN is never collected. Transaction history is cached locally.", fontSize = 13.sp)
        if (!confirmClear) Pill("Clear local transaction cache") { confirmClear = true }
        else Pill("Tap again to confirm delete", true) { scope.launch { Graph.repo.clearLocal(); confirmClear = false; toast(ctx, "Local cache cleared") } }

        SectionTitle("More")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Help & FAQ") { nav.navigate("support") }
            Pill("Rewards") { nav.navigate("rewards") }
        }
        Text("PayNova 1.0.0", fontSize = 12.sp)
    }
}
