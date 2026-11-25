package com.paynova.upi.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.paynova.upi.Graph

object LockState {
    var locked by mutableStateOf(false)
    var lastBg: Long = 0L
}

object Biometrics {
    fun canUse(ctx: android.content.Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** deviceCredential = true lets the person confirm with their phone PIN/pattern (used for "Forgot PIN"). */
    fun prompt(act: FragmentActivity, title: String, deviceCredential: Boolean, onOk: () -> Unit, onFail: (String) -> Unit) {
        val b = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        val info = if (deviceCredential) {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        } else {
            b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).setNegativeButtonText("Use PIN").build()
        }
        val bp = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onFail(errString.toString()) }
        })
        bp.authenticate(info)
    }
}

@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val s = Graph.settings
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (s.biometric && Biometrics.canUse(ctx)) {
            Biometrics.prompt(ctx.activity(), "Unlock PayNova", false, { LockState.locked = false }, {})
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PayNova", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter your app PIN")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v ->
                if (v.length <= s.pinLen && v.all { it.isDigit() }) {
                    pin = v
                    err = null
                    if (v.length == s.pinLen) {
                        val e = s.tryPin(v)
                        if (e == null) LockState.locked = false else { err = e; pin = "" }
                    }
                }
            },
            label = { Text("App PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (s.biometric && Biometrics.canUse(ctx)) {
            TextButton(onClick = {
                Biometrics.prompt(ctx.activity(), "Unlock PayNova", false, { LockState.locked = false }, {})
            }) { Text("Use fingerprint / face") }
        }
        TextButton(onClick = {
            Biometrics.prompt(ctx.activity(), "Verify it's you to reset the app PIN", true,
                { s.resetLock(); LockState.locked = false }, { err = it })
        }) { Text("Forgot PIN?") }
    }
}

/** Create or change the app-lock PIN (4 or 6 digits, with confirmation). This is NOT the UPI PIN. */
@Composable
fun PinSetupScreen(nav: NavHostController, change: Boolean) {
    val s = Graph.settings
    var step by remember { mutableIntStateOf(if (change && s.hasPin) 0 else 1) }
    var len by remember { mutableIntStateOf(if (s.hasPin) s.pinLen else 4) }
    var first by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val need = if (step == 0) s.pinLen else len

    Screen(if (change) "Change app PIN" else "Create app PIN", { nav.popBackStack() }) {
        Notice("This PIN only locks the PayNova app. It is never used for UPI payments — your UPI PIN is entered only in your bank's secure UPI screen.")
        if (step == 1) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("4-digit", len == 4) { len = 4; input = "" }
                Pill("6-digit", len == 6) { len = 6; input = "" }
            }
        }
        Text(when (step) { 0 -> "Enter current PIN"; 1 -> "Enter new $len-digit PIN"; else -> "Confirm new PIN" })
        OutlinedTextField(
            value = input,
            onValueChange = { v ->
                if (v.length <= need && v.all { it.isDigit() }) {
                    input = v
                    err = null
                    if (v.length == need) {
                        when (step) {
                            0 -> { val e = s.tryPin(v); if (e == null) { step = 1; input = "" } else { err = e; input = "" } }
                            1 -> { first = v; step = 2; input = "" }
                            else -> {
                                if (v == first) {
                                    s.setPin(first)
                                    s.updateLock(true)
                                    nav.popBackStack()
                                } else { err = "PINs do not match. Try again."; step = 1; input = "" }
                            }
                        }
                    }
                }
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
