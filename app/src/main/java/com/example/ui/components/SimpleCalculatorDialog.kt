package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.PersianNumberFormatter

/**
 * ماشین حساب ساده و کاربردی برای ورود و محاسبه مبالغ هزینه‌ها (+, -, *, /)
 * نتیجه محاسبه پس از تایید مستقیماً در فیلد مبلغ هزینه قرار می‌گیرد.
 */
@Composable
fun SimpleCalculatorDialog(
    initialValue: Long = 0L,
    isPersian: Boolean = true,
    title: String = if (isPersian) "ماشین حساب محاسبه مبلغ هزینه" else "Expense Amount Calculator",
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var expression by remember { mutableStateOf(if (initialValue > 0) initialValue.toString() else "") }
    var resultPreview by remember { mutableStateOf(initialValue) }

    fun evaluateExpression(expr: String): Long {
        if (expr.isBlank()) return 0L
        val clean = expr.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
        return try {
            // ساده‌ترین الگوریتم ارزیابی عبارت جبری با رعایت اولویت ضرب/تقسیم و جمع/تفریق
            val tokens = mutableListOf<String>()
            var currentNum = StringBuilder()
            for (c in clean) {
                if (c in "+-*/") {
                    if (currentNum.isNotEmpty()) {
                        tokens.add(currentNum.toString())
                        currentNum = StringBuilder()
                    }
                    tokens.add(c.toString())
                } else if (c.isDigit() || c == '.') {
                    currentNum.append(c)
                }
            }
            if (currentNum.isNotEmpty()) {
                tokens.add(currentNum.toString())
            }

            if (tokens.isEmpty()) return 0L

            // مرحله اول: اعمال ضرب و تقسیم
            val intermediate = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                if (token == "*" || token == "/") {
                    val prev = intermediate.removeAt(intermediate.lastIndex).toDoubleOrNull() ?: 0.0
                    val next = if (i + 1 < tokens.size) tokens[i + 1].toDoubleOrNull() ?: 1.0 else 1.0
                    val res = if (token == "*") prev * next else (if (next != 0.0) prev / next else prev)
                    intermediate.add(res.toString())
                    i += 2
                } else {
                    intermediate.add(token)
                    i++
                }
            }

            // مرحله دوم: اعمال جمع و تفریق
            var total = intermediate.firstOrNull()?.toDoubleOrNull() ?: 0.0
            var j = 1
            while (j < intermediate.size) {
                val op = intermediate[j]
                val nextVal = if (j + 1 < intermediate.size) intermediate[j + 1].toDoubleOrNull() ?: 0.0 else 0.0
                if (op == "+") total += nextVal
                else if (op == "-") total -= nextVal
                j += 2
            }
            total.toLong().coerceAtLeast(0L)
        } catch (e: Exception) {
            0L
        }
    }

    LaunchedEffect(expression) {
        resultPreview = evaluateExpression(expression)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // صفحه نمایش فرمول و نتیجه
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (expression.isBlank()) "0" else expression,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "= ${PersianNumberFormatter.formatAmount(resultPreview)} تومان",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // شبکه کلیدهای ماشین حساب
                val keys = listOf(
                    listOf("C", "⌫", "÷", "×"),
                    listOf("7", "8", "9", "−"),
                    listOf("4", "5", "6", "+"),
                    listOf("1", "2", "3", "000"),
                    listOf("0", "=")
                )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        row.forEach { key ->
                            val isOperator = key in listOf("÷", "×", "−", "+", "=")
                            val isAction = key in listOf("C", "⌫")
                            val weight = if (key == "0") 2f else if (key == "=") 2f else 1f

                            Surface(
                                modifier = Modifier
                                    .weight(weight)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        when (key) {
                                            "C" -> expression = ""
                                            "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                            "=" -> expression = resultPreview.toString()
                                            "÷" -> if (expression.isNotEmpty() && expression.last() !in "+−×÷") expression += " ÷ "
                                            "×" -> if (expression.isNotEmpty() && expression.last() !in "+−×÷") expression += " × "
                                            "−" -> if (expression.isNotEmpty() && expression.last() !in "+−×÷") expression += " − "
                                            "+" -> if (expression.isNotEmpty() && expression.last() !in "+−×÷") expression += " + "
                                            else -> expression += key
                                        }
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isOperator -> MaterialTheme.colorScheme.primaryContainer
                                    isAction -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isOperator) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (key == "⌫") {
                                        Icon(Icons.Default.Backspace, contentDescription = "Backspace", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = if (isOperator) 16.sp else 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                isOperator -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isAction -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmt = if (resultPreview > 0) resultPreview else evaluateExpression(expression)
                    onConfirm(finalAmt)
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isPersian) "درج مبلغ (${PersianNumberFormatter.formatAmount(resultPreview)})" else "Insert Amount", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "انصراف" else "Cancel")
            }
        }
    )
}
