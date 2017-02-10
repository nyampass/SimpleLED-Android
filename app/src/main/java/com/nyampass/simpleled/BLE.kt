package com.nyampass.simpleled

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

class BLE constructor(val context: Context, val callback: (success: Boolean, message: String) -> Unit) {
    companion object {
        val serviceUUID = ParcelUuid.fromString("713d0000-503e-4c75-ba94-3148f18d941e")!!
        val characteristicUUID = ParcelUuid.fromString("713d0003-503e-4c75-ba94-3148f18d941e")!!
    }

    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter!!

    var bluetoothLeScanner: BluetoothLeScanner? = null
    var scanCallback: ScanCallback? = null

    var devices = mutableListOf<BluetoothDevice>()

    object What {
        val successToWrite = 1

        val failDeviceIsNotFound = -1
        val failDeviceIsDisconnected = -2
        val failServiceIsNotFound = -3
        val failCharacteristicIsNotFound = -4
        val failToWrite = -5
    }

    val handler: Handler by lazy {
        Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                What.successToWrite -> {
                    this@BLE.callback(true, "書き込みに成功しました")
                }

                What.failDeviceIsNotFound -> {
                    this@BLE.callback(false, "デバイスが見つかりません")
                }
                What.failDeviceIsDisconnected -> {
                    this@BLE.callback(false, "デバイスの接続が切れました")
                }
                What.failServiceIsNotFound -> {
                    this@BLE.callback(false, "サービスが見つかりません")
                }
                What.failCharacteristicIsNotFound -> {
                    this@BLE.callback(false, "キャラクタリスティックが見つかりません")
                }
                What.failToWrite -> {
                    this@BLE.callback(false, "書き込みに失敗しました")
                }

                else -> {
                    this@BLE.callback(false, "不明なメッセージです: ${msg.what}")
                }
            }
            true
        }
    }

    fun stopToScanBluetooth() {
        val bluetoothLeScanner = this.bluetoothLeScanner
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(this.scanCallback)
            this.bluetoothLeScanner = null
            this.scanCallback = null
        }
    }

    fun scanDevices(foundDevicesCallback: (List<BluetoothDevice>) -> Unit) {
        val alreadyFoundDevices = mutableSetOf<String>()
        var found = false

        this.devices.clear()
        this.bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        this.scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)

                if (result != null && result.device != null
                        && !alreadyFoundDevices.contains(result.device.address)) {

                    alreadyFoundDevices.add(result.device.address)
                    this@BLE.devices.add(result.device)
                    foundDevicesCallback(this@BLE.devices)
                    found = true
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
            }
        }

        val filters = listOf(ScanFilter.Builder().setServiceUuid(serviceUUID).build())
        val scanSettings = ScanSettings.Builder().build()

         this.bluetoothLeScanner?.startScan(null, scanSettings, this.scanCallback)

        Handler().postDelayed({
            this.stopToScanBluetooth()
            if (!found) {
                this@BLE.handler.sendEmptyMessage(What.failDeviceIsNotFound)
                foundDevicesCallback(listOf())
            }
        }, 10000)
    }

    fun device(address: String) = bluetoothAdapter.getRemoteDevice(address)!!

    class Characteristic constructor(val gatt: BluetoothGatt, val characteristic: BluetoothGattCharacteristic) {
        var writeCallback: ((success: Boolean) -> Unit)? = null

        fun write(value: ByteArray, callback: (success: Boolean) -> Unit) {
            characteristic.value = value
            gatt.writeCharacteristic(characteristic)
            this.writeCallback = { success ->
                callback(success)
                this@Characteristic.writeCallback = null
            }
        }

        fun close() {
            this.gatt.close()
        }
    }

    fun connect(device: BluetoothDevice, callback: (characteristic: Characteristic) -> Unit) {
        var characteristicObj: Characteristic? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.discoverServices()
                } else {
                    this@BLE.handler.sendEmptyMessage(What.failDeviceIsDisconnected)
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    gatt?.let { gatt ->
                        gatt.getService(serviceUUID.uuid)?.let { service ->
                            var found = false
                            for (characteristic in service.characteristics) {
                                if (characteristic.uuid.equals(characteristicUUID.uuid)) {
                                    characteristicObj = Characteristic(gatt, characteristic)
                                    callback(characteristicObj!!)
                                    found = true
                                    break
                                }
                            }
                            if (!found) {
                                this@BLE.handler.sendEmptyMessage(What.failCharacteristicIsNotFound)
                            }
                        }
                    } ?: run {
                        this@BLE.handler.sendEmptyMessage(What.failServiceIsNotFound)
                    }
                } else {
                    this@BLE.handler.sendEmptyMessage(What.failServiceIsNotFound)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                super.onCharacteristicWrite(gatt, characteristic, status)

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    this@BLE.handler.sendEmptyMessage(What.successToWrite)
                    characteristicObj?.writeCallback?.invoke(true)
                } else {
                    this@BLE.handler.sendEmptyMessage(What.failToWrite)
                    characteristicObj?.writeCallback?.invoke(false)
                }
            }
        }

        device.connectGatt(context, true, gattCallback)
    }
}