package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.BankAccount
import com.example.util.IranianBankHelper
import com.example.util.IranianBankInfo

@Composable
fun IranianBankCard(
    account: BankAccount,
    modifier: Modifier = Modifier,
    clinicName: String = "",
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    showActions: Boolean = true,
    onShowTransactions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val detectedBank = remember(account.cardNumber, account.iban) {
        IranianBankHelper.detectBank(account.cardNumber) ?: IranianBankHelper.detectBankFromIban(account.iban)
    }

    val gradientBrush = remember(detectedBank) {
        if (detectedBank != null) {
            Brush.linearGradient(
                listOf(
                    detectedBank.primaryColor,
                    detectedBank.secondaryColor,
                    detectedBank.primaryColor.copy(alpha = 0.9f)
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    Color(0xFF1E3C72),
                    Color(0xFF2A5298),
                    Color(0xFF1E3C72)
                )
            )
        }
    }

    val formattedCardNumber = remember(account.cardNumber) {
        IranianBankHelper.formatCardNumber(account.cardNumber).ifBlank {
            account.cardNumber.ifBlank { "•••• - •••• - •••• - ••••" }
        }
    }

    val displayBankName = remember(detectedBank, account.bankName) {
        if (account.bankName.isNotBlank()) account.bankName
        else detectedBank?.nameFa ?: "کارت بانکی"
    }

    val displayBankEn = remember(detectedBank) {
        detectedBank?.nameEn ?: "Debit / Credit Card"
    }

    val accentGold = detectedBank?.accentColor ?: Color(0xFFFFD700)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(18.dp)
        ) {
            // Subtle decorative background circles
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            )
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-30).dp, y = 30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Bank Name, Emblem, Edit Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Bank Emblem Badge
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val resId = if (!detectedBank?.logoResName.isNullOrEmpty()) {
                                context.resources.getIdentifier(detectedBank?.logoResName, "drawable", context.packageName)
                            } else 0
                            
                            if (resId != 0) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = resId),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else if (!detectedBank?.shortCode.isNullOrEmpty()) {
                                Text(
                                    text = detectedBank?.shortCode ?: "",
                                    color = accentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = accentGold,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = displayBankName,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = displayBankEn,
                                fontSize = 11.sp,
                                color = accentGold,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onEdit != null) {
                            IconButton(
                                onClick = onEdit,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Card",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (onDelete != null) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Card",
                                    tint = Color(0xFFFF8A80),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Middle Row: EMV Chip + Contactless Wave + Balance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // EMV Gold Chip Representation
                        Box(
                            modifier = Modifier
                                .size(width = 42.dp, height = 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFFD54F), Color(0xFFFFB300), Color(0xFFFFD54F))
                                    )
                                )
                                .border(1.dp, Color(0xFFC67D0A), RoundedCornerShape(6.dp))
                        ) {
                            // Chip lines
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                                    .align(Alignment.Center)
                                    .background(Color(0xFF8D6E63))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .align(Alignment.Center)
                                    .background(Color(0xFF8D6E63))
                            )
                        }

                        // Contactless Waves
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Balance Display Tag (Masked by default with eye icon toggle)
                    // + دکمه «گردش حساب» کنار کادر موجودی (درخواست: نمایش گردش حساب کنار موجودی)
                    var isBalanceVisible by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable { isBalanceVisible = !isBalanceVisible },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "موجودی:",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                val formattedBal = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).format(account.balance)
                                Text(
                                    text = if (isBalanceVisible) "$formattedBal تومان" else "•••••• تومان",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF69F0AE)
                                )
                                Icon(
                                    imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Balance",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        if (onShowTransactions != null) {
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { onShowTransactions() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "گردش حساب",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 16-Digit Card Number (LTR Monospace, Click to copy)
                Surface(
                    color = Color.Black.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val clean = IranianBankHelper.cleanDigits(account.cardNumber)
                            if (clean.isNotEmpty()) {
                                copyToClipboard(context, clean, "شماره کارت کپی شد")
                            }
                        }
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formattedCardNumber,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                letterSpacing = 2.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy Card",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // CVV2 & Expiry Date Row (With Masking Toggle)
                var showSensitiveInfo by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (account.cvv2.isNotBlank()) {
                            Column {
                                Text(text = "CVV2", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(
                                    text = if (showSensitiveInfo) account.cvv2 else "•••",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }
                        }

                        val expText = remember(account.expireMonth, account.expireYear) {
                            if (account.expireMonth.isNotBlank() && account.expireYear.isNotBlank()) {
                                "${account.expireMonth.padStart(2, '0')} / ${account.expireYear.takeLast(2)}"
                            } else if (account.expireMonth.isNotBlank() || account.expireYear.isNotBlank()) {
                                "${account.expireMonth} / ${account.expireYear}"
                            } else {
                                ""
                            }
                        }
                        if (expText.isNotBlank()) {
                            Column {
                                Text(text = "انقضا (EXP)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(
                                    text = if (showSensitiveInfo) expText else "•• / ••",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (account.cvv2.isNotBlank() || account.expireMonth.isNotBlank() || account.expireYear.isNotBlank()) {
                        IconButton(
                            onClick = { showSensitiveInfo = !showSensitiveInfo },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showSensitiveInfo) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSensitiveInfo) "مخفی‌سازی اطلاعات حساس" else "نمایش اطلاعات حساس",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Cardholder Name & Account Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "دارنده کارت:",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = account.cardHolder.ifBlank { "نامشخص" },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (account.accountNumber.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "شماره حساب:",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = account.accountNumber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }

                // IBAN (Shaba) Bar if present
                if (account.iban.isNotBlank()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyToClipboard(context, account.iban.trim().uppercase(), "شماره شبا کپی شد")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "شبا: ${account.iban.trim().uppercase()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy IBAN",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Bottom Action Bar: Share Card Image, Share Text
                if (showActions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cleanCard = IranianBankHelper.cleanDigits(account.cardNumber)
                                val shareText = buildString {
                                    append("💳 اطلاعات کارت بانکی ${displayBankName}\n\n")
                                    append("شماره کارت:\n")
                                    if (cleanCard.isNotBlank()) {
                                        append(IranianBankHelper.formatCardNumber(cleanCard))
                                        append("\n")
                                    }
                                    append("نام دارنده:\n")
                                    if (account.cardHolder.isNotBlank()) append("${account.cardHolder}\n")
                                    append("بانک صادرکننده:\n")
                                    append(displayBankName)
                                    if (account.accountNumber.isNotBlank()) append("\nشماره حساب:\n${account.accountNumber}")
                                    if (account.iban.isNotBlank()) append("\nشماره شبا:\n${account.iban.trim().uppercase()}")
                                    append("\nCVV2: ***")
                                    append("\nتاریخ انقضا: **/**")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "ارسال اطلاعات حساب بانکی"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ارسال متن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val bitmap = IranianBankHelper.generateCardImageBitmap(
                                        context = context,
                                        bankInfo = detectedBank,
                                        cardNumber = account.cardNumber,
                                        cardHolder = account.cardHolder,
                                        cvv2 = account.cvv2,
                                        expireMonth = account.expireMonth,
                                        expireYear = account.expireYear,
                                        accountNumber = account.accountNumber,
                                        iban = account.iban,
                                        clinicTitle = clinicName
                                    )
                                    val imageFile = IranianBankHelper.saveCardImageToFile(context, bitmap, "card_${account.id}.png")
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, "💳 کارت بانکی ${displayBankName} - ${account.cardHolder}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری تصویر کارت بانکی"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در تولید عکس کارت: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ارسال عکس کارت", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, message: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Bank Info", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
