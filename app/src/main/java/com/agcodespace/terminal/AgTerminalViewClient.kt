package com.agcodespace.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class AgTerminalViewClient(
    var terminalView: TerminalView? = null
) : TerminalViewClient {
    private var ctrlDown = false
    private var altDown = false

    fun toggleCtrl(): Boolean {
        ctrlDown = !ctrlDown
        return ctrlDown
    }

    fun isCtrlPressed(): Boolean = ctrlDown

    fun toggleAlt(): Boolean {
        altDown = !altDown
        return altDown
    }

    fun isAltPressed(): Boolean = altDown

    fun showSoftKeyboard() {
        terminalView?.let { view ->
            view.requestFocus()
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, 0)
        }
    }

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) {
        showSoftKeyboard()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean {
        val active = ctrlDown
        ctrlDown = false
        return active
    }

    override fun readAltKey(): Boolean {
        val active = altDown
        altDown = false
        return active
    }

    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    override fun onEmulatorSet() {}

    override fun logError(tag: String, message: String) { Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { Log.e(tag, e.message, e) }
}
