package com.hazhanhasani.songbird

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF081825)
val InkLight = Color(0xFF122C38)
val Mint = Color(0xFF50E4C3)
val Muted = Color(0xFFA1B8C3)

@Composable
fun SongbirdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Ink,
            surface = Ink,
            onSurface = Color.White,
            background = Ink,
            secondary = Muted
        ),
        content = content
    )
}

@Composable
fun WelcomeScreen(
    address: String,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    error: String?
) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Ink, InkLight, Color(0xFF104F52)))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()
                .padding(horizontal = 28.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("SONGBIRD", letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = Mint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    color = Mint.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text(
                        "PRIVATE • REALTIME • YOURS",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mint
                    )
                }
                Text(
                    "دنیای گفتگو،\nکاملاً در اختیار تو.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 47.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    "با سرور اختصاصی خودت ارتباط بگیر؛ پیام‌ها، گروه‌ها و تماس‌ها در یک فضای شخصی.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                    lineHeight = 26.sp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("آدرس سرور امن (HTTPS)", color = Color.White, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("chat.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mint,
                        unfocusedBorderColor = Muted.copy(alpha = 0.45f)
                    )
                )
                if (error != null) {
                    Text(error, color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("اتصال به پیام‌رسان", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null)
                }
                Text(
                    "دامنه سرور باید گواهی SSL معتبر داشته باشد.",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BrowserHeader(
    origin: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onUpdates: () -> Unit
) {
    Surface(color = Ink, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Songbird", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    origin.removePrefix("https://"),
                    color = Mint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "بارگذاری مجدد", tint = Color.White)
            }
            IconButton(onClick = onUpdates) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = "بررسی آپدیت", tint = Mint)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "تنظیمات سرور", tint = Color.White)
            }
        }
    }
}

@Composable
fun ConnectionError(message: String, onRetry: () -> Unit, onServerChange: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = Mint, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("ارتباط با سرور برقرار نشد", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text("تلاش مجدد") }
        TextButton(onClick = onServerChange) { Text("تغییر آدرس سرور") }
    }
}
