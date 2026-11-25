package com.paynova.upi.util

import java.math.BigDecimal

object Money {
    const val MAX_RUPEES = 100_000L

    /** Parses user-entered rupees ("250", "250.5") to paise. Null if invalid, <= 0 or above the UPI limit. */
    fun parsePaise(s: String): Long? {
        val t = s.trim()
        if (!Regex("^\\d{1,7}(\\.\\d{1,2})?$").matches(t)) return null
        val p = BigDecimal(t).movePointRight(2).toLong()
        return if (p <= 0L || p > MAX_RUPEES * 100) null else p
    }

    /** "250" or "250.50" (no currency symbol). */
    fun plain(paise: Long): String =
        if (paise % 100 == 0L) (paise / 100).toString() else "%d.%02d".format(paise / 100, paise % 100)
}

object Phones {
    /** Returns a 10-digit Indian mobile number or null. */
    fun normalizeIndian(raw: String): String? {
        val d = raw.filter { it.isDigit() }
        val n = when {
            d.length == 10 -> d
            d.length == 11 && d.startsWith("0") -> d.substring(1)
            d.length == 12 && d.startsWith("91") -> d.substring(2)
            else -> return null
        }
        return if (Regex("^[6-9]\\d{9}$").matches(n)) n else null
    }
}

/** Brute-force guard for the app-lock PIN. Pure Kotlin so it can be unit tested. */
class PinGuard(
    private val maxFails: Int = 5,
    private val lockMs: Long = 30_000L,
    private val clock: () -> Long
) {
    private var fails = 0
    private var until = 0L

    /** Returns null when [ok] succeeds, otherwise a user-facing error message. */
    fun check(ok: () -> Boolean): String? {
        val now = clock()
        if (now < until) return "Too many attempts. Try again in ${(until - now) / 1000 + 1}s"
        if (ok()) {
            fails = 0
            return null
        }
        fails++
        if (fails >= maxFails) {
            fails = 0
            until = now + lockMs
            return "Too many attempts. Locked for ${lockMs / 1000}s"
        }
        return "Wrong PIN. ${maxFails - fails} attempts left"
    }
}
