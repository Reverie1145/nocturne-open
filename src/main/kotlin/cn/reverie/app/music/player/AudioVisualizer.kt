package cn.reverie.app.music.player

import kotlin.math.*

/**
 * High-performance, silky-smooth Real-time Audio Spectrum & Oscilloscope Engine.
 * Features:
 * - Lock-free PCM ring buffer for zero-overhead audio streaming
 * - Zero-crossing phase-locked oscilloscope triggering to eliminate jitter
 * - Native frame-rate temporal interpolation (runs silky smooth at 60/144/240+ FPS)
 * - 512-point Cooley-Tukey Radix-2 FFT with Hanning window & gravity-decay peak meters
 */
object AudioVisualizer {

    private const val FFT_SIZE = 512
    private const val HALF_FFT = FFT_SIZE / 2
    const val BANDS = 16

    // Real-time frequency bands (0.0 .. 1.0)
    val bands = FloatArray(BANDS)

    // Peak hold positions for each band (0.0 .. 1.0)
    val peakBands = FloatArray(BANDS)
    private val peakSpeed = FloatArray(BANDS)

    // Waveform display points for silky-smooth oscilloscope (-1.0 .. 1.0)
    const val WAVE_POINTS = 48
    val wavePoints = FloatArray(WAVE_POINTS)
    private val targetWave = FloatArray(WAVE_POINTS)

    // Overall energy indicators
    @Volatile
    var bassEnergy = 0f
        private set
    @Volatile
    var trebleEnergy = 0f
        private set
    @Volatile
    var volumeRms = 0f
        private set

    // Lock-free circular ring buffer for raw PCM samples
    private const val RING_SIZE = 8192
    private val pcmRing = FloatArray(RING_SIZE)
    @Volatile
    private var ringWriteHead = 0

    // Internal FFT buffers
    private val sampleBuffer = FloatArray(FFT_SIZE)
    private val fftReal = DoubleArray(FFT_SIZE)
    private val fftImag = DoubleArray(FFT_SIZE)
    private val window = DoubleArray(FFT_SIZE)

    // Logarithmic frequency crossover boundaries
    private val bandBinRanges = IntArray(BANDS * 2)

    private var lastFftTimeMs = 0L
    private var lastRenderTimeMs = System.currentTimeMillis()

    init {
        // Precompute Hanning window
        for (i in 0 until FFT_SIZE) {
            window[i] = 0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))
        }

        // Setup 16 logarithmic frequency bins from 30Hz to 18000Hz
        val minFreq = 30.0
        val maxFreq = 18000.0
        for (b in 0 until BANDS) {
            val fLow = minFreq * (maxFreq / minFreq).pow(b.toDouble() / BANDS)
            val fHigh = minFreq * (maxFreq / minFreq).pow((b + 1).toDouble() / BANDS)
            val binLow = (fLow * FFT_SIZE / 44100.0).toInt().coerceIn(0, HALF_FFT - 1)
            val binHigh = maxOf(binLow + 1, (fHigh * FFT_SIZE / 44100.0).toInt().coerceIn(binLow + 1, HALF_FFT))
            bandBinRanges[b * 2] = binLow
            bandBinRanges[b * 2 + 1] = binHigh
        }
    }

    /**
     * Ingest raw 16-bit PCM audio bytes directly from the audio playback thread.
     * Ultra-fast O(N) array write with minimal CPU overhead.
     */
    fun processPcm(buffer: ByteArray, bytesRead: Int) {
        if (bytesRead < 4) return

        var sampleSum = 0f
        var head = ringWriteHead

        var idx = 0
        while (idx < bytesRead - 1) {
            // Little-endian 16-bit signed short
            val s = (buffer[idx].toInt() and 0xFF or (buffer[idx + 1].toInt() shl 8)).toShort()
            val sample = s.toFloat() / 32768.0f

            pcmRing[head] = sample
            head = (head + 1) % RING_SIZE

            sampleSum += sample * sample
            idx += 4 // Stereo step
        }

        ringWriteHead = head
        volumeRms = sqrt(sampleSum / (bytesRead / 4).coerceAtLeast(1))

        // Throttle FFT to run at ~45Hz (every 22ms) to prevent audio thread starvation
        val now = System.currentTimeMillis()
        if (now - lastFftTimeMs >= 22) {
            lastFftTimeMs = now
            updateSpectrum(head)
        }
    }

    /**
     * Computes the FFT frequency spectrum from the latest samples.
     */
    private fun updateSpectrum(head: Int) {
        for (i in 0 until FFT_SIZE) {
            val bufIdx = (head - FFT_SIZE + i + RING_SIZE) % RING_SIZE
            val s = pcmRing[bufIdx]
            sampleBuffer[i] = s
            fftReal[i] = s.toDouble() * window[i]
            fftImag[i] = 0.0
        }

        computeFft(fftReal, fftImag, FFT_SIZE)

        var sumBass = 0f
        var sumTreble = 0f

        for (b in 0 until BANDS) {
            val startBin = bandBinRanges[b * 2]
            val endBin = bandBinRanges[b * 2 + 1]
            var bandMag = 0.0

            for (k in startBin until endBin) {
                val mag = sqrt(fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k])
                if (mag > bandMag) bandMag = mag
            }

            val boost = 1.0 + (b.toDouble() / BANDS) * 2.2
            val normalized = ((bandMag / (FFT_SIZE / 4.0)) * boost).toFloat().coerceIn(0f, 1f)

            if (normalized > bands[b]) {
                bands[b] += (normalized - bands[b]) * 0.70f
            } else {
                bands[b] += (normalized - bands[b]) * 0.16f
            }

            if (bands[b] > peakBands[b]) {
                peakBands[b] = bands[b]
                peakSpeed[b] = 0f
            } else {
                peakSpeed[b] += 0.0035f
                peakBands[b] = (peakBands[b] - peakSpeed[b]).coerceAtLeast(0f)
            }

            if (b < 4) sumBass += bands[b]
            if (b >= 10) sumTreble += bands[b]
        }

        bassEnergy = (sumBass / 4f).coerceIn(0f, 1f)
        trebleEnergy = (sumTreble / 6f).coerceIn(0f, 1f)
    }

    /**
     * Get real-time oscilloscope waveform points for rendering.
     * Automatically applies zero-crossing phase alignment and smooth temporal interpolation
     * on every render frame for silky-smooth 60/144/240+ FPS animations.
     */
    fun getRenderWave(): FloatArray {
        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastRenderTimeMs).coerceIn(1L, 100L)).toFloat() / 1000f
        lastRenderTimeMs = now

        val head = ringWriteHead
        val isQuiet = volumeRms < 0.008f

        if (!isQuiet) {
            // Zero-crossing trigger search: find upward zero crossing in recent samples to lock phase
            var trigPos = (head - 180 + RING_SIZE) % RING_SIZE
            val searchWindow = 140
            for (step in 0 until searchWindow) {
                val pos = (head - searchWindow + step + RING_SIZE) % RING_SIZE
                val prevPos = (pos - 1 + RING_SIZE) % RING_SIZE
                if (pcmRing[prevPos] <= 0f && pcmRing[pos] > 0f) {
                    trigPos = pos
                    break
                }
            }

            // Extract target wave samples
            val sampleStride = 3
            for (i in 0 until WAVE_POINTS) {
                val idx = (trigPos + i * sampleStride) % RING_SIZE
                targetWave[i] = pcmRing[idx]
            }

            // Smooth temporal blend towards target wave (frame-rate independent)
            val blendFactor = (1.0f - exp(-deltaSec * 32f)).coerceIn(0.20f, 0.85f)
            for (i in 0 until WAVE_POINTS) {
                wavePoints[i] += (targetWave[i] - wavePoints[i]) * blendFactor
            }
        } else {
            // Decay to flat line when quiet or paused
            val decay = (1.0f - exp(-deltaSec * 16f)).coerceIn(0.10f, 0.40f)
            for (i in 0 until WAVE_POINTS) {
                wavePoints[i] *= (1.0f - decay)
            }
        }

        return wavePoints
    }

    /**
     * Decay visualizer data when paused or stopped.
     */
    fun onIdleTick() {
        for (b in 0 until BANDS) {
            bands[b] = (bands[b] * 0.85f).coerceAtLeast(0f)
            peakBands[b] = (peakBands[b] * 0.88f).coerceAtLeast(0f)
        }
        for (i in 0 until WAVE_POINTS) {
            wavePoints[i] = wavePoints[i] * 0.82f
        }
        bassEnergy = (bassEnergy * 0.80f).coerceAtLeast(0f)
        trebleEnergy = (trebleEnergy * 0.80f).coerceAtLeast(0f)
        volumeRms = (volumeRms * 0.80f).coerceAtLeast(0f)
    }

    /**
     * Fast Cooley-Tukey Radix-2 in-place decimation-in-time FFT.
     */
    private fun computeFft(real: DoubleArray, imag: DoubleArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val half = len shr 1
            val theta = -2.0 * PI / len
            val wpr = cos(theta)
            val wpi = sin(theta)
            var wr = 1.0
            var wi = 0.0

            for (m in 0 until half) {
                var i = m
                while (i < n) {
                    val jdx = i + half
                    val tr = wr * real[jdx] - wi * imag[jdx]
                    val ti = wr * imag[jdx] + wi * real[jdx]
                    real[jdx] = real[i] - tr
                    imag[jdx] = imag[i] - ti
                    real[i] += tr
                    imag[i] += ti
                    i += len
                }
                val nextWr = wr * wpr - wi * wpi
                wi = wr * wpi + wi * wpr
                wr = nextWr
            }
            len = len shl 1
        }
    }
}
