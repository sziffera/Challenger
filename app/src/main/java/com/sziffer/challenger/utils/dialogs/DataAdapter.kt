package com.sziffer.challenger.utils.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R

class DataAdapter(
    private val mDataSet: ArrayList<String>,
    internal var recyclerViewItemClickListener: RecyclerViewItemClickListener
) : RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ViewHolder {

        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.voice_coach_item, parent, false)

        return ViewHolder(v)

    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.mTextView.text = mDataSet[i]
    }

    override fun getItemCount(): Int {
        return mDataSet.size
    }


    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener {

        var mTextView: TextView = v.findViewById(R.id.voiceCoachItemTextView)

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            recyclerViewItemClickListener.clickOnVoiceCoachItem(mDataSet[this.bindingAdapterPosition])
        }
    }

    interface RecyclerViewItemClickListener {
        fun clickOnVoiceCoachItem(data: String)
    }
}