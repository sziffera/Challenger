package com.sziffer.challenger.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R
import kotlinx.android.synthetic.main.list_dialog_layout.*

class CustomListDialog(
    private var activity: Activity,
    private var adapter: RecyclerView.Adapter<*>
) :
    Dialog(activity) {

    private var recyclerView: RecyclerView? = null
    private var mLayoutManager: RecyclerView.LayoutManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.list_dialog_layout)

        recyclerView = recycler_view
        mLayoutManager = LinearLayoutManager(activity)
        recyclerView?.layoutManager = mLayoutManager
        recyclerView?.addItemDecoration(
            DividerItemDecoration(
                activity.applicationContext,
                DividerItemDecoration.VERTICAL
            )
        )
        recyclerView?.adapter = adapter

    }

}