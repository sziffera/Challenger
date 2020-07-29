package com.sziffer.challenger

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sziffer.challenger.sync.DATA_DOWNLOADER_TAG
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import com.sziffer.challenger.user.FirebaseManager
import com.sziffer.challenger.user.UserManager
import com.sziffer.challenger.user.UserProfileActivity
import kotlinx.android.synthetic.main.activity_main.*
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig
import java.text.SimpleDateFormat


class MainActivity : AppCompatActivity(), LifecycleObserver {

    private lateinit var sharedPreferences: SharedPreferences
    private var dbHelper: ChallengeDbHelper? = null
    private lateinit var newChallengeButton: Button
    private lateinit var showMoreChallengeButton: Button
    private lateinit var recordActivityButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i("MAIN", "OnCreate")

        if (!checkPermissions())
            permissionRequest()

        startDataDownloaderWorkManager(applicationContext)
        observeWork()

        userManager = UserManager(applicationContext)

        sharedPreferences = getSharedPreferences(UID_SHARED_PREF, Context.MODE_PRIVATE)

        if (FirebaseManager.isUserValid) {
            Log.i("MAIN", "the user is registered")
            if (userManager.username != null) {
                Log.i("MAIN", "set from usermanager")
                heyUserTextView.text = "Hey, " + userManager.username + "!"
            } else {
                FirebaseManager.currentUserRef
                    ?.child("username")?.addListenerForSingleValueEvent(object :
                    ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        Log.i("FIREBASE", p0.toString())
                        val name = p0.getValue(String::class.java) as String
                        heyUserTextView.text = "Hey, " + name + "!"
                        //also store the name for further usage
                        userManager.username = name
                    }
                })
            }


        } else {

            heyUserTextView.visibility = View.GONE
        }

        recordActivityButton = findViewById(R.id.recordActivityButton)
        showMoreChallengeButton = findViewById(R.id.showMoreButton)
        newChallengeButton = findViewById(R.id.createChallengeButton)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this.applicationContext)

        userProfileimageButton.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }



        takeATourButton.setOnClickListener {
            appTour()
        }

        createChallengeButton.setOnClickListener {
            startActivity(Intent(this, CreateChallengeActivity::class.java))
        }

        recordActivityButton.setOnClickListener {
            if (checkPermissions()) {

                val buttonSettings = getSharedPreferences("button", 0)
                with(buttonSettings.edit()) {
                    putBoolean("started", false)
                    apply()
                }

                val intent = Intent(applicationContext, ChallengeRecorderActivity::class.java)
                intent.putExtra(ChallengeRecorderActivity.CHALLENGE, false)
                startActivity(intent)
            } else {
                permissionRequest()
            }
        }

        showMoreChallengeButton.setOnClickListener {

            startActivity(
                Intent(this, AllChallengeActivity::class.java),
                ActivityOptions.makeSceneTransitionAnimation(this).toBundle()
            )
        }
    }

    override fun onStart() {
        Log.i("MAIN", "OnStart")
        super.onStart()
        setUpView()

    }

    override fun onStop() {
        Log.i("MAIN", "OnStop")
        dbHelper?.close()
        dbHelper = null
        super.onStop()
    }

    private fun setUpView() {
        Log.i("MAIN", "SetUpView called")
        dbHelper = ChallengeDbHelper(this)
        val list = dbHelper?.getAllChallenges() as MutableList<Challenge>

        list.sortWith(Comparator { o1, o2 ->
            if (o1.date.isEmpty() || o2.date.isEmpty()) 0
            else {
                val format = SimpleDateFormat("dd-MM-yyyy. HH:mm")
                val date1 = format.parse(o1.date)!!
                val date2 = format.parse(o2.date)!!
                date1
                    .compareTo(date2)
            }
        })
        list.reverse()

        if (list.isEmpty()) {
            chooseChallenge.text = getText(R.string.it_s_empty_here_let_s_do_some_sports)
            recyclerView.visibility = View.INVISIBLE
            takeATourTextView.visibility = View.VISIBLE
            emptyImageView.visibility = View.VISIBLE
            takeATourButton.visibility = View.VISIBLE
            showMoreChallengeButton.visibility = View.INVISIBLE

        } else {
            chooseChallenge.text = getText(R.string.choose_a_challenge)
            recyclerView.visibility = View.VISIBLE
            takeATourTextView.visibility = View.GONE
            emptyImageView.visibility = View.GONE
            takeATourButton.visibility = View.GONE
            showMoreChallengeButton.visibility = View.VISIBLE
            with(recyclerView) {
                adapter =
                    ChallengeRecyclerViewAdapter(list as ArrayList<Challenge>, this@MainActivity)
                addItemDecoration(
                    DividerItemDecoration(
                        recyclerView.context,
                        DividerItemDecoration.VERTICAL
                    )
                )
            }
        }
        dbHelper?.close()
    }

    private fun observeWork() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData(DATA_DOWNLOADER_TAG)
            .observe(this, Observer { workInfo ->
                if (workInfo != null && workInfo[0].state == WorkInfo.State.SUCCEEDED) {
                    Log.i("MAIN", "WorkManager succeeded")
                    setUpView()
                }
            })
    }

    private fun appTour() {
        MaterialShowcaseView.resetSingleUse(this, SHOWCASE_ID)
        val config = ShowcaseConfig()
        config.delay = 500
        val sequence = MaterialShowcaseSequence(this, SHOWCASE_ID)
        sequence.setConfig(config)

        sequence.addSequenceItem(
            MaterialShowcaseView.Builder(this)
                .setTarget(recordActivityButton)
                .setDismissText(getString(R.string.got_it))
                .setContentText(getString(R.string.record_activity_showcase_text))
                .withRectangleShape(true)
                .build()
        )

        sequence.addSequenceItem(
            MaterialShowcaseView.Builder(this)
                .setTarget(createChallengeButton)
                .setDismissText(getString(R.string.let_s_do_it))
                .setContentText(getString(R.string.create_challenge_showcase_text))
                .withRectangleShape(true)
                .build()
        )

        sequence.start()
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


        if (!locationApproved) {
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
        private const val SHOWCASE_ID = "MainActivity"
        private const val REQUEST = 200

        //final uid which is used for authorization
        const val FINAL_USER_ID = "finalUid"

        //key for the user's sharedPref
        const val UID_SHARED_PREF = "sharedPrefUid"

        //get unregistered user id
        const val NOT_REGISTERED = "registered"
    }

}
