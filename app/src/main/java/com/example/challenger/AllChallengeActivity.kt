package com.example.challenger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


class AllChallengeActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_challenge)

        recyclerView = findViewById(R.id.allChallengeRecyclerView)
        dbHelper = ChallengeDbHelper(this)

        with(recyclerView) {
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = ChallengeRecyclerViewAdapter(dbHelper.getAllChallenges())
            addItemDecoration(
                DividerItemDecoration(
                    recyclerView.context,
                    DividerItemDecoration.VERTICAL
                )
            )
        }

    }
}
