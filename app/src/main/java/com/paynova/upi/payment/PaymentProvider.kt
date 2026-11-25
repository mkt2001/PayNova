package com.paynova.upi.payment

import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.TxStatus

enum class ProviderMode { DEMO, PRODUCTION }

class NotConfiguredException(msg: String = "Payment provider is not configured. This feature requires PSP integration.") :
    Exception(msg)

data class Recipient(
    val name: String?,
    val vpa: String,
    val verified: Boolean,
    val merchantInfo: String? = null
)

data class Draft(
    val method: String,                 // UPI_ID | MOBILE | BANK | QR | MERCHANT | BBPS
    val vpa: String? = null,
    val mobile: String? = null,
    val account: String? = null,
    val ifsc: String? = null,
    val qrName: String? = null,
    val qr: UpiQr? = null,
    val amountPaise: Long = 0L,
    val amountLocked: Boolean = false,
    val note: String = "",
    val category: String = "Others",
    val resolved: Recipient? = null
)

data class PaymentRequest(
    val idempotencyKey: String,
    val vpa: String,
    val name: String?,
    val amountPaise: Long,
    val note: String,
    val method: String,
    val category: String
)

data class PaymentHandle(
    val providerRef: String,
    val status: TxStatus,
    val upiTxnId: String?,
    val maskedAccount: String?
)

data class ProviderStatus(val status: TxStatus, val upiTxnId: String?, val maskedAccount: String?)

data class Bill(
    val billerName: String,
    val customerId: String,
    val customerName: String?,
    val dueDate: String?,
    val amountPaise: Long,
    val demo: Boolean
)

/**
 * Everything the app needs from a payment service provider. Implement this interface for a real
 * authorized UPI PSP / BBPS operator (usually via your own backend) - no UI code changes needed.
 *
 * Rules every implementation must follow:
 *  - never collect/log/store the UPI PIN; authentication happens in the PSP's secure UI
 *  - a payment is SUCCESS only when the PSP server says so (see [status])
 */
interface PaymentProvider {
    val mode: ProviderMode
    val supportsUpiLite: Boolean
    suspend fun resolveVpa(vpa: String, qrName: String?): Result<Recipient>
    suspend fun resolveMobile(mobile: String, contactName: String?): Result<Recipient>
    suspend fun resolveBank(account: String, ifsc: String, name: String?): Result<Recipient>
    suspend fun initiate(req: PaymentRequest): Result<PaymentHandle>
    suspend fun status(providerRef: String): Result<ProviderStatus>
    suspend fun listTransactions(): Result<List<TxEntity>>
    suspend fun fetchBill(category: String, customerId: String): Result<Bill>
}
