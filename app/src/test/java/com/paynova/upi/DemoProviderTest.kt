package com.paynova.upi

import com.paynova.upi.data.TxStatus
import com.paynova.upi.payment.DemoPaymentProvider
import com.paynova.upi.payment.PaymentRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoProviderTest {
    private fun req(paise: Long) = PaymentRequest("k-$paise", "a@okaxis", "A", paise, "", "UPI_ID", "Others")

    @Test fun successOnlyAfterProviderConfirms() = runBlocking {
        var now = 1_000L
        DemoPaymentProvider.clock = { now }
        val h = DemoPaymentProvider.initiate(req(50_000)).getOrThrow()
        assertEquals(TxStatus.PENDING, h.status)                       // never success at initiation
        assertEquals(TxStatus.PENDING, DemoPaymentProvider.status(h.providerRef).getOrThrow().status)
        now += 2_500
        assertEquals(TxStatus.SUCCESS, DemoPaymentProvider.status(h.providerRef).getOrThrow().status)
    }
    @Test fun failedAmount() = runBlocking {
        var now = 0L
        DemoPaymentProvider.clock = { now }
        val h = DemoPaymentProvider.initiate(req(1_300)).getOrThrow()
        now += 3_000
        assertEquals(TxStatus.FAILED, DemoPaymentProvider.status(h.providerRef).getOrThrow().status)
    }
    @Test fun pendingAmountStaysPending() = runBlocking {
        var now = 0L
        DemoPaymentProvider.clock = { now }
        val h = DemoPaymentProvider.initiate(req(1_700)).getOrThrow()
        now += 10_000
        assertEquals(TxStatus.PENDING, DemoPaymentProvider.status(h.providerRef).getOrThrow().status)
        now += 25_000
        assertEquals(TxStatus.SUCCESS, DemoPaymentProvider.status(h.providerRef).getOrThrow().status)
    }
    @Test fun resolutionNeverClaimsVerified() = runBlocking {
        assertTrue(!DemoPaymentProvider.resolveVpa("a@okaxis", null).getOrThrow().verified)
        assertTrue(DemoPaymentProvider.resolveMobile("123", null).isFailure)
    }
}
