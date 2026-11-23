package com.githabit.prototype.fixtures

import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.atomic.AtomicBoolean

/** Test-process observations, including instrumentation/input retention; not app-only peaks. */
internal class OperationMetrics(private val window: Window? = null) : AutoCloseable {
    private val worker = HandlerThread("QA-metrics").apply { start() }
    private val handler = Handler(worker.looper)
    private val active = AtomicBoolean(true)
    private val frames = mutableListOf<Long>()
    private var firstDraw = 0
    private var drops = 0
    @Volatile private var heapPeak = 0L
    @Volatile private var nativePeak = 0L
    @Volatile private var pssPeak = 0
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        // Callback object is reused. Copy primitive values here, never retain it.
        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val first = metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)
        synchronized(frames) {
            drops += dropped
            if (first == 1L) firstDraw++ else if (duration >= 0) frames += duration
        }
    }
    private val sample = object : Runnable {
        override fun run() {
            val runtime = Runtime.getRuntime()
            heapPeak = maxOf(heapPeak, runtime.totalMemory() - runtime.freeMemory())
            nativePeak = maxOf(nativePeak, Debug.getNativeHeapAllocatedSize())
            val memory = Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            pssPeak = maxOf(pssPeak, memory.totalPss)
            if (active.get()) handler.postDelayed(this, 50)
        }
    }

    init { window?.addOnFrameMetricsAvailableListener(listener, handler); handler.post(sample) }

    fun <T> measure(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtimeNanos()
        val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")
        Log.i("GitHabitMetrics", "$label started maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
        return try { block() } finally {
            Log.i("GitHabitMetrics", "$label elapsedMs=${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000} " +
                "gcBefore=$gcBefore gcAfter=${Debug.getRuntimeStat("art.gc.gc-count")} heapPeakBytes=$heapPeak " +
                "nativePeakBytes=$nativePeak pssPeakKiB=$pssPeak maxHeapBytes=${Runtime.getRuntime().maxMemory()}")
        }
    }

    override fun close() {
        window?.removeOnFrameMetricsAvailableListener(listener)
        active.set(false)
        handler.removeCallbacks(sample)
        synchronized(frames) {
            val sorted = frames.sorted()
            fun percentile(p: Double) = sorted.getOrNull(((sorted.size - 1) * p).toInt())?.div(1_000_000.0)
            Log.i("GitHabitMetrics", "frames count=${frames.size} firstDraw=$firstDraw callbackDrops=$drops " +
                "over16ms=${frames.count { it > 16_666_667 }} over33ms=${frames.count { it > 33_333_333 }} " +
                "medianMs=${percentile(.5)} p95Ms=${percentile(.95)} maxMs=${sorted.lastOrNull()?.div(1_000_000.0)}")
        }
        worker.quitSafely()
    }
}
