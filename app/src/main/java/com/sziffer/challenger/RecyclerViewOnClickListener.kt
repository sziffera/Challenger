package com.sziffer.challenger

import android.view.View

interface RecyclerViewOnClickListener {
    fun itemClicked(v: View, pos: Int)
}