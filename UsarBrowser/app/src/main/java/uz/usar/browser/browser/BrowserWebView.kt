package uz.usar.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/**
 * WebView that reports whether the root page was over-scrolled at the top during the current
 * gesture. Pull-to-refresh only triggers then, so pages with their own inner scrolling areas
 * keep working normally.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class BrowserWebView(context: Context) : WebView(context) {
    var overscrolledTop = false
        private set
    var onScrollDelta: ((Int) -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) overscrolledTop = false
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (clampedY && scrollY == 0) overscrolledTop = true
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t > 0) overscrolledTop = false
        onScrollDelta?.invoke(t - oldt)
    }
}
