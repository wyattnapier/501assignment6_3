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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.assign6_3.ui.theme.Assign6_3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt

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

                        buffer.size.also { n ->
                            for (i in 0 until n) {
                                phase += phaseIncrement
                                val s = kotlin.math.sin(phase)
                                buffer[i] = (s * Short.MAX_VALUE).toInt().toShort()
                            }
                        }

                    } else {
                        val recorder = getRecorder() ?: 0
                        recorder.read(buffer, 0, buffer.size)
                    }

                    if (read > 0) {
                        // FAST + CORRECT RMS
                        var sum = 0f
                        for (i in 0 until read) {
                            val v = buffer[i].toFloat()
                            sum += v * v
                        }
                        val rms = kotlin.math.sqrt(sum / read)
                        val db = 20 * log10(rms / 32768f + 1e-6f)

                        dbValue = db.toFloat()
                        isLoud = db > 80
                    }
                }
            }
        }
    }

    // UI --------------------------------------------------------

    val barHeight by animateDpAsState((dbValue * 2).dp)
    val barColor by animateColorAsState(if (isLoud) Color.Red else Color(0xFF00E676))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(25, 25, 35)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sound Level Meter", color = Color.White)

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
        Text("Volume: ${dbValue.toInt()} dB", color = Color.White)

        if (isLoud) {
            Text("⚠️ Loud Environment!", color = Color.Red)
        }

        Spacer(Modifier.height(40.dp))
        Row {
            Button(onClick = start, enabled = !isRecording.value) { Text("Start") }
            Spacer(Modifier.width(20.dp))
            Button(onClick = stop, enabled = isRecording.value) { Text("Stop") }
        }
    }
}
