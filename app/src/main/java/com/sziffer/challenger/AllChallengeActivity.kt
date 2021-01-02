package com.sziffer.challenger


import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.model.Challenge
import com.sziffer.challenger.sync.KEY_DELETE
import com.sziffer.challenger.sync.updateSharedPrefForSync
import com.sziffer.challenger.user.UserProfileActivity
import kotlinx.android.synthetic.main.activity_all_challenge.*
import java.text.SimpleDateFormat


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
        challenges.sortWith(Comparator { o1, o2 ->
            if (o1.date.isEmpty() || o2.date.isEmpty()) 0
            else {
                val format = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                val date1 = format.parse(o1.date)!!
                val date2 = format.parse(o2.date)!!
                date1
                    .compareTo(date2)
            }
        })
        challenges.reverse()

        profileButton.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

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
                updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_DELETE)
                dbHelper.deleteChallenge(challenge.id)
            }

        }).attachToRecyclerView(recyclerView)
    }

    override fun onStop() {
        dbHelper.close()
        super.onStop()
    }
}
