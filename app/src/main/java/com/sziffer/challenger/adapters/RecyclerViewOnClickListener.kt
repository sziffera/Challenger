package com.sziffer.challenger.adapters

import android.view.View

interface RecyclerViewOnClickListener {
    fun itemClicked(v: View, pos: Int)
}