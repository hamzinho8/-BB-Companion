package com.hamza.blackberrybridge.audio

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamza.blackberrybridge.state.BridgeStateManager
import java.lang.ref.WeakReference

class FindPhoneActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        SoundManager.activeActivity = WeakReference(this)

        setContent {
            val isRinging by BridgeStateManager.isAlarmRinging.collectAsState()

            // Close automatically if stopped externally
            if (!isRinging) {
                DisposableEffect(Unit) {
                    finish()
                    onDispose { }
                }
            }

            FindPhoneScreen(
                onDismiss = {
                    SoundManager.stopFindPhone(this, notifyBlackBerry = true)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (SoundManager.activeActivity?.get() == this) {
            SoundManager.activeActivity = null
        }
    }
}

@Composable
fun FindPhoneScreen(onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E0A0A),
                        Color(0xFF0F0B10),
                        Color(0xFF0A0A14)
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulse Radar Visual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(260.dp)
            ) {
                // Expanding Canvas Rings
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFFFF3B30).copy(alpha = ringAlpha),
                        radius = (size.minDimension / 2) * pulseScale,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFF9500).copy(alpha = (ringAlpha * 0.7f)),
                        radius = (size.minDimension / 2.6f) * pulseScale,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Inner Glow Core
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF3B30), Color(0xFF8B0000))
                            )
                        )
                        .border(3.dp, Color(0xFFFF8080), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🚨",
                        fontSize = 54.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "TÉLÉPHONE LOCALISÉ",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Votre BlackBerry fait sonner cet appareil à volume maximal pour vous aider à le retrouver.",
                color = Color(0xFFCCCCCC),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Big Stop Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("stop_alarm_button")
                    .border(2.dp, Color(0xFF00E676), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Text(
                    text = "ARRÊTER L'ALARME",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
