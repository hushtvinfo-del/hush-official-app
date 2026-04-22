package com.hushtv.tv

import android.app.Application
import com.hushtv.tv.data.XtreamApi

class HushTVApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable HTTP disk cache — first call to a category list goes to the
        // network, every subsequent call for 5 min is served from disk.
        XtreamApi.enableDiskCache(this)
    }
}
