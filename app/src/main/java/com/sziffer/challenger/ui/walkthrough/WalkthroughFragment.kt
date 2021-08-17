package com.sziffer.challenger.ui.walkthrough

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sziffer.challenger.databinding.FragmentWalkthroughBinding

class WalkthroughFragment : Fragment() {

    private var _binding: FragmentWalkthroughBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalkthroughBinding.inflate(layoutInflater)
        return binding.root
    }

    companion object {
        const val KEY_TITLE = "keyTitle"
        const val KEY_DESCRIPTION = "keyDesc"
        const val KEY_IMAGE = "keyImg"
    }
}