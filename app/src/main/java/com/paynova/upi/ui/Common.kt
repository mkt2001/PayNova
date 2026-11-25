package com.paynova.upi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.title
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Purple = Color(0xFF26002F)
val Violet = Color(0xFF8C35C9)
val Green = Color(0xFF079B55)
val LightPurple = Color(0xFFF7EEFC)
val CardBg = Color(0xFFF8F8FA)
val Ink = Color(0xFF171717)
val Amber = Color(0xFFE08A00)
val Red = Color(0xFFC62828)

@Composable
fun PayNovaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFD7A8F5), onPrimary = Purple, secondary = Violet,
        background = Color(0xFF120016), surface = Color(0xFF1C0A22),
        onBackground = Color(0xFFF2E8F6), onSurface = Color(0xFFF2E8F6),
        surfaceVariant = Color(0xFF2A1531), primaryContainer = Color(0xFF3A1546), onPrimaryContainer = Color(0xFFF2E8F6)
    ) else lightColorScheme(
        primary = Purple, onPrimary = Color.White, secondary = Violet,
        background = Color.White, surface = Color.White, onBackground = Ink, onSurface = Ink,
        surfaceVariant = CardBg, primaryContainer = LightPurple, onPrimaryContainer = Purple
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun Context.activity(): FragmentActivity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    error("No activity")
}

fun rupees(paise: Long): String {
    val r = paise / 100
    val p = paise % 100
    return "₹" + NumberFormat.getInstance(Locale("en", "IN")).format(r) + if (p == 0L) "" else "." + p.toString().padStart(2, '0')
}

fun fmtDateTime(ms: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date(ms))
fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun copyText(ctx: Context, label: String, text: String) {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
    toast(ctx, "$label copied")
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Share"))
}

@Composable
fun rememberOnline(): Boolean {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var online by remember { mutableStateOf(cm.activeNetwork != null) }
    DisposableEffect(Unit) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = cm.activeNetwork != null }
        }
        cm.registerDefaultNetworkCallback(cb)
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return online
}

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val sc = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            else Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        val base = Modifier.weight(1f).fillMaxWidth()
        val m = (if (scroll) base.verticalScroll(sc) else base).padding(horizontal = 16.dp, vertical = 4.dp)
        Column(m, verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = fg, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun Tile(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val base = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
    val m = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(14.dp)
    Column(m, content = content)
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontSize = 16.sp)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp, photo: ImageBitmap? = null) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DemoBanner() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF1D6)).padding(10.dp)) {
        Text(
            "DEMO MODE — simulated payments with fake money. Test amounts: ₹13 fails, ₹17 stays pending 30s.",
            color = Color(0xFF7A4B00), fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun Notice(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp)) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun InfoRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.weight(0.42f))
        Text(
            if (value.isNullOrBlank()) "Not available" else value,
            fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.58f), textAlign = TextAlign.End
        )
    }
}

fun statusColor(status: String): Color = when (status) {
    "SUCCESS" -> Green
    "PENDING" -> Amber
    else -> Red
}

fun statusLabel(status: String): String = when (status) {
    "SUCCESS" -> "Successful"
    "PENDING" -> "Pending"
    "FAILED" -> "Failed"
    "REVERSED" -> "Reversed"
    else -> status
}

@Composable
fun TxRow(t: TxEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(t.title())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title(), fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                fmtDateTime(t.createdAt) + if (t.demo) " · DEMO" else "",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (t.direction == "SENT") "-" else "+"
            Text("$sign${rupees(t.amountPaise)}", fontWeight = FontWeight.SemiBold)
            Text(statusLabel(t.status), fontSize = 12.sp, color = statusColor(t.status))
        }
    }
}
