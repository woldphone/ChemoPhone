package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object AutoUpdateManager {
    private const val TAG = "AutoUpdateManager"
    private const val GITHUB_RELEASE_API = "https://api.github.com/repos/woldphone/ChemoPhone/releases/tags/latest-debug"

    private val _updateStatus = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateStatus: StateFlow<UpdateState> = _updateStatus

    private val client = OkHttpClient()

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val latestVersion: String, val downloadUrl: String) : UpdateState()
        object NoUpdate : UpdateState()
        data class Downloading(val progress: Int) : UpdateState()
        data class ReadyToInstall(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    fun checkForUpdates(scope: CoroutineScope) {
        _updateStatus.value = UpdateState.Checking
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GITHUB_RELEASE_API)
                    .header("User-Agent", "OfflinePhoneFinder-AutoUpdater")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateStatus.value = UpdateState.Error("Failed to fetch release info: Code ${response.code}")
                        return@launch
                    }

                    val jsonStr = response.body?.string() ?: ""
                    val json = JSONObject(jsonStr)

                    // Parse Assets to find app-debug.apk download url
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl.isBlank()) {
                        _updateStatus.value = UpdateState.Error("No APK found in the latest release assets.")
                        return@launch
                    }

                    // For continuous debug releases, check body or tag_name or standard rolling update strategy.
                    // We can compare against standard version names, or do a rolling comparison. Since it is a rolling-debug
                    // commit build, any remote build that is newer than current compile timestamp or simply letting the user
                    // click "Update Now" to sync to GitHub main branch is incredibly robust.
                    // Let's offer direct manual/auto update capability if latest release tag is fetched successfully!
                    val tagName = json.optString("tag_name", "latest-debug")
                    _updateStatus.value = UpdateState.UpdateAvailable(tagName, downloadUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                _updateStatus.value = UpdateState.Error("Connection error: ${e.localizedMessage}")
            }
        }
    }

    fun downloadAndInstallApk(context: Context, scope: CoroutineScope, downloadUrl: String) {
        scope.launch(Dispatchers.IO) {
            try {
                _updateStatus.value = UpdateState.Downloading(0)
                val request = Request.Builder().url(downloadUrl).build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _updateStatus.value = UpdateState.Error("Download failed: Code ${response.code}")
                        return@launch
                    }

                    val body = response.body
                    if (body == null) {
                        _updateStatus.value = UpdateState.Error("Empty download response body.")
                        return@launch
                    }

                    val apkFile = File(context.cacheDir, "app-update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    val totalBytes = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(apkFile)

                    val data = ByteArray(4096)
                    var totalRead = 0L
                    var read: Int

                    while (inputStream.read(data).also { read = it } != -1) {
                        outputStream.write(data, 0, read)
                        totalRead += read
                        if (totalBytes > 0) {
                            val progress = ((totalRead * 100) / totalBytes).toInt()
                            _updateStatus.value = UpdateState.Downloading(progress)
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    _updateStatus.value = UpdateState.ReadyToInstall(apkFile)

                    // Trigger package installer immediately
                    launchInstaller(context, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                _updateStatus.value = UpdateState.Error("Download failed: ${e.localizedMessage}")
            }
        }
    }

    fun launchInstaller(context: Context, file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Package Installer: ${e.message}", e)
            _updateStatus.value = UpdateState.Error("Cannot launch Installer: ${e.localizedMessage}")
        }
    }
}
