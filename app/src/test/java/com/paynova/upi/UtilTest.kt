package com.paynova.upi

import com.paynova.upi.util.Money
import com.paynova.upi.util.Phones
import com.paynova.upi.util.PinGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UtilTest {
    @Test fun amounts() {
        assertEquals(25000L, Money.parsePaise("250"))
        assertEquals(1050L, Money.parsePaise("10.5"))
        assertNull(Money.parsePaise("0"))
        assertNull(Money.parsePaise("abc"))
        assertNull(Money.parsePaise("100001"))
        assertEquals("250", Money.plain(25000)); assertEquals("250.05", Money.plain(25005))
    }
    @Test fun contactSearchNumbers() {
        assertEquals("9876543210", Phones.normalizeIndian("+91 98765-43210"))
        assertEquals("9876543210", Phones.normalizeIndian("09876543210"))
        assertNull(Phones.normalizeIndian("12345"))
        assertNull(Phones.normalizeIndian("5876543210"))
    }
    @Test fun pinLockout() {
        var now = 0L
        val g = PinGuard(maxFails = 3, lockMs = 30_000, clock = { now })
        assertNotNull(g.check { false }); assertNotNull(g.check { false })
        val locked = g.check { false }
        assertNotNull(locked)
        assertNotNull(g.check { true })      // still locked, even with correct PIN
        now = 31_000
        assertNull(g.check { true })
    }
}
