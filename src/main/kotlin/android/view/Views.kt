package android.view

/** Ce que le code partagé lit d'une vue Android : rien qui ait un effet sur Windows. */
open class View {
    var isSoundEffectsEnabled: Boolean = true
    open val parent: Any? get() = null

    object Root : View()
}

class Window {
    var callback: Callback? = null
    fun setDimAmount(amount: Float) {}
    fun addFlags(flags: Int) {}

    interface Callback {
        fun dispatchKeyEvent(event: KeyEvent): Boolean
    }
}

class KeyEvent(val action: Int, val keyCode: Int) {
    var repeatCount: Int = 0
    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val KEYCODE_DPAD_UP = 19
        const val KEYCODE_DPAD_DOWN = 20
        const val KEYCODE_DPAD_LEFT = 21
        const val KEYCODE_DPAD_RIGHT = 22
        const val KEYCODE_DPAD_CENTER = 23
        const val KEYCODE_ENTER = 66
        const val KEYCODE_NUMPAD_ENTER = 160
        const val KEYCODE_BACK = 4
        const val KEYCODE_ESCAPE = 111
    }
}
