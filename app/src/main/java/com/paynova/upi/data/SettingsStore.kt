package com.paynova.upi.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.paynova.upi.util.PinGuard
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App settings. PIN hash and auth token live in EncryptedSharedPreferences. The UPI PIN is never stored anywhere. */
class SettingsStore(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("paynova_settings", Context.MODE_PRIVATE)
    private val sp: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            ctx,
            "paynova_secure",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("paynova_secure_fallback", Context.MODE_PRIVATE)
    }

    var darkMode by mutableStateOf(p.getBoolean("dark", false))
    var ttsEnabled by mutableStateOf(p.getBoolean("tts_on", true))
    var ttsLang by mutableStateOf(p.getString("tts_lang", "en") ?: "en")
    var ttsRate by mutableStateOf(p.getFloat("tts_rate", 1f))
    var ttsVol by mutableStateOf(p.getFloat("tts_vol", 1f))
    var ttsVoice by mutableIntStateOf(p.getInt("tts_voice", 0))
    var lockEnabled by mutableStateOf(p.getBoolean("lock_on", false))
    var biometric by mutableStateOf(p.getBoolean("bio_on", false))
    var autoLockSec by mutableIntStateOf(p.getInt("autolock", 30))
    var ownVpa by mutableStateOf(p.getString("own_vpa", "") ?: "")
    var displayName by mutableStateOf(p.getString("name", "") ?: "")
    var prod by mutableStateOf(p.getBoolean("prod", false))
    var notify by mutableStateOf(p.getBoolean("notify", false))
    var lastSeenNotif by mutableStateOf(p.getLong("seen_notif", 0L))
    var pinLen by mutableIntStateOf(sp.getInt("pin_len", 4))
    var authToken by mutableStateOf(sp.getString("token", "") ?: "")

    fun updateDark(v: Boolean) { darkMode = v; p.edit().putBoolean("dark", v).apply() }
    fun updateTtsEnabled(v: Boolean) { ttsEnabled = v; p.edit().putBoolean("tts_on", v).apply() }
    fun updateTtsLang(v: String) { ttsLang = v; ttsVoice = 0; p.edit().putString("tts_lang", v).putInt("tts_voice", 0).apply() }
    fun updateTtsRate(v: Float) { ttsRate = v; p.edit().putFloat("tts_rate", v).apply() }
    fun updateTtsVol(v: Float) { ttsVol = v; p.edit().putFloat("tts_vol", v).apply() }
    fun updateTtsVoice(v: Int) { ttsVoice = v; p.edit().putInt("tts_voice", v).apply() }
    fun updateLock(v: Boolean) { lockEnabled = v; p.edit().putBoolean("lock_on", v).apply() }
    fun updateBiometric(v: Boolean) { biometric = v; p.edit().putBoolean("bio_on", v).apply() }
    fun updateAutoLock(v: Int) { autoLockSec = v; p.edit().putInt("autolock", v).apply() }
    fun updateOwnVpa(v: String) { ownVpa = v; p.edit().putString("own_vpa", v).apply() }
    fun updateName(v: String) { displayName = v; p.edit().putString("name", v).apply() }
    fun updateProd(v: Boolean) { prod = v; p.edit().putBoolean("prod", v).apply() }
    fun updateNotify(v: Boolean) { notify = v; p.edit().putBoolean("notify", v).apply() }
    fun updateLastSeen(v: Long) { lastSeenNotif = v; p.edit().putLong("seen_notif", v).apply() }
    fun updateToken(v: String) { authToken = v; sp.edit().putString("token", v).apply() }

    // ---- App-lock PIN (PBKDF2, salted) ----
    private val guard = PinGuard(clock = { android.os.SystemClock.elapsedRealtime() })
    val hasPin: Boolean get() = sp.contains("pin_hash")

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 60_000, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return Base64.encodeToString(f.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .putInt("pin_len", pin.length)
            .apply()
        pinLen = pin.length
    }

    private fun verify(pin: String): Boolean {
        val h = sp.getString("pin_hash", null) ?: return false
        val salt = Base64.decode(sp.getString("pin_salt", "") ?: "", Base64.NO_WRAP)
        return MessageDigest.isEqual(h.toByteArray(), hash(pin, salt).toByteArray())
    }

    /** Returns null on success, or an error message (wrong PIN / temporarily locked). */
    fun tryPin(pin: String): String? = guard.check { verify(pin) }

    /** Used after device-credential re-authentication ("Forgot PIN"). */
    fun resetLock() {
        sp.edit().remove("pin_hash").remove("pin_salt").apply()
        updateLock(false)
        updateBiometric(false)
    }

    // ---- Favourites, saved beneficiaries, announcements ----
    fun favourites(): Set<String> = p.getStringSet("fav", emptySet()) ?: emptySet()
    var favVersion by mutableIntStateOf(0)
    fun toggleFavourite(number: String) {
        val s = favourites().toMutableSet()
        if (!s.add(number)) s.remove(number)
        p.edit().putStringSet("fav", s).apply()
        favVersion++
    }

    var benVersion by mutableIntStateOf(0)
    fun beneficiaries(): List<Pair<String, String>> =
        (p.getStringSet("ben", emptySet()) ?: emptySet()).mapNotNull {
            val i = it.indexOf('|')
            if (i < 0) null else it.substring(0, i) to it.substring(i + 1)
        }.sortedBy { it.first.lowercase() }

    fun saveBeneficiary(name: String, vpa: String) {
        val s = (p.getStringSet("ben", emptySet()) ?: emptySet()).filterNot { it.endsWith("|$vpa") }.toMutableSet()
        s.add("$name|$vpa")
        p.edit().putStringSet("ben", s).apply()
        benVersion++
    }

    /** True only the first time an id is marked - guarantees one voice announcement per transaction. */
    fun markAnnounced(id: String): Boolean {
        val s = (p.getStringSet("announced", emptySet()) ?: emptySet()).toMutableSet()
        if (!s.add(id)) return false
        p.edit().putStringSet("announced", s).apply()
        return true
    }

    fun referralCode(): String {
        val existing = p.getString("ref", null)
        if (existing != null) return existing
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        val code = "PN" + (1..6).map { chars[r.nextInt(chars.length)] }.joinToString("")
        p.edit().putString("ref", code).apply()
        return code
    }
}
