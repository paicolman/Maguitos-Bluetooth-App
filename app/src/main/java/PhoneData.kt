package com.android.example.listviewtest

import android.os.BatteryManager
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.telephony.SignalStrength
import android.telephony.PhoneStateListener
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter


// PhoneData is a singleton which provides access to battery usage and signal strength.
// Implemented as singleton because I wanted to use it's data both in MainActivity and BluetoothService,
// but I finally put everything in MainActivity. It could be declared either as a companionObject to MainActivity
// or as a normal instantiable class (singletons are usually not recommended but are the easiest way to implement global stuff...)
// Many of these functions will work only after API level 26

object PhoneData : PhoneStateListener() {

    var battLevel = "000"
    var mSignalStrength = 0

    // Battery Info receiver. Gets triggered on battery level changes
    val batInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            battLevel = "$level"
        }
    }

    // Delivers a date string. Placed here to be consequent, could have been implemented in MainActivity
    fun getDate(): String {
        val date = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return date.format(formatter)
    }

    //Delivers a time string. Placed here to be consequent, could have been implemented in MainActivity
    fun getTime(): String {
        val time = LocalTime.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        return time.format(formatter)
    }

    // Signal Strength function.
    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            super.onSignalStrengthsChanged(signalStrength)
            mSignalStrength = (signalStrength.level + 1) * 20
            //mSignalStrength = 2 * mSignalStrength - 113 // -> dBm
    }
}