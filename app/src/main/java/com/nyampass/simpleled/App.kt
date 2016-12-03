package com.nyampass.simpleled;

import android.app.Application

class App: Application() {
    private object Holder { var INSTANCE: App = null!! }

    override fun onCreate() {
        super.onCreate()
        Holder.INSTANCE = this
    }

    companion object {
        val instance: App by lazy { Holder.INSTANCE }
    }
}
