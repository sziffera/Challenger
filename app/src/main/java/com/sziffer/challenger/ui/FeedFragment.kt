package com.sziffer.challenger.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sziffer.challenger.R
import com.sziffer.challenger.databinding.FragmentFeedBinding
import com.sziffer.challenger.sync.DATA_DOWNLOADER_TAG
import com.sziffer.challenger.sync.startDataDownloaderWorkManager
import com.sziffer.challenger.utils.*
import com.sziffer.challenger.viewmodels.MainViewModel

class FeedFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener, NetworkStateListener {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var myNetworkCallback: MyNetworkCallback
    private lateinit var userIdSharedPreferences: SharedPreferences

    private var challengeAdapter: ChallengeRecyclerViewAdapter? = null

    private val viewModel: MainViewModel by activityViewModels()

    private var connected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectivityManager = requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        myNetworkCallback = MyNetworkCallback(this, connectivityManager)
        userIdSharedPreferences =
            requireContext().getSharedPreferences(UID_SHARED_PREF, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment

        _binding = FragmentFeedBinding.inflate(layoutInflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.setHasFixedSize(true)
        binding.swipeRefreshLayout.setOnRefreshListener(this)

        binding.findNearbyChallengesButton.setOnClickListener {
            startActivity(Intent(requireContext(), NearbyChallengesActivity::class.java))
        }
        viewModel.fetchChallenges(requireContext())

        viewModel.callObserveWork.observe(viewLifecycleOwner, {
            Log.d("OBSERVE", it.toString())
            if (it) {
                observeWork()
                viewModel.turnOfObserver()
            }

        })

        viewModel.challengesLiveData.observe(viewLifecycleOwner, {
            with(binding) {
                if (it.isEmpty()) return@observe
                swipeRefreshLayout.visibility = View.VISIBLE
                emptyViewLinearLayout.visibility = View.GONE
                with(recyclerView) {
                    challengeAdapter = ChallengeRecyclerViewAdapter(it, requireContext())
                    adapter = challengeAdapter
                    addItemDecoration(
                        DividerItemDecoration(
                            recyclerView.context,
                            DividerItemDecoration.VERTICAL
                        )
                    )
                }
            }
        })


        return binding.root
    }

    override fun onStart() {
        if (connectivityManager.allNetworks.isEmpty()) {
            connected = false
        }
        myNetworkCallback.registerCallback()
        super.onStart()

    }

    override fun onStop() {
        myNetworkCallback.unregisterCallback()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        challengeAdapter = null
        binding.recyclerView.adapter = null
        _binding = null
    }

    // swipe refresh layout helper method
    override fun onRefresh() {
        if (!shouldRefreshDataSet(UpdateTypes.DATA_SYNC, 60, requireContext())) {
            binding.swipeRefreshLayout.isRefreshing = false
            Toast.makeText(
                requireContext(), getString(R.string.last_update_warning),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        startDataDownloaderWorkManager(requireContext())
        observeWork()
        if (!connected) {
            Toast.makeText(
                requireContext(), getString(R.string.no_internet_connection_will_update),
                Toast.LENGTH_LONG
            ).show()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun noInternetConnection() {
        connected = false
    }

    override fun connectedToInternet() {
        connected = true
    }

    private fun observeWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(DATA_DOWNLOADER_TAG)
            .observe(viewLifecycleOwner, { workInfo ->
                if (workInfo != null && workInfo[0].state == WorkInfo.State.SUCCEEDED) {
                    Log.i("MAIN", "WorkManager succeeded")
                    binding.swipeRefreshLayout.isRefreshing = false
                    viewModel.fetchChallenges(requireContext(), startDownloader = false)
                    updateRefreshDate(UpdateTypes.DATA_SYNC, requireContext())
                }
            })
    }

    companion object {
        private const val SHOWCASE_ID = "MainActivity"

        //key for the user's sharedPref
        const val UID_SHARED_PREF = "sharedPrefUid"

        //get unregistered user id
        const val NOT_REGISTERED = "registered"
    }
}