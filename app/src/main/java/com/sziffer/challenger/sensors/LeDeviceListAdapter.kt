package com.sziffer.challenger.sensors

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.sziffer.challenger.R

class LeDeviceListAdapter(
    private val layoutInflater: LayoutInflater,
    private val context: Context
) : BaseAdapter() {

    private val mLeDevices: ArrayList<BluetoothDevice> = ArrayList()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        val viewHolder: ViewHolder
        val view: View
        if (convertView == null) {
            view = layoutInflater.inflate(R.layout.item_bluetooth, null)
            viewHolder = ViewHolder()
            viewHolder.deviceName = view.findViewById(R.id.bluetoothItemName)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = convertView.tag as ViewHolder
        }
        val device = mLeDevices[position]
        val deviceName = device.name
        if (deviceName.isNotEmpty())
            viewHolder.deviceName?.text = context.getString(R.string.unknown_device)
        else
            viewHolder.deviceName?.text = deviceName

        return view
    }

    override fun getItem(position: Int): Any = mLeDevices[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getCount(): Int = mLeDevices.count()

    fun addDevice(device: BluetoothDevice) {
        if (!mLeDevices.contains(device))
            mLeDevices.add(device)
    }

    fun clear() = mLeDevices.clear()

    fun getDevice(position: Int) = mLeDevices[position]

    private inner class ViewHolder() {
        var deviceName: TextView? = null
    }

}