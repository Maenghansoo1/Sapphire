package com.sapphire.hawe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.sapphire.hawe.core.AppSession
import com.sapphire.hawe.core.data.DataStore
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val dataStore = DataStore(File(application.cacheDir, "hawe-media"))
    val appSession = AppSession()
}
