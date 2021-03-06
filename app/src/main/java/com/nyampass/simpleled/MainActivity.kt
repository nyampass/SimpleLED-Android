package com.nyampass.simpleled

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.nyampass.simpleled.databinding.ActivityMainBinding
import com.nyampass.simpleled.databinding.DeviceRowBinding

class MainActivity : Activity() {
    var devices = listOf<BluetoothDevice>()
        set(devices) {
            field = devices
            adapter.notifyDataSetChanged()
        }

    var adapter = object : BaseAdapter() {
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getItem(position: Int): BluetoothDevice {
            return devices[position]
        }

        override fun getCount(): Int {
            return devices.count()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val binding = convertView?.run { DataBindingUtil.getBinding<DeviceRowBinding>(this) }
                    ?: DeviceRowBinding.inflate(layoutInflater, parent, false)
            val device = getItem(position)

            binding.device = device
            binding.check.text = if (device.address == this@MainActivity.selectedAddress) "✓" else ""

            return binding.root
        }
    }

    val ble by lazy {
        BLE(this) { success, message ->
            this@MainActivity.runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val progressDialog by lazy {
        ProgressDialog(this).apply {
            isIndeterminate = true
            setMessage("HaLake Kit linoを検索中..")
        }
    }

    val preference: SharedPreferences? by lazy {
        this.getSharedPreferences("setting", Context.MODE_PRIVATE)
    }

    var selectedAddress: String?
        get() = this.preference!!.getString("selectedAddress", null)
        set(value) {
            val editor = this.preference!!.edit()
            editor.putString("selectedAddress",  value)
            editor.apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)

        val listView = binding.list
        listView.adapter = adapter
        listView.setOnItemClickListener { parent, view, position, id ->
            val device = devices[position]

            val buttons = arrayOf("ONにする", "OFFにする", "デフォルトに設定")

            val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle(device.name)
                    .setItems(buttons, { dialog, index ->
                            when(index) {
                                0 ->
                                    this@MainActivity.ble.connect(device) { characteristic ->
                                        characteristic.write(byteArrayOf(0x53, 0x01, 0x01)) {
                                            characteristic.write(byteArrayOf(0x4e, 0x01, 0xFF.toByte())) {
                                                characteristic.close()
                                            }
                                        }
                                    }
                                1 ->
                                    this@MainActivity.ble.connect(device) { characteristic ->
                                        characteristic.write(byteArrayOf(0x4e, 0x01, 0x00)) {
                                            characteristic.close()
                                        }
                                    }

                                2 -> {
                                    this@MainActivity.selectedAddress = device.address
                                    this@MainActivity.adapter.notifyDataSetChanged()
                                }
                            }
                        })
                    .setCancelable(true)
                    .create()
            dialog.show()
        }

        this.scanDevices(true)

        binding.refresh.setOnRefreshListener {
            this@MainActivity.scanDevices(false) {
                binding.refresh.isRefreshing = false
            }
        }
    }

    private fun scanDevices(showDialog: Boolean, callback: (() -> Unit)? = null) {
        this.devices = listOf()
        if (showDialog)
            this.progressDialog.show()

        this.ble.scanDevices { devices ->
            if (showDialog)
                this.progressDialog.hide()
            this.devices = devices
            callback?.invoke()
        }
    }
}