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
                val isRecordingState = remember { mutableStateOf(false) }

                SoundMeterScreen(
                    startRecording = {
                        startAudioRecording(isRecordingState.value)
                        isRecordingState.value = true
                                     },
                    stopRecording = {
                        isRecordingState.value = false
                        stopAudioRecording() },
                    getRecorder = { recorder!! },
                    isRecording = isRecordingState,
                    debugFakeAudio = DEBUG_FAKE_AUDIO
                )
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
    startRecording: () -> Unit,
    stopRecording: () -> Unit,
    getRecorder: () -> AudioRecord,
    isRecording: State<Boolean>,
    debugFakeAudio: Boolean
) {
    var dbValue by remember { mutableFloatStateOf(0f) }
    var isLoud by remember { mutableStateOf(false) }
    var phase = 0.0
    val phaseIncrement = 2 * Math.PI * 440 / 44100.0

    LaunchedEffect(isRecording.value) {
        if (isRecording.value) {
            startRecording()

            withContext(Dispatchers.Default) {
                val recorder = getRecorder()
                val buffer = ShortArray(1024)

                while (isRecording.value) {
                    val read = if (debugFakeAudio) {
                        // Fake audio generation for testing
                        buffer.size.also {
                            for (i in 0 until it) {
                                phase += phaseIncrement
                                val sample = kotlin.math.sin(phase)
                                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
                            }
                        }
                    } else {
                        // Real microphone
                        recorder.read(buffer, 0, buffer.size)
                    }

                    if (read > 0) {
                        val rms = sqrt(buffer.take(read).map { it * it }.sum().toFloat() / read)
                        val db = 20 * log10(rms / 32768f + 1e-6f)
                        dbValue = db.coerceAtLeast(0f)

                        isLoud = dbValue > 80
                    }

                }
            }
        } else {
            stopRecording()
        }
    }

    val barHeight by animateDpAsState(targetValue = (dbValue * 2).dp)
    val barColor by animateColorAsState(
        if (isLoud) Color.Red else Color(0xFF00E676)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(25, 25, 35)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Sound Level Meter",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(30.dp))

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

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Volume: ${dbValue.toInt()} dB",
            color = Color.White
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isLoud) {
            Text(
                text = "⚠️ Loud Environment!",
                color = Color.Red,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Start / Stop Buttons
        Row {
            Button(onClick = startRecording, enabled = !isRecording.value) {
                Text("Start")
            }
            Spacer(modifier = Modifier.width(20.dp))
            Button(onClick = stopRecording , enabled = isRecording.value) {
                Text("Stop")
            }
        }
    }
}
