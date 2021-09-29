package com.cristianronda.speechtotext

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.experimental.and

//
class MainActivity : AppCompatActivity() {
    private var ivMic: ImageView? = null
    private var tvSpeechToText: TextView? = null
    private val idIntent = 1

//    variables to record
    private val RECORDER_SAMPLERATE = 8000
    private val RECORDER_CHANNELS: Int = AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING: Int = AudioFormat.ENCODING_PCM_16BIT
    private var recorder: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false
    private val mSampleRates = intArrayOf(8000, 11025, 22050, 44100)
    var bufferSize = 0
    var BufferElements2Rec = 1024 // want to play 2048 (2K) since 2 bytes we use only 1024
    var BytesPerElement = 2 // 2 bytes in 16bit format
//    end variables to record


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        initialization thread
        initializationThreadRecord()
//        end initialization
        ivMic = findViewById(R.id.iv_mic)
        tvSpeechToText = findViewById(R.id.tv_speech_to_text)
        ivMic!!.setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault()
            )
            intent.putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR")
            intent.putExtra("android.speech.extra.GET_AUDIO", true)
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to text")
            try {
                if(null==recorder){
                    initializationThreadRecord()
                }
                startRecording()
                startActivityForResult(intent, idIntent)
            } catch (e: Exception) {
                Toast
                    .makeText(
                        this@MainActivity, " " + e.message,
                        Toast.LENGTH_SHORT
                    )
                    .show()
            }
        }

    }


    override fun onActivityResult(
        requestCode: Int, resultCode: Int,
        @Nullable data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == idIntent) {
            stopRecording()
            if (resultCode == RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS
                )
                tvSpeechToText?.text = Objects.requireNonNull(result)!![0]
            }
        }
    }

//    I think here is the problem because I am doing logic AND with bytes
    private fun short2byte(sData: ShortArray): ByteArray {
        val shortArraySize = sData.size
        val bytes = ByteArray(shortArraySize * 2)
        for (i in 0 until shortArraySize) {
            bytes[i * 2] = (sData[i].toByte() and 0x00FF.toByte())
            bytes[i * 2 + 1] = (sData[i].toByte() and 0x00FF.toByte())
            sData[i] = 0
        }
        return bytes
    }

    private fun findAudioRecord(): AudioRecord? {
        for (rate in mSampleRates) {
            for (audioFormat in shortArrayOf(
                AudioFormat.ENCODING_PCM_8BIT.toShort(),
                AudioFormat.ENCODING_PCM_16BIT.toShort()
            )) {
                for (channelConfig in shortArrayOf(
                    AudioFormat.CHANNEL_IN_MONO.toShort(),
                    AudioFormat.CHANNEL_IN_STEREO.toShort()
                )) {
                    try {
                        Log.d(
                            "Mic2", "Attempting rate " + rate
                                    + "Hz, bits: " + audioFormat
                                    + ", channel: " + channelConfig
                        )
                        bufferSize = AudioRecord.getMinBufferSize(
                            rate,
                            channelConfig.toInt(), audioFormat.toInt()
                        )
                        return AudioRecord(
                            MediaRecorder.AudioSource.DEFAULT, rate,
                            channelConfig.toInt(), audioFormat.toInt(), bufferSize
                        )
                    } catch (e: java.lang.Exception) {
                        Log.e("TAG", rate.toString() + "Exception, keep trying.", e)
                    }
                }
            }
        }
        return null
    }

    private fun initializationThreadRecord() {
        bufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLERATE,
            RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING
        )


        recorder = findAudioRecord()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                1234
            )
        }
    }

    private fun startRecording() {
        recorder!!.startRecording()
        isRecording = true
        recordingThread = Thread({ writeAudioDataToFile() }, "AudioRecorder Thread")
        recordingThread!!.start()
    }


    private fun writeAudioDataToFile() {
        val filePath = "/storage/emulated/0/test.pcm"
        val file = File(filePath)

        if (!file.exists()) {
            try {
                file.createNewFile()
            } catch (exp: Exception) {
                exp.printStackTrace()
                Log.e("logmessage", "cannot create file")
            }
        }
        val sData = ShortArray(BufferElements2Rec)
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(filePath)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        while (isRecording) {
            recorder!!.read(sData, 0, BufferElements2Rec)
            println("Short writing to file$sData in $filePath")
            try {
                val bData: ByteArray = short2byte(sData)
                os?.write(bData, 0, BufferElements2Rec * BytesPerElement)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        try {
            os?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        if (null != recorder) {
            isRecording = false
            recorder!!.stop()
            recorder!!.release()
            recorder = null
            recordingThread = null
        }
    }
}

