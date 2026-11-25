package com.paynova.upi.data

import com.paynova.upi.Graph
import com.paynova.upi.payment.Draft
import com.paynova.upi.payment.PaymentRequest
import com.paynova.upi.payment.ProviderMode
import com.paynova.upi.payment.ProviderStatus
import com.paynova.upi.util.Money
import com.paynova.upi.voice.Announcer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class TxRepository(
    private val dao: TxDao,
    private val s: SettingsStore,
    private val announcer: Announcer
) {
    private val mutex = Mutex()
    val all: Flow<List<TxEntity>> get() = dao.observeAll()
    fun observe(id: String): Flow<TxEntity?> = dao.observe(id)
    suspend fun clearLocal() = dao.deleteAll()

    /**
     * Starts a payment. The same [key] always maps to one transaction (idempotency), so double taps,
     * retries and process restarts cannot create a duplicate payment.
     * The row is created as PENDING; SUCCESS is only ever set from the provider's server status.
     */
    suspend fun pay(d: Draft, key: String): Result<String> = mutex.withLock {
        dao.byKey(key)?.let { return@withLock Result.success(it.id) }
        val r = d.resolved ?: return@withLock Result.failure(IllegalStateException("Recipient not resolved"))
        val provider = Graph.provider
        val req = PaymentRequest(key, r.vpa, r.name, d.amountPaise, d.note, d.method, d.category)
        provider.initiate(req).map { h ->
            val tx = TxEntity(
                id = UUID.randomUUID().toString(),
                counterpartyName = r.name.orEmpty(),
                counterpartyVpa = r.vpa,
                amountPaise = d.amountPaise,
                direction = "SENT",
                status = h.status.name,
                method = d.method,
                note = d.note,
                category = d.category,
                createdAt = System.currentTimeMillis(),
                providerRef = h.providerRef,
                upiTxnId = h.upiTxnId.orEmpty(),
                maskedAccount = h.maskedAccount.orEmpty(),
                demo = provider.mode == ProviderMode.DEMO,
                idempotencyKey = key
            )
            dao.upsert(tx)
            if (h.status == TxStatus.SUCCESS) onSuccess(tx)
            tx.id
        }
    }

    suspend fun refresh(id: String) {
        val tx = dao.get(id) ?: return
        if (tx.status != TxStatus.PENDING.name) return
        Graph.providerFor(tx.demo).status(tx.providerRef).onSuccess { applyStatus(tx, it) }
    }

    suspend fun refreshPending() {
        dao.pending().forEach { refresh(it.id) }
    }

    /** Pulls server-side history (production mode only). */
    suspend fun syncRemote() {
        val p = Graph.provider
        if (p.mode != ProviderMode.PRODUCTION) return
        p.listTransactions().onSuccess { list ->
            for (remote in list) {
                val local = dao.get(remote.id)
                if (local == null) dao.upsert(remote)
                else applyStatus(local, ProviderStatus(TxStatus.valueOf(remote.status), remote.upiTxnId, remote.maskedAccount))
            }
        }
    }

    private suspend fun applyStatus(tx: TxEntity, st: ProviderStatus) {
        if (st.status.name == tx.status) return
        val upd = tx.copy(
            status = st.status.name,
            upiTxnId = st.upiTxnId ?: tx.upiTxnId,
            maskedAccount = st.maskedAccount ?: tx.maskedAccount
        )
        dao.upsert(upd)
        if (st.status == TxStatus.SUCCESS) onSuccess(upd)
    }

    /** Voice + notification only after a verified SUCCESS, and only once per transaction. */
    private fun onSuccess(tx: TxEntity) {
        if (tx.direction != "SENT") return
        if (!s.markAnnounced(tx.id)) return
        announcer.announceSuccess(Money.plain(tx.amountPaise), tx.title())
        com.paynova.upi.data.Notifier.notify(
            Graph.app, "Payment successful", "₹${Money.plain(tx.amountPaise)} paid to ${tx.title()}"
        )
    }
}
