package com.numandegirmenci.belgeizi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.numandegirmenci.belgeizi.ui.ScanApp

class MainActivity : ComponentActivity() {
    private var newFiles by mutableStateOf<List<Uri>>(emptyList())
    private var editingExternalFile by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        newFiles = parseFiles(intent)
        editingExternalFile = intent?.action == Intent.ACTION_EDIT
        setContent {
            ScanApp(activity = this, pendingUris = newFiles, externalEdit = editingExternalFile,
                onConsumed = { newFiles = emptyList() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newFiles = parseFiles(intent)
        editingExternalFile = intent?.action == Intent.ACTION_EDIT
    }

    private fun parseFiles(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val results = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data?.let(results::add)
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(results::add)
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(results::addAll)
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let(results::add)
                    @Suppress("DEPRECATION")
                    (intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))?.let(results::addAll)
                }
            }
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(results::add)
        }
        return results.distinctBy { it.toString() }
    }
}
