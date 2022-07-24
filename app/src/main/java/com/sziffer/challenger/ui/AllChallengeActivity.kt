package com.sziffer.challenger.ui


import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sziffer.challenger.adapters.ChallengeRecyclerViewAdapter
import com.sziffer.challenger.database.ChallengeDbHelper
import com.sziffer.challenger.databinding.ActivityAllChallengeBinding
import com.sziffer.challenger.model.challenge.Challenge
import com.sziffer.challenger.sync.KEY_DELETE
import com.sziffer.challenger.sync.updateSharedPrefForSync
import com.sziffer.challenger.ui.user.UserProfileActivity
import java.text.SimpleDateFormat


class AllChallengeActivity : AppCompatActivity() {

    private lateinit var dbHelper: ChallengeDbHelper
    private lateinit var challenges: ArrayList<Challenge>
    private lateinit var binding: ActivityAllChallengeBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAllChallengeBinding.inflate(layoutInflater)
        setContentView(binding.root)



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

        binding.profileButton.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        with(binding.allChallengeRecyclerView) {
            layoutManager = LinearLayoutManager(applicationContext)
            adapter = ChallengeRecyclerViewAdapter(challenges, applicationContext)
            addItemDecoration(
                DividerItemDecoration(
                    context,
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
                val pos = viewHolder.layoutPosition.also {
                    Log.i(this::class.java.simpleName, it.toString())
                }
                val challenge = challenges[pos]

                challenges.removeAt(pos)
                binding.allChallengeRecyclerView.adapter?.notifyItemRemoved(pos)
                updateSharedPrefForSync(applicationContext, challenge.firebaseId, KEY_DELETE)
                dbHelper.deleteChallenge(challenge.id)
            }

        }).attachToRecyclerView(binding.allChallengeRecyclerView)
    }

    override fun onStop() {
        dbHelper.close()
        super.onStop()
    }
}
