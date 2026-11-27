// Text drawn into pictures (the widget's mini-forecast labels, the status-bar temperature icon) is
// drawn on the main thread only, one picture at a time.
//
// Why: Android's software text drawing (text into a Bitmap, SkBitmapDevice) is not safe on two
// threads at once. 0.7.0's widget thread drew its frames' text while the main thread drew text into a
// picture too, and the app died with a native crash (SIGSEGV in prepare_for_direct_mask_drawing /
// SkGlyph::mask) — on Shane's phone right after a widget tap opened it. The emulator caught the same
// crash with the main thread drawing the app's own text in software (0.6.2 and the 0.7.0 RC). The
// main thread is the one thread that can't race itself, so all such text is drawn there; the widget
// thread only adds the finished text picture to its frames. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.widget

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** The main thread was too busy to draw the text in time; the caller keeps its previous picture. */
class TextNotDrawnInTime : RuntimeException("text picture not drawn on the main thread in time")

object MainThreadText {
    const val WAIT_MS = 10_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Name of the thread the last text picture was drawn on (for the regression tests). */
    @Volatile var lastThread: String? = null

    /** Runs [draw] on the main thread (straight away if already there) and returns what it made. */
    fun <T> draw(waitMs: Long = WAIT_MS, draw: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) { lastThread = Thread.currentThread().name; return draw() }
        val done = CountDownLatch(1)
        val out = AtomicReference<Result<T>?>(null)
        main.post {
            out.set(runCatching { lastThread = Thread.currentThread().name; draw() })
            done.countDown()
        }
        if (!done.await(waitMs, TimeUnit.MILLISECONDS)) throw TextNotDrawnInTime()
        return out.get()!!.getOrThrow()
    }
}
