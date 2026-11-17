@file:JvmName("NetflouxDialogWindowProvider")

package androidx.compose.ui.window

/** Sur Android, la fenêtre d'une boîte de dialogue. Les boîtes de Windows n'en exposent pas. */
interface DialogWindowProvider {
    val window: android.view.Window
}
