package com.imashnake.animite.social.data

import android.content.Context
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Handles avatar image uploads to Cloudinary via unsigned upload preset.
 *
 * ABUSE RISKS & PRODUCTION SECURITY NOTICE:
 * This implementation uses Cloudinary's unsigned upload mode, which allows public clients to upload
 * assets directly to the Cloudinary cloud using an upload preset without an API secret.
 *
 * Abuse Risks of Unsigned Uploads:
 * 1. Anyone with knowledge of the cloud name and upload preset can send arbitrary requests to the
 *    Cloudinary API, potentially consuming storage and bandwidth quotas.
 * 2. Unsigned uploads cannot securely verify that the requester is the authenticated user owning the account.
 * 3. Client-side format and file-size validation are bypassable by anyone invoking the API directly.
 *
 * Production Recommendation:
 * For a secure production architecture, NEVER use client-side unsigned uploads. Instead:
 * 1. Use a trusted server or backend service (e.g., Firebase Cloud Functions or a dedicated API).
 * 2. Verify Firebase Authentication ID tokens on the server.
 * 3. Generate a signed Cloudinary upload signature on the server using the Cloudinary API Secret.
 * 4. The Cloudinary API Secret must NEVER be stored or bundled in the Android application.
 */
@Singleton
class CloudinaryUploader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CLOUD_NAME = "dojsusfim"
        const val UPLOAD_PRESET = "animeta_avatars"
        const val UPLOAD_ENDPOINT = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

        @Volatile
        private var isInitialized = false

        fun initialize(context: Context, cloudName: String = CLOUD_NAME) {
            if (!isInitialized && cloudName.isNotBlank()) {
                val config = HashMap<String, Any>()
                config["cloud_name"] = cloudName
                config["secure"] = true
                runCatching {
                    MediaManager.init(context.applicationContext, config)
                    isInitialized = true
                }
            }
        }
    }

    init {
        initialize(context, CLOUD_NAME)
    }

    /**
     * Uploads the given image bytes to Cloudinary using multipart/form-data.
     *
     * @param imageBytes The raw binary data of the image.
     * @param mimeType The detected MIME type (e.g. image/jpeg, image/png).
     * @return Result containing the secure_url on success, or an exception on failure.
     */
    suspend fun uploadImage(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        preset: String = UPLOAD_PRESET,
        cloud: String = CLOUD_NAME,
    ): Result<String> = uploadMedia(
        mediaBytes = imageBytes,
        mimeType = mimeType,
        preset = preset,
        cloud = cloud,
    )

    /**
     * Uploads image or video media (MP4/GIF) to Cloudinary.
     */
    suspend fun uploadMedia(
        mediaBytes: ByteArray,
        mimeType: String,
        preset: String = UPLOAD_PRESET,
        cloud: String = CLOUD_NAME,
    ): Result<String> = withContext(Dispatchers.IO) {
        val isVideo = mimeType == "video/mp4" || mimeType.startsWith("video/")
        val resourceType = if (isVideo) "video" else "image"

        runCatching {
            uploadViaHttp(mediaBytes, mimeType, preset, cloud, resourceType)
        }.recoverCatching { httpError ->
            if (isInitialized && !isVideo) {
                val sdkResult = uploadViaSdk(mediaBytes, preset)
                if (sdkResult.isSuccess) {
                    return@recoverCatching sdkResult.getOrThrow()
                }
            }
            throw httpError
        }
    }

    /**
     * Derives a static poster image URL from a Cloudinary video URL.
     * Replaces .mp4 extension with .jpg for instant Cloudinary-generated poster frame.
     */
    fun derivePosterUrl(videoUrl: String): String {
        return if (videoUrl.contains("/video/upload/", ignoreCase = true) && videoUrl.endsWith(".mp4", ignoreCase = true)) {
            videoUrl.substringBeforeLast(".mp4") + ".jpg"
        } else {
            videoUrl
        }
    }

    private fun uploadViaHttp(
        mediaBytes: ByteArray,
        mimeType: String,
        preset: String,
        cloud: String,
        resourceType: String = "image",
    ): String {
        val boundary = "===AnimiteBoundary" + System.currentTimeMillis() + "==="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val endpoint = URL("https://api.cloudinary.com/v1_1/$cloud/$resourceType/upload")
        val conn = endpoint.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doInput = true
            conn.doOutput = true
            conn.useCaches = false
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 45000
            conn.readTimeout = 45000

            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "video/mp4" -> "mp4"
                else -> if (resourceType == "video") "mp4" else "jpg"
            }

            DataOutputStream(conn.outputStream).use { dos ->
                // upload_preset field
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"$lineEnd$lineEnd")
                dos.writeBytes(preset)
                dos.writeBytes(lineEnd)

                // file field
                dos.writeBytes("$twoHyphens$boundary$lineEnd")
                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"media.$extension\"$lineEnd")
                dos.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")
                dos.write(mediaBytes)
                dos.writeBytes(lineEnd)

                // End of multipart
                dos.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
                dos.flush()
            }

            val responseCode = conn.responseCode
            val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseBody = inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val errorMsg = runCatching {
                    JSONObject(responseBody).getJSONObject("error").getString("message")
                }.getOrDefault(if (responseBody.isNotBlank()) responseBody else "HTTP error code $responseCode")
                throw Exception("Cloudinary upload failed ($responseCode): $errorMsg")
            }

            val json = JSONObject(responseBody)
            val secureUrl = json.optString("secure_url").ifBlank { json.optString("url") }
            if (secureUrl.isBlank()) {
                throw Exception("Cloudinary response did not contain a valid media URL")
            }
            return secureUrl
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun uploadViaSdk(
        imageBytes: ByteArray,
        preset: String,
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val requestId = MediaManager.get().upload(imageBytes)
            .unsigned(preset)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val secureUrl = resultData["secure_url"] as? String
                        ?: resultData["url"] as? String
                        ?: ""
                    if (secureUrl.isNotBlank()) {
                        continuation.resume(Result.success(secureUrl))
                    } else {
                        continuation.resume(Result.failure(Exception("Cloudinary returned empty URL")))
                    }
                }

                override fun onError(requestId: String, error: ErrorInfo) {
                    continuation.resume(Result.failure(Exception(error.description)))
                }

                override fun onReschedule(requestId: String, error: ErrorInfo) {
                    continuation.resume(Result.failure(Exception("Upload rescheduled: ${error.description}")))
                }
            })
            .dispatch()

        continuation.invokeOnCancellation {
            runCatching { MediaManager.get().cancelRequest(requestId) }
        }
    }
}
