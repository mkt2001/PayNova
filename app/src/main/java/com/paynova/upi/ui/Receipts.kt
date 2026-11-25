package com.paynova.upi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.title
import com.paynova.upi.util.Money
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receipts / statements are generated only from stored (provider-verified) transaction records. */
object Receipts {
    private fun dt(ms: Long) = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(ms))

    fun receiptTitle(t: TxEntity): String = when (t.status) {
        "SUCCESS" -> if (t.direction == "RECEIVED") "Payment Received" else "Payment Successful"
        "PENDING" -> "Payment Pending"
        "FAILED" -> "Payment Failed"
        else -> "Payment Reversed"
    }

    fun receiptLines(t: TxEntity): List<String> = listOf(
        "Status: ${receiptTitle(t)}" + if (t.demo) "  (DEMO - fake money)" else "",
        "Amount: Rs. ${Money.plain(t.amountPaise)}",
        "Recipient: ${t.counterpartyName.ifBlank { "Not available" }}",
        "UPI ID / account: ${t.counterpartyVpa}",
        "Date & time: ${dt(t.createdAt)}",
        "Payment method: ${t.method}",
        "Bank account: ${t.maskedAccount.ifBlank { "Not available" }}",
        "UPI transaction ID: ${t.upiTxnId.ifBlank { "Not available" }}",
        "Provider reference: ${t.providerRef}",
        "Note: ${t.note.ifBlank { "-" }}"
    )

    fun receiptPdf(t: TxEntity): ByteArray = render("PayNova Receipt", receiptLines(t))

    fun statementPdf(list: List<TxEntity>): ByteArray {
        val lines = list.map {
            "${dt(it.createdAt)} | ${it.title().take(22)} | ${it.status} | ${if (it.direction == "SENT") "-" else "+"}Rs.${Money.plain(it.amountPaise)}"
        }
        return render("PayNova Statement (${list.size} transactions)", lines.ifEmpty { listOf("No transactions") })
    }

    private fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun csv(list: List<TxEntity>): String {
        val sb = StringBuilder("Date,Time,Name,UPI ID,Amount (INR),Direction,Status,Method,Category,Reference,UPI Txn ID,Note\n")
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val t = SimpleDateFormat("HH:mm:ss", Locale.US)
        for (x in list) {
            sb.append(
                listOf(
                    d.format(Date(x.createdAt)), t.format(Date(x.createdAt)), esc(x.title()), esc(x.counterpartyVpa),
                    Money.plain(x.amountPaise), x.direction, x.status, x.method, x.category, x.providerRef, x.upiTxnId, esc(x.note)
                ).joinToString(",")
            ).append('\n')
        }
        return sb.toString()
    }

    private fun render(title: String, lines: List<String>): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f; isAntiAlias = true; color = android.graphics.Color.BLACK }
        val titlePaint = Paint(paint).apply { textSize = 20f; isFakeBoldText = true; color = android.graphics.Color.rgb(38, 0, 47) }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var c = page.canvas
        var y = 60f
        c.drawText(title, 40f, y, titlePaint)
        y += 34f
        for (l in lines) {
            if (y > 800f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                c = page.canvas
                y = 50f
            }
            c.drawText(l.take(95), 40f, y, paint)
            y += 20f
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun shareFile(ctx: Context, name: String, mime: String, bytes: ByteArray) {
        val f = File(ctx.cacheDir, name)
        f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share"))
    }
}
