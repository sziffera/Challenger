package com.example.challenger

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.challenger.sync.KEY_DELETE
import com.example.challenger.sync.updateSharedPrefForSync


class AllChallengeActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var recyclerView: RecyclerView
    private lateinit var challenges: ArrayList<Challenge>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_challenge)

        recyclerView = findViewById(R.id.allChallengeRecyclerView)
        dbHelper = ChallengeDbHelper(this)
        challenges = dbHelper.getAllChallenges()

        with(recyclerView) {
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = ChallengeRecyclerViewAdapter(challenges, applicationContext)
            addItemDecoration(
                DividerItemDecoration(
                    recyclerView.context,
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(0, ItemTouchHelper.LEFT)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition.also {
                    Log.i(this::class.java.simpleName, it.toString())
                }
                val challenge = challenges[pos]

                challenges.removeAt(pos)
                recyclerView.adapter?.notifyItemRemoved(pos)
                updateSharedPrefForSync(applicationContext, challenge.id, KEY_DELETE)
                dbHelper.deleteChallenge(challenge.id)
            }

        }).attachToRecyclerView(recyclerView)
    }

    override fun onStop() {
        dbHelper.close()
        super.onStop()
    }
}
