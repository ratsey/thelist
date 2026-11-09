package com.ratsey.thelist.receipt

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Photo capture helpers. Capture is delegated to the installed camera app through
 * ACTION_IMAGE_CAPTURE, so The List needs no CAMERA permission and no preview code.
 */
object ReceiptPhotos {

    fun newPhotoFile(context: Context): File {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        return File(dir, "receipt-${System.currentTimeMillis()}.jpg")
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** On-device OCR. The bundled Latin model works without Google Play Services. */
class ReceiptOcr(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun readText(uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (error: Exception) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }

        recognizer.process(image)
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
