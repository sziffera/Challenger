package com.example.challenger

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*


class MainActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var mRef: DatabaseReference
    private lateinit var challengeReference: DatabaseReference

    private lateinit var sharedPreferences: SharedPreferences
    private  var uid: String? = null

    private lateinit var newChallengeButton: Button
    private lateinit var recordActivityButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FirebaseRecyclerAdapter<Challenge, UserViewHolder>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        mRef = database.getReference("users")
        challengeReference = database.getReference("challenges").apply {
            keepSynced(true)
        }

        sharedPreferences = getSharedPreferences("uid", Context.MODE_PRIVATE)

        recordActivityButton = findViewById(R.id.recordActivityButton)
        newChallengeButton = findViewById(R.id.createChallengeButton)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this.applicationContext)

        recordActivityButton.setOnClickListener {
            intent = Intent(applicationContext,ChallengeRecorderActivity::class.java)
            startActivity(intent)
        }


        uid = if(sharedPreferences.getString("offline user", null) != null && mAuth.currentUser == null) {
            sharedPreferences.getString("offline user",System.nanoTime().toString()).toString()

        } else {
            mAuth.currentUser?.uid!!
        }



        Log.i("ID", uid.toString())
/*
        mRef.child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                Log.i("MAIN", p0.details)

            }

            override fun onDataChange(p0: DataSnapshot) {
                Log.i("SHOULD BE EMPTY", p0.child("username").value.toString())
            }

        })


 */

    }

    override fun onStart() {
        super.onStart()
        setUpRecyclerView()

    }

    private fun setUpRecyclerView() {
        val options: FirebaseRecyclerOptions<Challenge> =
            FirebaseRecyclerOptions.Builder<Challenge>()
                .setQuery(challengeReference,Challenge::class.java)
                .build()

        adapter =
            object :
                FirebaseRecyclerAdapter<Challenge,UserViewHolder>(options) {
                override fun onBindViewHolder(
                    holder: UserViewHolder,
                    position: Int,
                    model: Challenge
                ) {
                    holder.distance.text = model.distance.toString() + " km"
                    holder.duration.text = model.duration.toString() + " minutes"
                    holder.type.text = model.type
                }

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
                    val context = parent.context
                    val inflater = LayoutInflater.from(context)
                    val v: View =
                        inflater.inflate(R.layout.challange_item, parent, false)
                    return UserViewHolder(v)
                }
            }

        recyclerView.adapter = adapter
        adapter.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter.stopListening()
    }

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val distance: TextView = itemView.findViewById<TextView>(R.id.challengeDistanceText)
        val duration: TextView = itemView.findViewById<TextView>(R.id.challengeDurationText)
        val type: TextView = itemView.findViewById<TextView>(R.id.challengeTypeText)

    }

}
