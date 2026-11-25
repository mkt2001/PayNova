package com.paynova.upi

import com.paynova.upi.payment.QrType
import com.paynova.upi.payment.UpiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiParserTest {
    @Test fun staticQr() {
        val q = UpiParser.parse("upi://pay?pa=ravi@okaxis&pn=Ravi%20Kumar&cu=INR")
        assertEquals(QrType.STATIC, q.type)
        assertEquals("ravi@okaxis", q.vpa)
        assertEquals("Ravi Kumar", q.name)
        assertNull(q.amount)
    }
    @Test fun dynamicQrWithAmountAndNote() {
        val q = UpiParser.parse("upi://pay?pa=shop@upi&pn=Shop&am=150.50&tn=Order%2012&tr=TXN1")
        assertEquals(QrType.DYNAMIC, q.type)
        assertEquals("150.50", q.amount)
        assertEquals("Order 12", q.note)
        assertEquals(15050L, UpiParser.toPaise(q.amount))
    }
    @Test fun merchantQr() {
        assertEquals(QrType.MERCHANT, UpiParser.parse("upi://pay?pa=store@icici&pn=Store&mc=5411").type)
    }
    @Test fun personalMc0000IsNotMerchant() {
        assertEquals(QrType.STATIC, UpiParser.parse("upi://pay?pa=a.b@okhdfc&mc=0000").type)
    }
    @Test fun paymentLink() {
        assertEquals(QrType.LINK, UpiParser.parse("https://pay.example.com/p?pa=x@okaxis&pn=X").type)
    }
    @Test fun invalidInputs() {
        assertEquals(QrType.INVALID, UpiParser.parse("hello world").type)
        assertEquals(QrType.INVALID, UpiParser.parse("upi://pay?pn=NoVpa").type)
        assertEquals(QrType.INVALID, UpiParser.parse("upi://pay?pa=bad vpa@x").type)
        assertEquals(QrType.INVALID, UpiParser.parse("upi://pay?pa=a@okaxis&cu=USD").type)
        assertEquals(QrType.INVALID, UpiParser.parse("upi://pay?pa=a@okaxis&am=-5").type)
        assertEquals(QrType.INVALID, UpiParser.parse("upi://pay?pa=a@okaxis&am=999999999").type)
        assertEquals(QrType.INVALID, UpiParser.parse("https://example.com/no-upi").type)
    }
    @Test fun neverInventsData() {
        val q = UpiParser.parse("upi://pay?pa=a@okaxis")
        assertNull(q.name); assertNull(q.amount); assertNull(q.note); assertNull(q.merchantCode)
    }
    @Test fun buildLinkRoundTrips() {
        val link = UpiParser.buildLink("me@okaxis", "My Name", "99.5", "Lunch")
        val q = UpiParser.parse(link)
        assertEquals("me@okaxis", q.vpa); assertEquals("My Name", q.name); assertEquals("99.5", q.amount)
        assertTrue(UpiParser.isValidVpa("name.surname@oksbi"))
    }
}
