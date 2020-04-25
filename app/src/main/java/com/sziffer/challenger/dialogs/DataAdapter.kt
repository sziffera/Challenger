package com.sziffer.challenger.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R
import kotlinx.android.synthetic.main.voice_coach_item.view.*

class DataAdapter(
    private val mDataset: ArrayList<String>,
    internal var recyclerViewItemClickListener: RecyclerViewItemClickListener
) : RecyclerView.Adapter<DataAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, i: Int): ViewHolder {

        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.voice_coach_item, parent, false)

        return ViewHolder(v)

    }

    override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
        viewHolder.mTextView.text = mDataset[i]
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }


    inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v), View.OnClickListener {

        var mTextView: TextView = v.voiceCoachItemTextView

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            recyclerViewItemClickListener.clickOnVoiceCoachItem(mDataset[this.adapterPosition])
        }
    }

    interface RecyclerViewItemClickListener {
        fun clickOnVoiceCoachItem(data: String)
    }
}