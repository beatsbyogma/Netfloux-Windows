@file:JvmName("NetflouxAndroidLocals")

package androidx.compose.ui.platform

import android.content.Context
import android.content.DesktopContext
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/** Le contexte de l'application, comme sur Android. */
val LocalContext = staticCompositionLocalOf<Context> { DesktopContext }

/** La « vue » qui porte l'écran : sur Windows, un simple porte-réglages. */
val LocalView = staticCompositionLocalOf<View> { View.Root }
