package com.sziffer.challenger.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.ItemPublicChallengeBinding
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.ui.PublicChallengeDetailsActivity
import com.sziffer.challenger.utils.getDrawable
import com.sziffer.challenger.utils.getStringFromNumber

class PublicChallengeRecyclerViewAdapter(
    private val challenges: ArrayList<PublicChallenge>,
    private val context: Context,
    private val currentLocation: GeoLocation
) : RecyclerView.Adapter<PublicChallengeRecyclerViewAdapter.ViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemPublicChallengeBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            object : RecyclerViewOnClickListener {
                override fun itemClicked(v: View, pos: Int) {

                    Log.d("RV_ADAPTER", challenges[pos].id)

                    val id = challenges[pos].id
                    val intent = Intent(
                        context.applicationContext,
                        PublicChallengeDetailsActivity::class.java
                    ).apply {
                        putExtra(PublicChallengeDetailsActivity.KEY_CHALLENGE_ID, id)
                    }

                    context.startActivity(intent)
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val challenge = challenges[position]

        val distanceToChallenge = GeoFireUtils.getDistanceBetween(
            currentLocation,
            GeoLocation(challenge.lat, challenge.lng)
        )

        val avgSpeed = (challenge.distance / challenge.duration.toDouble()) * 3.6
        with(holder) {
            binding.challengeTypeImageView.setImageDrawable(getDrawable(challenge.type, context))
            binding.distance.text = "${getStringFromNumber(1, challenge.distance / 1000.0)} km"
            binding.duration.text = DateUtils.formatElapsedTime(challenge.duration)
            binding.avgSpeed.text = "${getStringFromNumber(1, avgSpeed)} km/h"
            binding.elevationGainedTv.text = "${challenge.elevationGained} m"
            binding.distanceFromUserPosition.text = "${
                getStringFromNumber(
                    1,
                    distanceToChallenge / 1000.0
                )
            }${context.getString(R.string.away_from_you)}"
        }
    }

    override fun getItemCount(): Int = challenges.count()


    inner class ViewHolder(
        val binding: ItemPublicChallengeBinding,
        private val recyclerViewOnClickListener: RecyclerViewOnClickListener
    ) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.imageButton.setOnClickListener(this)
        }

        override fun onClick(p0: View?) {
            recyclerViewOnClickListener.itemClicked(itemView, this.layoutPosition)
        }
    }
}