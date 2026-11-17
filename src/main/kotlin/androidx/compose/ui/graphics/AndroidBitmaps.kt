@file:JvmName("NetflouxAndroidBitmaps")

package androidx.compose.ui.graphics

/** Image en mémoire → image affichable. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = image.toComposeImageBitmap()

/** Image affichable → image dont on peut lire les pixels. */
fun ImageBitmap.asAndroidBitmap(): android.graphics.Bitmap = android.graphics.Bitmap.fromImage(toAwtImage())
