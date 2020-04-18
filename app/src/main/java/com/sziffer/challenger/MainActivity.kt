package com.sziffer.challenger

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.gms.maps.MapView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var challengeReference: DatabaseReference

    private lateinit var sharedPreferences: SharedPreferences
    private  var uid: String? = null

    private lateinit var newChallengeButton: Button
    private lateinit var showMoreChallengeButton: Button
    private lateinit var recordActivityButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FirebaseRecyclerAdapter<Challenge, UserViewHolder>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!checkPermissions())
            permissionRequest()

        mAuth = FirebaseAuth.getInstance()

        startDataDownloaderWorkManager(applicationContext)

        Thread(Runnable {
            try {
                val mv =
                    MapView(applicationContext)
                mv.onCreate(null)
                mv.onPause()
                mv.onDestroy()
            } catch (ignored: Exception) {
            }
        }).start()

        testButton.setOnClickListener {
            val dbHelper = ChallengeDbHelper(this)
            FirebaseManager.currentUsersChallenges?.addListenerForSingleValueEvent(object :
                ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    //Log.e(this@DataDownloaderWorker::class.java.simpleName, p0.details)
                }

                override fun onDataChange(p0: DataSnapshot) {

                    for (data in p0.children) {
                        val key = data.key.toString()
                        if (dbHelper.getChallengeByFirebaseId(key) == null) {
                            val challenge: Challenge? = data.getValue(Challenge::class.java)
                            if (challenge?.firebaseId?.isEmpty()!!) {
                                Log.i("iD", "EMPTY")
                                challenge.firebaseId = challenge.id
                            }
                            Log.i("CHALLENGE IS", "The challenge is: $challenge")
                        }
                    }
                }
            })
        }

        sharedPreferences = getSharedPreferences(UID_SHARED_PREF, Context.MODE_PRIVATE)

        recordActivityButton = findViewById(R.id.recordActivityButton)
        showMoreChallengeButton = findViewById(R.id.showMoreButton)
        newChallengeButton = findViewById(R.id.createChallengeButton)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this.applicationContext)

        userProfileimageButton.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        recordActivityButton.setOnClickListener {
            intent = Intent(applicationContext, ChallengeRecorderActivity::class.java)
            intent.putExtra(ChallengeRecorderActivity.CHALLENGE, false)
            startActivity(intent)
        }

        createChallengeButton.setOnClickListener {
            startActivity(Intent(this, CreateChallengeActivity::class.java))
        }

        showMoreChallengeButton.setOnClickListener {

            startActivity(
                Intent(this, AllChallengeActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }


        Log.i("ID", sharedPreferences.getString(FINAL_USER_ID,"").toString())
    }

    override fun onStart() {
        super.onStart()
        setUpRecyclerView()

    }

    private fun setUpRecyclerView() {

        //TODO(make available offline)

        if (FirebaseManager.publicChallenges == null) return

        val options: FirebaseRecyclerOptions<Challenge> =
            FirebaseRecyclerOptions.Builder<Challenge>()
                .setQuery(FirebaseManager.publicChallenges!!, Challenge::class.java)
                .build()

        adapter =
            object :
                FirebaseRecyclerAdapter<Challenge,UserViewHolder>(options) {
                override fun onBindViewHolder(
                    holder: UserViewHolder,
                    position: Int,
                    model: Challenge
                ) {
                    holder.distance.text =
                        getString(R.string.distance) + ": " + getStringFromNumber(
                            1,
                            model.dst
                        ) + " km"
                    holder.duration.text =
                        getString(R.string.duration) + ": " + DateUtils.formatElapsedTime(model.dur)

                    val image = if (model.type == "cycling") {
                        R.drawable.cycling
                    } else
                        R.drawable.running

                    holder.type.setImageResource(image)
                    holder.name.text = model.name
                    holder.avg.text = getString(R.string.avgspeed) + ": " + getStringFromNumber(
                        1,
                        model.avg
                    ) + " km/h"

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

        val distance: TextView = itemView.findViewById(R.id.challengeDistanceText)
        val duration: TextView = itemView.findViewById(R.id.challengeDurationText)
        val type: ImageView = itemView.findViewById(R.id.challengeTypeImageView)
        val name: TextView = itemView.findViewById(R.id.challengeNameTextView)
        val avg: TextView = itemView.findViewById(R.id.avgSpeedText)


    }

    private fun permissionRequest() {
        val locationApproved = ActivityCompat
            .checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (locationApproved) {
                val hasBackgroundLocationPermission = ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasBackgroundLocationPermission) {
                    // handle location update
                } else {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        REQUEST_CODE_BACKGROUND
                    )
                }
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    REQUEST_CODE_BACKGROUND
                )
            }
        } else {
            // App doesn't have access to the device's location at all. Make full request
            // for permission.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                    REQUEST
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ),
                   REQUEST
                )
            }
        }

    }
    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

        } else {
            return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) && PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    companion object {
        private const val REQUEST = 200
        private const val REQUEST_CODE_BACKGROUND = 1545
        //final uid which is used for authorization
        const val FINAL_USER_ID = "finalUid"
        //key for the user's sharedPref
        const val UID_SHARED_PREF = "sharedPrefUid"
        //get unregistered user id
        const val NOT_REGISTERED = "registered"
        //get users list from firebase
        private const val USERS_DATA = "users"
        //get public challenges from firebase
        private const val CHALLENGES_DATA = "challenges"
    }

}
