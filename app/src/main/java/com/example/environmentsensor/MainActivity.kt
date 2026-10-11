package com.example.environmentsensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.time.Duration.Companion.seconds
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Environment Sensor (Jetpack Compose)
 *  - Light sensor          (Environment) -> langit di bagian ATAS berubah:
 *                                            malam (bulan + bintang), sore (matahari jingga turun),
 *                                            mendung (awan), cerah (matahari)
 *  - Magnetic field sensor (Position)    -> kompas di bagian BAWAH berputar ke utara
 *
 * Lifecycle: registerListener di onResume, unregisterListener di onPause.
 */
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var magnetSensor: Sensor? = null

    // State yang dibaca UI
    private var lux by mutableFloatStateOf(0f)
    private var mx by mutableFloatStateOf(0f)
    private var my by mutableFloatStateOf(0f)
    private var mz by mutableFloatStateOf(0f)
    private var status by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val missing = mutableListOf<String>()
        if (lightSensor == null) missing.add("Light")
        if (magnetSensor == null) missing.add("Magnetic Field")
        status = if (missing.isEmpty()) "Sensor Light & Magnetic Field aktif"
        else "Sensor tidak tersedia: ${missing.joinToString()}"

        setContent {
            EnvironmentSensorScreen(lux = lux, mx = mx, my = my, mz = mz, status = status)
        }
    }

    // ---------- LIFECYCLE SENSOR ----------

    override fun onResume() {
        super.onResume()
        lightSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // ---------- CALLBACK SENSOR ----------

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> lux = event.values[0]
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Low-pass filter supaya jarum kompas tidak bergetar
                mx = mx * 0.9f + event.values[0] * 0.1f
                my = my * 0.9f + event.values[1] * 0.1f
                mz = mz * 0.9f + event.values[2] * 0.1f
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// =====================================================================
//  UI
// =====================================================================

@Composable
fun EnvironmentSensorScreen(lux: Float, mx: Float, my: Float, mz: Float, status: String) {
    // 0 = malam, 1 = sore/senja, 2 = mendung/redup, 3 = cerah
    val level = when {
        lux < 20f -> 0
        lux < 150f -> 1
        lux < 500f -> 2
        else -> 3
    }

    val skyTops = listOf(Color(0xFF0B1026), Color(0xFF3F2B6B), Color(0xFF546E7A), Color(0xFF1E88E5))
    val skyBottoms = listOf(Color(0xFF1B2A5C), Color(0xFFFF8A50), Color(0xFF90A4AE), Color(0xFF81D4FA))
    val cardBgs = listOf(Color(0xFF11162B), Color(0xFFFFF3E8), Color(0xFFF5F7FA), Color(0xFFF5F7FA))
    val dialBgs = listOf(Color(0xFF1F2745), Color(0xFFFFE0CC), Color(0xFFE3EAF2), Color(0xFFE3EAF2))
    val cardTexts = listOf(Color.White, Color(0xFF3E2723), Color(0xFF212121), Color(0xFF212121))
    val conditions = listOf("Malam", "Sore / Senja", "Mendung / Redup", "Cerah")

    val skyTop by animateColorAsState(skyTops[level], tween(1200), label = "skyTop")
    val skyBottom by animateColorAsState(skyBottoms[level], tween(1200), label = "skyBottom")
    val cardBg by animateColorAsState(cardBgs[level], tween(1200), label = "cardBg")
    val dialBg by animateColorAsState(dialBgs[level], tween(1200), label = "dialBg")
    val cardText by animateColorAsState(cardTexts[level], tween(1200), label = "cardText")

    val nightA by animateFloatAsState(if (level == 0) 1f else 0f, tween(1200), label = "night")
    val starA by animateFloatAsState(
        when (level) { 0 -> 1f; 1 -> 0.25f; else -> 0f }, tween(1200), label = "star"
    )
    val sunA by animateFloatAsState(
        when (level) { 0 -> 0f; 2 -> 0.35f; else -> 1f }, tween(1200), label = "sun"
    )
    val cloudA by animateFloatAsState(
        when (level) { 0 -> 0.15f; 1 -> 0.5f; 2 -> 0.9f; else -> 0.45f }, tween(1200), label = "cloud"
    )
    // Sore: matahari turun mendekati horizon dan berwarna jingga
    val sunY by animateFloatAsState(if (level == 1) 0.58f else 0.30f, tween(1200), label = "sunY")
    val sunColor by animateColorAsState(
        if (level == 1) Color(0xFFFF7043) else Color(0xFFFFEB3B), tween(1200), label = "sunColor"
    )
    val cloudColor by animateColorAsState(
        if (level == 1) Color(0xFFFFCCBC) else Color(0xFFECEFF1), tween(1200), label = "cloudColor"
    )

    // Animasi terus-menerus: sinar matahari berputar, awan bergeser, bintang berkedip
    val infinite = rememberInfiniteTransition(label = "infinite")
    val rayRot by infinite.animateFloat(
        0f, 360f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "ray"
    )
    val cloudShift by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "cloudShift"
    )
    val twinkle by infinite.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "twinkle"
    )

    // Jam realtime
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1.seconds)
        }
    }
    val date = Date(nowMs)
    val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    val ss = SimpleDateFormat("ss", Locale.getDefault()).format(date)
    val dateText = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("id-ID")).format(date)
    val hour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }

    // Data dekorasi langit
    val stars = remember {
        val r = Random(7)
        List(45) { Triple(r.nextFloat(), r.nextFloat() * 0.8f, r.nextFloat() * 1.5f + 1f) }
    }
    val clouds = remember {
        listOf(
            Triple(0.10f, 0.62f, 1.2f),
            Triple(0.45f, 0.80f, 0.9f),
            Triple(0.75f, 0.55f, 1.4f),
            Triple(0.30f, 0.45f, 0.8f)
        )
    }

    // Kompas dari Magnetic Field (HP diletakkan datar)
    val heading = ((Math.toDegrees(atan2(-mx, my).toDouble()) + 360.0) % 360.0).toFloat()
    val strength = sqrt(mx * mx + my * my + mz * mz)
    val directions = listOf(
        "Utara", "Timur Laut", "Timur", "Tenggara",
        "Selatan", "Barat Daya", "Barat", "Barat Laut"
    )
    val directionName = directions[((heading + 22.5f) / 45f).toInt() % 8]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cardBg)
    ) {
        // ===================== ATAS: WAKTU & KONDISI HARI =====================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(skyTop, skyBottom)))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Bintang (malam)
                if (starA > 0.01f) {
                    stars.forEach { (sx, sy, sr) ->
                        drawCircle(
                            color = Color.White.copy(alpha = starA * twinkle),
                            radius = sr.dp.toPx(),
                            center = Offset(sx * w, sy * h)
                        )
                    }
                }

                val c = Offset(w * 0.78f, h * sunY)

                // Bulan (malam)
                if (nightA > 0.01f) {
                    val glowR = 90.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.35f * nightA), Color.Transparent),
                            center = c, radius = glowR
                        ),
                        radius = glowR, center = c
                    )
                    drawCircle(Color(0xFFF5F5DC).copy(alpha = nightA), 32.dp.toPx(), c)
                    drawCircle(
                        Color(0xFFD7D7C0).copy(alpha = nightA), 6.dp.toPx(),
                        c + Offset(-10.dp.toPx(), -6.dp.toPx())
                    )
                    drawCircle(
                        Color(0xFFD7D7C0).copy(alpha = nightA), 4.dp.toPx(),
                        c + Offset(10.dp.toPx(), 8.dp.toPx())
                    )
                }

                // Matahari (cerah penuh, redup saat mendung)
                if (sunA > 0.01f) {
                    val glowR = 130.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(sunColor.copy(alpha = 0.6f * sunA), Color.Transparent),
                            center = c, radius = glowR
                        ),
                        radius = glowR, center = c
                    )
                    for (i in 0 until 12) {
                        val rad = (rayRot + i * 30f) * PI / 180.0
                        val inner = 46.dp.toPx()
                        val outer = 66.dp.toPx()
                        drawLine(
                            color = sunColor.copy(alpha = sunA),
                            start = c + Offset((cos(rad) * inner).toFloat(), (sin(rad) * inner).toFloat()),
                            end = c + Offset((cos(rad) * outer).toFloat(), (sin(rad) * outer).toFloat()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(sunColor.copy(alpha = sunA), 34.dp.toPx(), c)
                }

                // Awan bergeser
                clouds.forEach { (fx, fy, s) ->
                    val x = ((fx + cloudShift) % 1f) * (w + 300f) - 150f
                    drawCloud(
                        center = Offset(x, h * fy),
                        scale = s * density,
                        color = cloudColor.copy(alpha = cloudA)
                    )
                }
            }

            // Teks waktu
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 24.dp, top = 16.dp)
            ) {
                Text(greeting, color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(hm, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                    Text(
                        ss, color = Color.White.copy(alpha = 0.8f), fontSize = 26.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 12.dp)
                    )
                }
                Text(dateText, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }

            // Chip kondisi hari
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "${conditions[level]}  •  %.0f lux".format(lux),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
            }
        }

        // ===================== BAWAH: KOMPAS =====================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("▼", color = Color(0xFFE53935), fontSize = 22.sp)
            CompassDial(
                heading = heading,
                dialColor = dialBg,
                textColor = cardText
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "%.0f°  %s".format(heading, directionName),
                color = cardText, fontSize = 26.sp, fontWeight = FontWeight.Bold
            )
            Text(
                "Medan magnet: %.1f µT".format(strength),
                color = cardText.copy(alpha = 0.7f), fontSize = 13.sp
            )
            Text(status, color = cardText.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
fun CompassDial(heading: Float, dialColor: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .graphicsLayer { rotationZ = -heading }
            .clip(CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f
            drawCircle(dialColor, r, Offset(cx, cy))

            // Garis skala tiap 5 derajat
            for (i in 0 until 72) {
                val rad = (i * 5.0 - 90.0) * PI / 180.0
                val major = i % 6 == 0
                val len = if (major) 14.dp.toPx() else 7.dp.toPx()
                val outer = r - 6.dp.toPx()
                val inner = outer - len
                drawLine(
                    color = textColor.copy(alpha = if (major) 1f else 0.45f),
                    start = Offset(cx + (cos(rad) * inner).toFloat(), cy + (sin(rad) * inner).toFloat()),
                    end = Offset(cx + (cos(rad) * outer).toFloat(), cy + (sin(rad) * outer).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // Jarum: merah ke utara, abu ke selatan
            drawLine(
                Color(0xFFE53935), Offset(cx, cy), Offset(cx, cy - r * 0.45f),
                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
            )
            drawLine(
                textColor.copy(alpha = 0.5f), Offset(cx, cy), Offset(cx, cy + r * 0.45f),
                strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round
            )
            drawCircle(textColor, 6.dp.toPx(), Offset(cx, cy))
        }

        Text(
            "N", color = Color(0xFFE53935), fontSize = 24.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp)
        )
        Text(
            "E", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
        )
        Text(
            "S", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
        )
        Text(
            "W", color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
        )
    }
}

private fun DrawScope.drawCloud(center: Offset, scale: Float, color: Color) {
    drawCircle(color, 28f * scale, Offset(center.x - 30f * scale, center.y + 6f * scale))
    drawCircle(color, 38f * scale, Offset(center.x, center.y - 8f * scale))
    drawCircle(color, 28f * scale, Offset(center.x + 34f * scale, center.y + 6f * scale))
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - 58f * scale, center.y + 4f * scale),
        size = Size(120f * scale, 30f * scale),
        cornerRadius = CornerRadius(15f * scale)
    )
}