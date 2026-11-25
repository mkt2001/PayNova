package com.paynova.upi.payment

import java.math.BigDecimal
import java.net.URLDecoder
import java.net.URLEncoder

enum class QrType(val label: String) {
    STATIC("Static UPI QR"),
    DYNAMIC("Dynamic UPI QR"),
    MERCHANT("Merchant QR"),
    LINK("UPI payment link"),
    INVALID("Invalid QR")
}

data class UpiQr(
    val type: QrType,
    val vpa: String? = null,
    val name: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val note: String? = null,
    val merchantCode: String? = null,
    val txnRef: String? = null,
    val params: Map<String, String> = emptyMap(),
    val error: String? = null
)

/**
 * Parses UPI deep links / QR payloads (pa, pn, am, cu, tn, mc, tr ...).
 * Never invents data: anything missing stays null and the UI shows "Not available".
 * A QR scan does NOT verify identity; verification is only via the PSP resolution API.
 */
object UpiParser {
    private val VPA = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{2,64}$")
    private val AMOUNT = Regex("^\\d{1,9}(\\.\\d{1,2})?$")
    const val MAX_RUPEES = 100_000L

    fun isValidVpa(s: String): Boolean = VPA.matches(s.trim())

    fun parse(raw: String): UpiQr {
        val text = raw.trim()
        val lower = text.lowercase()
        val isUpi = lower.startsWith("upi://pay")
        val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
        if (!isUpi && !isHttp) return bad("This QR code is not a UPI payment QR.")
        val query = text.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return bad("This QR code has no UPI payment details.")

        val params = LinkedHashMap<String, String>()
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            params[part.substringBefore('=').lowercase()] = decode(part.substringAfter('=', ""))
        }

        val pa = params["pa"]?.trim().orEmpty()
        if (pa.isEmpty()) return bad("UPI ID (pa) is missing in this QR.", params)
        if (!isValidVpa(pa)) return bad("The UPI ID in this QR is invalid.", params)

        val cu = (params["cu"] ?: "INR").uppercase()
        if (cu != "INR") return bad("Unsupported currency: $cu. Only INR is supported.", params)

        val am = params["am"]?.trim()?.takeIf { it.isNotEmpty() }
        if (am != null) {
            if (!AMOUNT.matches(am)) return bad("The amount in this QR is invalid.", params)
            val v = BigDecimal(am)
            if (v.signum() <= 0 || v > BigDecimal(MAX_RUPEES)) {
                return bad("The amount in this QR is outside the allowed range.", params)
            }
        }
        val mc = params["mc"]?.trim()?.takeIf { it.isNotEmpty() }
        val tr = params["tr"]?.trim()?.takeIf { it.isNotEmpty() }
        val type = when {
            isHttp -> QrType.LINK
            mc != null && mc != "0000" -> QrType.MERCHANT
            am != null || tr != null -> QrType.DYNAMIC
            else -> QrType.STATIC
        }
        return UpiQr(
            type = type,
            vpa = pa,
            name = params["pn"]?.trim()?.takeIf { it.isNotEmpty() },
            amount = am,
            currency = cu,
            note = params["tn"]?.trim()?.takeIf { it.isNotEmpty() },
            merchantCode = mc,
            txnRef = tr,
            params = params
        )
    }

    fun toPaise(amount: String?): Long =
        if (amount == null) 0L else BigDecimal(amount).movePointRight(2).toLong()

    fun buildLink(vpa: String, name: String?, amountRupees: String?, note: String?): String {
        val sb = StringBuilder("upi://pay?pa=").append(vpa)
        if (!name.isNullOrBlank()) sb.append("&pn=").append(enc(name))
        if (!amountRupees.isNullOrBlank()) sb.append("&am=").append(amountRupees)
        sb.append("&cu=INR")
        if (!note.isNullOrBlank()) sb.append("&tn=").append(enc(note))
        return sb.toString()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    private fun bad(msg: String, params: Map<String, String> = emptyMap()) =
        UpiQr(type = QrType.INVALID, params = params, error = msg)
}
