package com.android.example.listviewtest

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.example.maguitosbluetoothapp.R

//This is the RecycleView adapter for the bluetooth devices list. This is a standard Recyclerview adapter as used for lists in Android.
// When implemented, I did not understand the usage tof the horizontal decorator and just made a horizontal line in the XML to separate
// the cells. This is also an improvements which can be done in this app...
class RecyclerViewAdapter(context: Context, devices: List<BluetoothDevice>, onNoteListener: OnNoteListener): RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

    val btDevices:List<BluetoothDevice>
    val kontext: Context
    val noteListener: OnNoteListener

    init {
        btDevices = devices
        kontext = context
        noteListener = onNoteListener
    }

    //Overrides the creation of the viewholder to use the internal class declared here
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view:View = LayoutInflater.from(parent.context).inflate(R.layout.bluetooth_device, parent, false)
        val holder = ViewHolder(view, noteListener)
        return  holder
    }

    override fun getItemCount(): Int {
        return btDevices.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bluetoothDevice = btDevices.get(position)
        holder.deviceName.setText(bluetoothDevice.name)
        holder.deviceAddress.setText(bluetoothDevice.address)
    }

    // View holder. See the bluetooth_device.xml to se what the viewholder contains.
    class ViewHolder(itemView: View, noteListener: OnNoteListener): RecyclerView.ViewHolder(itemView), View.OnClickListener {

        var deviceName: TextView
        var deviceAddress: TextView
        var onNoteListener: OnNoteListener

        override fun onClick(v: View?) {
            v?.setBackgroundColor(0)
            onNoteListener.onNoteClick(adapterPosition)
        }

        init {
            deviceName = itemView.findViewById<TextView>(R.id.device_name)
            deviceAddress = itemView.findViewById<TextView>(R.id.device_address)
            onNoteListener = noteListener
            itemView.setOnClickListener(this)
        }

    }

    interface OnNoteListener {
        fun onNoteClick(position: Int)
    }
}