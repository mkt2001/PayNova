package com.paynova.upi.payment

import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.TxStatus
import com.paynova.upi.util.Phones
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sandbox / test payment simulator. NEVER moves real money. Everything it returns is labelled DEMO.
 *
 * Test rules (whole rupees):
 *   amount ending in 13  -> FAILED
 *   amount ending in 17  -> stays PENDING for 30 s, then SUCCESS
 *   anything else        -> PENDING for 2 s, then SUCCESS
 */
object DemoPaymentProvider : PaymentProvider {
    override val mode = ProviderMode.DEMO
    override val supportsUpiLite = false

    var clock: () -> Long = { System.currentTimeMillis() }
    private val started = ConcurrentHashMap<String, Pair<Long, Long>>()
    private const val DEMO_ACCOUNT = "Demo Bank ••••0000"

    override suspend fun resolveVpa(vpa: String, qrName: String?): Result<Recipient> =
        if (UpiParser.isValidVpa(vpa)) {
            Result.success(Recipient(qrName?.takeIf { it.isNotBlank() }, vpa.trim(), false, null))
        } else {
            Result.failure(IllegalArgumentException("Invalid UPI ID"))
        }

    override suspend fun resolveMobile(mobile: String, contactName: String?): Result<Recipient> {
        val n = Phones.normalizeIndian(mobile)
            ?: return Result.failure(IllegalArgumentException("Enter a valid 10-digit mobile number"))
        return Result.success(
            Recipient(contactName, "$n@demo", false, "DEMO route - not a real UPI ID")
        )
    }

    override suspend fun resolveBank(account: String, ifsc: String, name: String?): Result<Recipient> {
        if (!Regex("^\\d{9,18}$").matches(account)) {
            return Result.failure(IllegalArgumentException("Account number must be 9-18 digits"))
        }
        if (!Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(ifsc.uppercase())) {
            return Result.failure(IllegalArgumentException("Invalid IFSC code"))
        }
        return Result.success(
            Recipient(name, "A/C ••••${account.takeLast(4)} · ${ifsc.uppercase()}", false, "DEMO - bank account not verified")
        )
    }

    override suspend fun initiate(req: PaymentRequest): Result<PaymentHandle> {
        val ref = "DEMO" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        started[ref] = clock() to req.amountPaise
        return Result.success(PaymentHandle(ref, TxStatus.PENDING, null, DEMO_ACCOUNT))
    }

    override suspend fun status(providerRef: String): Result<ProviderStatus> {
        val info = started[providerRef]
        val upiId = "DEMOUPI" + providerRef.takeLast(8)
        if (info == null) return Result.success(ProviderStatus(TxStatus.SUCCESS, upiId, DEMO_ACCOUNT))
        val rupees = info.second / 100
        val elapsed = clock() - info.first
        val st = when {
            rupees % 100 == 13L -> if (elapsed > 2_000) TxStatus.FAILED else TxStatus.PENDING
            rupees % 100 == 17L -> if (elapsed > 30_000) TxStatus.SUCCESS else TxStatus.PENDING
            else -> if (elapsed > 2_000) TxStatus.SUCCESS else TxStatus.PENDING
        }
        return Result.success(
            ProviderStatus(st, if (st == TxStatus.SUCCESS) upiId else null, DEMO_ACCOUNT)
        )
    }

    override suspend fun listTransactions(): Result<List<TxEntity>> = Result.success(emptyList())

    override suspend fun fetchBill(category: String, customerId: String): Result<Bill> {
        if (customerId.isBlank()) return Result.failure(IllegalArgumentException("Enter customer / consumer ID"))
        val amount = ((customerId.hashCode() and 0x7fffffff) % 900 + 100) * 100L
        val due = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(clock() + 7L * 86_400_000L))
        return Result.success(Bill("$category biller (DEMO)", customerId, "Demo Customer", due, amount, true))
    }
}
