package com.example.challenger


import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class ChallengeRecyclerViewAdapter(
    private val challenges: ArrayList<Challenge>
) :
    RecyclerView.Adapter<ChallengeRecyclerViewAdapter.ViewHolder>() {

    class ViewHolder(
        itemView: View,
        private val recyclerViewOnClickListener: RecyclerViewOnClickListener
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val type: TextView = itemView.findViewById(R.id.challengeTypeText)
        private val item: LinearLayout = itemView.findViewById(R.id.challengeItemLinearLayout)
        val distance: TextView = itemView.findViewById(R.id.challengeDistanceText)
        val duration: TextView = itemView.findViewById(R.id.challengeDurationText)
        val avgSpeed: TextView = itemView.findViewById(R.id.avgSpeedText)
        val maxSpeed: TextView = itemView.findViewById(R.id.maxSpeedText)
        val name: TextView = itemView.findViewById(R.id.challengeNameTextView)
        override fun onClick(v: View) {
            recyclerViewOnClickListener.itemClicked(itemView, this.layoutPosition)
        }

        init {
            item.setOnClickListener(this)
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context: Context = parent.context
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.challange_item, parent, false)
        return ViewHolder(itemView, object : RecyclerViewOnClickListener {
            override fun itemClicked(v: View, pos: Int) {
                val intent = Intent(context, ChallengeDetailsActivity::class.java)
                intent.putExtra("challenge", challenges[pos])
                intent.putExtra("start", true)
                context.startActivity(intent)
            }

        })
    }

    override fun getItemCount(): Int {
        return challenges.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val challenge = challenges[position]
        with(holder) {
            name.text = challenge.n
            type.text = challenge.type
            duration.text = DateUtils.formatElapsedTime(challenge.dur)
            distance.text = getStringFromNumber(3, challenge.dst)
            avgSpeed.text = getStringFromNumber(1, challenge.avg)
            maxSpeed.text = getStringFromNumber(1, challenge.mS)

        }
    }
}