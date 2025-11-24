package com.example.assign6_3

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.assign6_3.ui.theme.Assign6_3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private var recorder: AudioRecord? = null
    private val DEBUG_FAKE_AUDIO = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1001
            )
        }

        setContent {
            Assign6_3Theme {

                // true source of truth
                val isRecordingState = remember { mutableStateOf(false) }

                SoundMeterScreen(
                    start = { isRecordingState.value = true },
                    stop = { isRecordingState.value = false },
                    getRecorder = { recorder },
                    isRecording = isRecordingState,
                    debugFakeAudio = DEBUG_FAKE_AUDIO
                )

                // handle real recorder start/stop here
                LaunchedEffect(isRecordingState.value) {
                    if (isRecordingState.value) {
                        startAudioRecording(isRecordingState.value)
                    } else {
                        stopAudioRecording()
                    }
                }
            }
        }

    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioRecording(isRecordingState: Boolean) {
        if (isRecordingState) return

        Log.d("AudioRecording", "Starting audio recording")

        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        recorder?.startRecording()

        Log.d("AudioRecording", "Audio recording started: $recorder")
    }

    private fun stopAudioRecording() {
        Log.d("AudioRecording", "Stopping audio recording")
        recorder?.stop()
        recorder?.release()
        recorder = null
    }

    override fun onPause() {
        super.onPause()
        stopAudioRecording()
    }
}

@Composable
fun SoundMeterScreen(
    start: () -> Unit,
    stop: () -> Unit,
    getRecorder: () -> AudioRecord?,
    isRecording: State<Boolean>,
    debugFakeAudio: Boolean
) {
    var dbValue by remember { mutableStateOf(0f) }
    var isLoud by remember { mutableStateOf(false) }

    // Phase MUST persist across iterations → store it in remember
    var phase by remember { mutableStateOf(0.0) }
    val phaseIncrement = 2 * Math.PI * 440 / 44100.0

    LaunchedEffect(isRecording.value) {
        if (isRecording.value) {

            withContext(Dispatchers.Default) {

                val buffer = ShortArray(1024)

                while (isRecording.value) {

                    val read = if (debugFakeAudio) {
                        // Generate random varying amplitude audio
                        buffer.size.also { n ->
                            // Random amplitude between 0.1 and 1.0 changes every buffer
                            val amplitude = Random.nextFloat() * 0.9f + 0.1f

                            for (i in 0 until n) {
                                phase += phaseIncrement
                                // Mix sine wave with some noise
                                val sine = kotlin.math.sin(phase)
                                val noise = Random.nextFloat() * 0.2f - 0.1f
                                val sample = (sine + noise) * amplitude
                                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                            }
                        }

                    } else {
                        val recorder = getRecorder()
                        recorder?.read(buffer, 0, buffer.size) ?: 0
                    }

                    if (read > 0) {
                        // FAST + CORRECT RMS
                        var sum = 0.0
                        for (i in 0 until read) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / read)
                        val dbFS = 20 * log10(rms / 32768.0 + 1e-6)

                        // Convert dBFS to positive dB scale (shift by 60)
                        val db = dbFS + 60

                        dbValue = db.toFloat().coerceIn(0f, 60f)
                        isLoud = db > 50 // Threshold at 50 dB for varying test audio
                    }

                    // Small delay to simulate realistic update rate
                    delay(50)
                }
            }
        }
    }

    // UI --------------------------------------------------------
    // Map dB range (0 to 60) to height range (0 to 250)
    val normalizedHeight = (dbValue / 60 * 250).coerceIn(0f, 250f)
    val barHeight by animateDpAsState(normalizedHeight.dp)
    val barColor by animateColorAsState(if (isLoud) Color.Red else Color(0xFF00E676))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(25, 25, 35)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sound Level Meter", color = Color.White, style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .width(60.dp)
                .height(250.dp)
                .background(Color.DarkGray, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(barHeight.coerceAtMost(250.dp))
                    .background(barColor, RoundedCornerShape(16.dp))
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Volume: ${dbValue.toInt()} dB", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        val warningMessageHeight = 40.dp
        if (isLoud) {
            Text("⚠️ Loud Environment!", color = Color.Red, style = MaterialTheme.typography.titleMedium, modifier = Modifier.height(warningMessageHeight))
        } else {
            Spacer(Modifier.height(warningMessageHeight))
        }

        Row {
            Button(onClick = start, enabled = !isRecording.value) { Text("Start") }
            Spacer(Modifier.width(20.dp))
            Button(onClick = stop, enabled = isRecording.value) { Text("Stop") }
        }
        Text("NOTE: digital values may not align with real-world sound levels due to lack of necessary calibration and conversion rates",
            color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 50.dp, vertical = 20.dp), textAlign = TextAlign.Center)
    }
}