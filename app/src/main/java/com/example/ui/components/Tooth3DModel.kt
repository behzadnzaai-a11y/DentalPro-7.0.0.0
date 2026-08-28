package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** دسته‌بندی دقیق نوع دندان بر اساس شماره FDI، برای رعایت شکل تشریحی واقعی هر دندان در شماتیک */
fun toothTypeCategory(toothInt: Int): String {
    val quadrant = toothInt / 10
    val position = toothInt % 10
    return when {
        position in 1..2 -> "INCISOR"
        position == 3 -> "CANINE"
        position in 4..5 -> if (quadrant in 1..4) "PREMOLAR" else "MOLAR" // دندان‌های شیری پرمولار ندارند
        position in 6..8 -> "MOLAR"
        else -> "INCISOR"
    }
}

@Composable
fun CariesClassSchematicDiagram(
    modifier: Modifier = Modifier,
    isAnterior: Boolean,
    toothNumber: Int = if (isAnterior) 11 else 16,
    selectedClass: String,
    onClassSelected: (String) -> Unit
) {
    val toothType = remember(toothNumber) { toothTypeCategory(toothNumber) }
    val allSchematics = remember(isAnterior) {
        if (isAnterior) {
            listOf(
                GvBlackItem("Class I", "L", "کلاس I (پیت لنگوال)", "L - Lingual Pit", GvType.CLASS_1_ANT),
                GvBlackItem("Class III", "F", "کلاس III (بین‌دندانی قدامی)", "F - Interproximal Anterior", GvType.CLASS_3),
                GvBlackItem("Class IV", "F", "کلاس IV (بین‌دندانی + لبه)", "F - Incisal Angle", GvType.CLASS_4),
                GvBlackItem("Class V", "F/L", "کلاس V (طوق دندان)", "F/L - Cervical 1/3", GvType.CLASS_5_ANT),
                GvBlackItem("Class VI", "B/L", "کلاس VI (لبه اینسیزال)", "B/L - Incisal Edge", GvType.CLASS_6_ANT)
            )
        } else {
            listOf(
                GvBlackItem("Class I", "B/L", "کلاس I (شیارهای اکلوزال)", "B/L - Occlusal Fissures", GvType.CLASS_1_POST),
                GvBlackItem("Class II (MOD)", "B/L", "کلاس II (بین‌دندانی خلفی MOD)", "B/L - Proximal Posterior", GvType.CLASS_2_MOD),
                GvBlackItem("Class II (MO)", "B/L", "کلاس II (بین‌دندانی مزیال MO)", "B/L - Mesial Proximal", GvType.CLASS_2_MO),
                GvBlackItem("Class II (DO)", "B/L", "کلاس II (بین‌دندانی دیستال DO)", "B/L - Distal Proximal", GvType.CLASS_2_DO),
                GvBlackItem("Class V", "B/L", "کلاس V (طوق دندان خلفی)", "B/L - Cervical 1/3", GvType.CLASS_5_POST),
                GvBlackItem("Class VI", "B/L", "کلاس VI (نوک کادپ‌ها)", "B/L - Cusp Tips", GvType.CLASS_6_POST)
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "G.V. Black Classification",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = run {
                            val typeLabel = when (toothType) {
                                "INCISOR" -> "اینسیزور"
                                "CANINE" -> "کانین"
                                "PREMOLAR" -> "پرمولار"
                                else -> "مولار"
                            }
                            "دندان $toothNumber ($typeLabel)"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "👆 جهت انتخاب کلاس ترمیمی، روی تصویر آناتومیک دندان کلیک کنید:",
                fontSize = 10.5.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            // Grid of Anatomical Tooth Cards (Matching Photo 1 Grid Layout)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val rows = allSchematics.chunked(2)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            val isSel = selectedClass == item.code
                            GvBlackToothCard(
                                item = item,
                                isSelected = isSel,
                                toothType = toothType,
                                onClick = { onClassSelected(if (isSel) "" else item.code) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

enum class GvType {
    CLASS_1_ANT, CLASS_1_POST, CLASS_2_MOD, CLASS_2_MO, CLASS_2_DO,
    CLASS_3, CLASS_4, CLASS_5_ANT, CLASS_5_POST, CLASS_6_ANT, CLASS_6_POST
}

data class GvBlackItem(
    val code: String,
    val surfaceHeader: String,
    val titleFa: String,
    val subText: String,
    val type: GvType
)

@Composable
fun GvBlackToothCard(
    item: GvBlackItem,
    isSelected: Boolean,
    toothType: String = "INCISOR",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)
    val bgColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF020617)

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Surface Header Code (e.g. L, B/L, F, F/L)
            Text(
                text = item.surfaceHeader,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFE2E8F0)
            )

            // Realistic Anatomical Tooth Drawing Canvas (Matching Photo 1)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF090D16)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f

                    when (item.type) {
                        GvType.CLASS_1_ANT -> drawAnteriorTooth(cx, h, cariousType = "LINGUAL_PIT", toothType = toothType)
                        GvType.CLASS_1_POST -> drawPosteriorTooth(cx, h, cariousType = "OCCLUSAL_PIT", toothType = toothType)
                        GvType.CLASS_2_MOD -> drawPosteriorTooth(cx, h, cariousType = "MOD", toothType = toothType)
                        GvType.CLASS_2_MO -> drawPosteriorTooth(cx, h, cariousType = "MO", toothType = toothType)
                        GvType.CLASS_2_DO -> drawPosteriorTooth(cx, h, cariousType = "DO", toothType = toothType)
                        GvType.CLASS_3 -> drawAnteriorTooth(cx, h, cariousType = "INTERPROXIMAL", toothType = toothType)
                        GvType.CLASS_4 -> drawAnteriorTooth(cx, h, cariousType = "INCISAL_ANGLE", toothType = toothType)
                        GvType.CLASS_5_ANT -> drawAnteriorTooth(cx, h, cariousType = "CERVICAL", toothType = toothType)
                        GvType.CLASS_5_POST -> drawPosteriorTooth(cx, h, cariousType = "CERVICAL", toothType = toothType)
                        GvType.CLASS_6_ANT -> drawAnteriorTooth(cx, h, cariousType = "INCISAL_EDGE", toothType = toothType)
                        GvType.CLASS_6_POST -> drawPosteriorTooth(cx, h, cariousType = "CUSP_TIPS", toothType = toothType)
                    }
                }
            }

            // Bottom Label (e.g., Class I, Class II)
            Text(
                text = item.code,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFFF59E0B) else Color(0xFFF8FAFC)
            )
            Text(
                text = item.titleFa,
                fontSize = 8.5.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun DrawScope.drawAnteriorTooth(cx: Float, h: Float, cariousType: String, toothType: String = "INCISOR") {
    val isCanine = toothType == "CANINE"

    // Root: کانین دارای طویل‌ترین و مخروطی‌ترین ریشه دندانی است
    val rootPath = if (isCanine) {
        Path().apply {
            moveTo(cx - 12f, h * 0.42f)
            quadraticTo(cx - 14f, h * 0.92f, cx - 1f, h * 1.02f)
            quadraticTo(cx, h * 1.04f, cx + 1f, h * 1.02f)
            quadraticTo(cx + 14f, h * 0.92f, cx + 12f, h * 0.42f)
            close()
        }
    } else {
        Path().apply {
            moveTo(cx - 14f, h * 0.42f)
            quadraticTo(cx - 18f, h * 0.85f, cx - 2f, h * 0.95f)
            quadraticTo(cx, h * 0.97f, cx + 2f, h * 0.95f)
            quadraticTo(cx + 18f, h * 0.85f, cx + 14f, h * 0.42f)
            close()
        }
    }
    drawPath(rootPath, color = Color(0xFFE2D9C3), style = Fill)
    drawPath(rootPath, color = Color(0xFF8D7B68), style = Stroke(width = 2f))

    // Crown: کانین دارای یک راس نوک‌تیز مرکزی (Cusp Tip) به‌جای لبه صاف اینسیزال است
    val crownPath = if (isCanine) {
        Path().apply {
            moveTo(cx - 20f, h * 0.42f)
            quadraticTo(cx - 25f, h * 0.24f, cx - 16f, h * 0.11f)
            lineTo(cx, h * 0.03f) // نوک تیز کانین
            lineTo(cx + 16f, h * 0.11f)
            quadraticTo(cx + 25f, h * 0.24f, cx + 20f, h * 0.42f)
            close()
        }
    } else {
        Path().apply {
            moveTo(cx - 22f, h * 0.42f)
            quadraticTo(cx - 26f, h * 0.25f, cx - 18f, h * 0.10f)
            quadraticTo(cx, h * 0.06f, cx + 18f, h * 0.10f)
            quadraticTo(cx + 26f, h * 0.25f, cx + 22f, h * 0.42f)
            close()
        }
    }
    drawPath(crownPath, color = Color(0xFFFFFDF5), style = Fill)
    drawPath(crownPath, color = Color(0xFF78716C), style = Stroke(width = 2.5f))

    // Carious Lesion Dark Spots matching G.V. Black photo
    when (cariousType) {
        "LINGUAL_PIT" -> {
            drawCircle(color = Color(0xFF1C1917), radius = 8f, center = Offset(cx, h * 0.26f))
            drawCircle(color = Color(0xFF78350F), radius = 5f, center = Offset(cx, h * 0.26f))
        }
        "INTERPROXIMAL" -> {
            drawOval(color = Color(0xFF1C1917), topLeft = Offset(cx - 22f, h * 0.24f), size = Size(12f, 16f))
            drawOval(color = Color(0xFF78350F), topLeft = Offset(cx - 20f, h * 0.25f), size = Size(8f, 12f))
        }
        "INCISAL_ANGLE" -> {
            val lesionPath = Path().apply {
                moveTo(cx - 20f, h * 0.10f)
                lineTo(cx - 8f, h * 0.10f)
                lineTo(cx - 20f, h * 0.24f)
                close()
            }
            drawPath(lesionPath, color = Color(0xFF1C1917), style = Fill)
        }
        "CERVICAL" -> {
            drawOval(color = Color(0xFF1C1917), topLeft = Offset(cx - 16f, h * 0.36f), size = Size(32f, 10f))
            drawOval(color = Color(0xFF78350F), topLeft = Offset(cx - 12f, h * 0.37f), size = Size(24f, 7f))
        }
        "INCISAL_EDGE" -> {
            drawRect(color = Color(0xFF1C1917), topLeft = Offset(cx - 12f, h * 0.07f), size = Size(24f, 7f))
        }
    }
}

private fun DrawScope.drawPosteriorTooth(cx: Float, h: Float, cariousType: String, toothType: String = "MOLAR") {
    val isPremolar = toothType == "PREMOLAR"

    if (isPremolar) {
        // پرمولار: معمولاً تک‌ریشه (باریک‌تر از مولار) با تاج دوکاسپی (باکال/لینگوال)
        val rootPath = Path().apply {
            moveTo(cx - 12f, h * 0.44f)
            quadraticTo(cx - 15f, h * 0.88f, cx - 1f, h * 0.98f)
            quadraticTo(cx, h * 1.0f, cx + 1f, h * 0.98f)
            quadraticTo(cx + 15f, h * 0.88f, cx + 12f, h * 0.44f)
            close()
        }
        drawPath(rootPath, color = Color(0xFFE2D9C3), style = Fill)
        drawPath(rootPath, color = Color(0xFF8D7B68), style = Stroke(width = 2f))

        val crownPath = Path().apply {
            moveTo(cx - 19f, h * 0.44f)
            quadraticTo(cx - 22f, h * 0.26f, cx - 13f, h * 0.13f) // کاسپ باکال
            quadraticTo(cx - 4f, h * 0.20f, cx, h * 0.16f)
            quadraticTo(cx + 4f, h * 0.20f, cx + 13f, h * 0.13f) // کاسپ لینگوال
            quadraticTo(cx + 22f, h * 0.26f, cx + 19f, h * 0.44f)
            close()
        }
        drawPath(crownPath, color = Color(0xFFFFFDF5), style = Fill)
        drawPath(crownPath, color = Color(0xFF78716C), style = Stroke(width = 2.5f))
    } else {
    // مولار: دو (یا سه) ریشه استاندارد و تاج پهن چندکاسپی
    val root1Path = Path().apply {
        moveTo(cx - 20f, h * 0.44f)
        quadraticTo(cx - 28f, h * 0.82f, cx - 14f, h * 0.94f)
        quadraticTo(cx - 8f, h * 0.85f, cx - 6f, h * 0.50f)
        close()
    }
    val root2Path = Path().apply {
        moveTo(cx + 20f, h * 0.44f)
        quadraticTo(cx + 28f, h * 0.82f, cx + 14f, h * 0.94f)
        quadraticTo(cx + 8f, h * 0.85f, cx + 6f, h * 0.50f)
        close()
    }
    drawPath(root1Path, color = Color(0xFFE2D9C3), style = Fill)
    drawPath(root2Path, color = Color(0xFFE2D9C3), style = Fill)
    drawPath(root1Path, color = Color(0xFF8D7B68), style = Stroke(width = 2f))
    drawPath(root2Path, color = Color(0xFF8D7B68), style = Stroke(width = 2f))

    // Crown (Molar Occlusal Cusps)
    val crownPath = Path().apply {
        moveTo(cx - 24f, h * 0.44f)
        quadraticTo(cx - 28f, h * 0.28f, cx - 20f, h * 0.12f)
        quadraticTo(cx - 10f, h * 0.18f, cx, h * 0.10f)
        quadraticTo(cx + 10f, h * 0.18f, cx + 20f, h * 0.12f)
        quadraticTo(cx + 28f, h * 0.28f, cx + 24f, h * 0.44f)
        close()
    }
    drawPath(crownPath, color = Color(0xFFFFFDF5), style = Fill)
    drawPath(crownPath, color = Color(0xFF78716C), style = Stroke(width = 2.5f))
    }


    // Carious Lesion Dark Spots matching G.V. Black photo
    when (cariousType) {
        "OCCLUSAL_PIT" -> {
            drawCircle(color = Color(0xFF1C1917), radius = 9f, center = Offset(cx - 6f, h * 0.22f))
            drawCircle(color = Color(0xFF78350F), radius = 5f, center = Offset(cx - 6f, h * 0.22f))
        }
        "MOD" -> {
            val modPath = Path().apply {
                moveTo(cx - 20f, h * 0.14f)
                lineTo(cx + 20f, h * 0.14f)
                lineTo(cx + 14f, h * 0.28f)
                lineTo(cx - 14f, h * 0.28f)
                close()
            }
            drawPath(modPath, color = Color(0xFF1C1917), style = Fill)
            drawPath(modPath, color = Color(0xFF78350F), style = Stroke(width = 2f))
        }
        "MO" -> {
            val moPath = Path().apply {
                moveTo(cx - 20f, h * 0.14f)
                lineTo(cx, h * 0.14f)
                lineTo(cx - 4f, h * 0.28f)
                lineTo(cx - 16f, h * 0.28f)
                close()
            }
            drawPath(moPath, color = Color(0xFF1C1917), style = Fill)
        }
        "DO" -> {
            val doPath = Path().apply {
                moveTo(cx, h * 0.14f)
                lineTo(cx + 20f, h * 0.14f)
                lineTo(cx + 16f, h * 0.28f)
                lineTo(cx + 4f, h * 0.28f)
                close()
            }
            drawPath(doPath, color = Color(0xFF1C1917), style = Fill)
        }
        "CERVICAL" -> {
            drawOval(color = Color(0xFF1C1917), topLeft = Offset(cx - 18f, h * 0.37f), size = Size(36f, 10f))
            drawOval(color = Color(0xFF78350F), topLeft = Offset(cx - 14f, h * 0.38f), size = Size(28f, 7f))
        }
        "CUSP_TIPS" -> {
            drawCircle(color = Color(0xFF1C1917), radius = 5f, center = Offset(cx - 18f, h * 0.12f))
            drawCircle(color = Color(0xFF1C1917), radius = 5f, center = Offset(cx + 18f, h * 0.12f))
        }
    }
}

