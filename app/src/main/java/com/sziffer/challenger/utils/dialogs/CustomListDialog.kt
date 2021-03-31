package com.sziffer.challenger.utils.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.R


class CustomListDialog(
    private var activity: Activity,
    private var adapter: RecyclerView.Adapter<*>
) :
    Dialog(activity) {

    private lateinit var recyclerView: RecyclerView
    private var mLayoutManager: RecyclerView.LayoutManager? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.list_dialog_layout)

        recyclerView = findViewById(R.id.recycler_view)
        mLayoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = mLayoutManager
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                activity.applicationContext,
                DividerItemDecoration.VERTICAL
            )
        )
        recyclerView.adapter = adapter

    }

}