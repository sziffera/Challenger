package com.example.challenger

import android.view.View

interface RecyclerViewOnClickListener {
    fun itemClicked(v: View, pos: Int)
}