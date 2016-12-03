package com.nyampass.simpleled

import android.util.Log

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService

import android.content.ContentValues.TAG
import android.provider.Settings
import com.github.kittinunf.fuel.httpPost

class MyFirebaseIdService : FirebaseInstanceIdService() {
    override fun onTokenRefresh() {
        val refreshedToken = FirebaseInstanceId.getInstance().token
        Log.d(TAG, "Refreshed token: " + refreshedToken!!)

        val androidId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID)

        "http://192.168.11.68:3000/api/tokens/android/$androidId".httpPost(listOf("token" to refreshedToken))
                .response { request, response, result ->
                }
    }
}