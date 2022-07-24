package com.sziffer.challenger.ui.custom

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.geofire.GeoLocation
import com.sziffer.challenger.adapters.PublicChallengeRecyclerViewAdapter
import com.sziffer.challenger.model.challenge.PublicChallenge
import com.sziffer.challenger.utils.extensions.dp

@SuppressLint("ViewConstructor")
class NearbyChallengeCategoryHolder(
    context: Context,
    challenges: ArrayList<PublicChallenge>,
    private val title: String,
    currentLocation: GeoLocation
) : LinearLayout(context) {

    var recyclerView: RecyclerView? = null
    var rvAdapter: PublicChallengeRecyclerViewAdapter? = null

    init {
        this.orientation = VERTICAL

        rvAdapter = PublicChallengeRecyclerViewAdapter(
            challenges, context,
            GeoLocation(currentLocation.latitude, currentLocation.longitude)
        )

        recyclerView = RecyclerView(context).apply {
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    setMargins(12.dp, 6.dp, 12.dp, 0)
                }
            layoutManager = LinearLayoutManager(
                context,
                LinearLayoutManager.HORIZONTAL, false
            )
            adapter = rvAdapter
        }

        this.addView(
            TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        setMargins(12.dp, 12.dp, 0, 0)
                    }
                text = title
            }
        )
        this.addView(
            recyclerView!!
        )
    }

}