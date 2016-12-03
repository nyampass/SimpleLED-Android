package com.nyampass.simpleled

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import com.google.android.gms.internal.zzs.TAG

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        Log.d(TAG, "From: " + remoteMessage!!.from)

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
        }

        if (remoteMessage.notification != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.notification.body)
        }

        val preference = this.getSharedPreferences("setting", Context.MODE_PRIVATE)
        val address = preference.getString("selectedAddress", null)
        if (address == null) {
            return
        }

        val ble = BLE(this) { success, message ->
            Toast.makeText(this@MyFirebaseMessagingService, message, Toast.LENGTH_SHORT).show()
        }

        val device = ble.device(address)

        ble.connect(device) { gatt, characteristic ->
            ble.writeCharacteristic(gatt, characteristic, byteArrayOf(0x00))
            Handler().postDelayed({
                ble.writeCharacteristic(gatt, characteristic, byteArrayOf(0x01))
            }, 3000)
        }
    }
}