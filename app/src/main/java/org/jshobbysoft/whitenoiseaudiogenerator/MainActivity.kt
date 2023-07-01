package org.jshobbysoft.whitenoiseaudiogenerator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jshobbysoft.whitenoiseaudiogenerator.databinding.ActivityMainBinding
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fs: Int = 44100
    private var isPlaying = false
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    private val buffLength = AudioTrack.getMinBufferSize(
        fs,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val trackAttrib = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private val trackFormat = AudioFormat.Builder()
        .setSampleRate(fs)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

    private val noiseTrack = AudioTrack(
        trackAttrib,
        trackFormat,
        buffLength, AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val userFreq = sharedPref.getFloat("frequency", "440".toFloat())
        val userAmpAmp = sharedPref.getFloat("amp_amp", "0".toFloat())
        val userAmpFreq = sharedPref.getFloat("amp_freq", "0".toFloat())
        val userNoisePct = sharedPref.getFloat("noise_pct", "0".toFloat())
        val userNoiseType = sharedPref.getString("noise_type", "White")

        binding.frequencyEdittext.setText(userFreq.toString())
        binding.ampAmpEdittext.setText(userAmpAmp.toString())
        binding.ampFreqEdittext.setText(userAmpFreq.toString())
        binding.noisePctEdittext.setText(userNoisePct.toString())

        val spinner = binding.spinnerNoise
        ArrayAdapter.createFromResource(
            this,
            R.array.noiseChoices,
            android.R.layout.simple_spinner_dropdown_item
        )
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }

        val noiseTypeIndex = arrayOf("White", "Pink", "Brownian").indexOf(userNoiseType)
        binding.spinnerNoise.setSelection(noiseTypeIndex)

        binding.btnPlay.setOnClickListener {
            sharedPref.edit()
                .putFloat("frequency", binding.frequencyEdittext.text.toString().toFloat()).apply()
            sharedPref.edit()
                .putFloat("amp_freq", binding.ampFreqEdittext.text.toString().toFloat()).apply()
            sharedPref.edit().putFloat("amp_amp", binding.ampAmpEdittext.text.toString().toFloat())
                .apply()
            sharedPref.edit()
                .putFloat("noise_pct", binding.noisePctEdittext.text.toString().toFloat()).apply()
            sharedPref.edit().putString("noise_type", binding.spinnerNoise.selectedItem.toString())
                .apply()
            startOrRestartPlaying()
        }

        binding.btnStop.setOnClickListener {
            if (isPlaying) {
                sharedPref.edit()
                    .putFloat("frequency", binding.frequencyEdittext.text.toString().toFloat())
                    .apply()
                sharedPref.edit()
                    .putFloat("amp_freq", binding.ampFreqEdittext.text.toString().toFloat()).apply()
                sharedPref.edit()
                    .putFloat("amp_amp", binding.ampAmpEdittext.text.toString().toFloat()).apply()
                sharedPref.edit()
                    .putFloat("noise_pct", binding.noisePctEdittext.text.toString().toFloat())
                    .apply()
                sharedPref.edit()
                    .putString("noise_type", binding.spinnerNoise.selectedItem.toString()).apply()
                stopPlaying()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        noiseTrack.release()
        scope.cancel()
    }

    //  https://rajat-r-bapuri.github.io/DSP-Lab-Android-Demos/Android_Demos/kotlin_implementations/Sine_Wave_Demo1/
    //  https://stackoverflow.com/questions/26963342/generating-colors-of-noise-in-java
    private suspend fun playback(
        frequencyTone: Float,
        amplitudeDelta: Float,
        amplitudeFrequency: Float,
        noisePct: Float,
        noiseType: String
    ) =
        withContext(Dispatchers.IO) {
            // simple sine wave generator
            val frameOut = ShortArray(buffLength)
            val amplitudeMax = 32767
            val twoPi = 2 * PI
            var phaseTone = 0.0
            var phaseAmplitude = 0.0

            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var b3 = 0.0
            var b4 = 0.0
            var b5 = 0.0
            var b6 = 0.0
            var lastOut = 0.0

            while (isPlaying) {
                for (i in 0 until buffLength) {
                    val amplitude = (amplitudeMax
                            + 0.5 * amplitudeDelta * (sin(phaseAmplitude) - 1)).toInt()
                    val noiseGaussian = Random.nextFloat()
                    val noiseOutput = when (noiseType) {
                        "White" -> {
                            noiseGaussian
                        }

                        "Pink" -> {
                            b0 = 0.99886 * b0 + noiseGaussian * 0.0555179
                            b1 = 0.99332 * b1 + noiseGaussian * 0.0750759
                            b2 = 0.96900 * b2 + noiseGaussian * 0.1538520
                            b3 = 0.86650 * b3 + noiseGaussian * 0.3104856
                            b4 = 0.55000 * b4 + noiseGaussian * 0.5329522
                            b5 = -0.7616 * b5 - noiseGaussian * 0.0168980
                            var output = b0 + b1 + b2 + b3 + b4 + b5 + b6 + noiseGaussian * 0.5362
                            b6 = noiseGaussian * 0.115926
                            output /= 40 // (roughly) compensate for gain
//                            scale output from 0.75-1 to 0-1 (after initial transient)
//                            if (output > 0.75) {
//
//                            }
                            output.toFloat()
                        }

                        "Brownian" -> {
                            var output = (lastOut + (0.02 * noiseGaussian)) / 1.02
                            lastOut = output
                            output *= 1.2 // (roughly) compensate for gain
                            output.toFloat()
                        }

                        else -> {
                            noiseGaussian
                        }
                    }

                    frameOut[i] =
                        (amplitude * ((1 - noisePct / 100) * sin(phaseTone) + noisePct / 100 * noiseOutput))
                            .toInt().toShort()
//                    val fTemp = frameOut[i]
//                    println("$i $amplitude $noiseGaussian $b0 $b1 $b2 $b3 $b4 $b5 $b6 $noiseOutput $fTemp")
//                    println("$i $amplitude $noiseGaussian $noiseOutput $fTemp")
                    phaseTone += twoPi * frequencyTone / fs
                    phaseTone %= twoPi
                    phaseAmplitude += twoPi * amplitudeFrequency / fs
                    phaseAmplitude %= twoPi
                }
                noiseTrack.write(frameOut, 0, buffLength)
            }
        }

    private suspend fun startPlaying() =
        withContext(Dispatchers.IO) {
            noiseTrack.play()
            isPlaying = true
        }

    private fun stopPlaying() {
        if (isPlaying) {
            isPlaying = false
            noiseTrack.stop()
        }
    }

    private fun startOrRestartPlaying() {
        if (!isPlaying) {
            if (binding.frequencyEdittext.text.toString()
                    .toFloat() > 20000 || binding.frequencyEdittext.text.toString().toFloat() < 20
            ) {
                Snackbar.make(
                    binding.root,
                    "Frequency must be between 20-20000",
                    Snackbar.LENGTH_LONG
                ).show()
            } else if (binding.ampAmpEdittext.text.toString()
                    .toFloat() > 32767 || binding.ampAmpEdittext.text.toString().toFloat() < 0
            ) {
                Snackbar.make(
                    binding.root,
                    "Wave effect amplitude modulation must be between 0-32767",
                    Snackbar.LENGTH_LONG
                ).show()
            } else if (binding.ampFreqEdittext.text.toString()
                    .toFloat() > 5 || binding.ampFreqEdittext.text.toString().toFloat() < 0
            ) {
                Snackbar.make(
                    binding.root,
                    "Wave effect frequency must be between 0-5",
                    Snackbar.LENGTH_LONG
                ).show()
            } else if (binding.noisePctEdittext.text.toString()
                    .toFloat() > 100 || binding.noisePctEdittext.text.toString().toFloat() < 0
            ) {
                Snackbar.make(
                    binding.root,
                    "Noise percent must be between 0-100",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                scope.launch {
                    startPlaying()
                    playback(
                        binding.frequencyEdittext.text.toString().toFloat(),
                        binding.ampAmpEdittext.text.toString().toFloat(),
                        binding.ampFreqEdittext.text.toString().toFloat(),
                        binding.noisePctEdittext.text.toString().toFloat(),
                        binding.spinnerNoise.selectedItem.toString()
                        )
                }
            }
        }
    }
}
