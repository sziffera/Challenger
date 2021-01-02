package com.sziffer.challenger


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.utils.getStringFromNumber


class ChallengeRecyclerViewAdapter(
    private val challenges: ArrayList<Challenge>,
    private val mContext: Context
) :
    RecyclerView.Adapter<ChallengeRecyclerViewAdapter.ViewHolder>() {


    class ViewHolder(
        itemView: View,
        private val recyclerViewOnClickListener: RecyclerViewOnClickListener
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val type: ImageView = itemView.findViewById(R.id.challengeTypeImageView)
        private val item: ImageView = itemView.findViewById(R.id.DetailsImageButton)
        val distance: TextView = itemView.findViewById(R.id.challengeDistanceText)
        val duration: TextView = itemView.findViewById(R.id.challengeDurationText)
        val avgSpeed: TextView = itemView.findViewById(R.id.avgSpeedText)
        val name: TextView = itemView.findViewById(R.id.challengeNameTextView)
        val date: TextView = itemView.findViewById(R.id.dateTextView)
        override fun onClick(v: View) {
            item.startAnimation(AlphaAnimation(1f, 0.8f))
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
                intent.putExtra(ChallengeDetailsActivity.CHALLENGE_ID, challenges[pos].id.toLong())
                intent.putExtra(ChallengeDetailsActivity.IS_IT_A_CHALLENGE, true)
                context.startActivity(intent)
            }

        })
    }

    override fun getItemCount(): Int {
        return challenges.size
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val challenge = challenges[position]
        with(holder) {
            name.text = challenge.name
            val image = if (challenge.type == mContext.getString(R.string.cycling)) {
                R.drawable.cycling
            } else
                R.drawable.running
            type.setImageResource(image)

            duration.text =
                mContext.getString(R.string.duration) + ": " + DateUtils.formatElapsedTime(challenge.dur)
            distance.text =
                mContext.getString(R.string.distance) + ": " + getStringFromNumber(
                    1,
                    challenge.dst
                ) + " km"
            avgSpeed.text =
                mContext.getString(R.string.avgspeed) + ": " + getStringFromNumber(
                    1,
                    challenge.avg
                ) + " km/h"
            val myDate: String = challenge.date
            date.text = myDate.split(".")[0]

        }
    }

    interface RecyclerViewOnClickListener {
        fun itemClicked(v: View, pos: Int)
    }
}