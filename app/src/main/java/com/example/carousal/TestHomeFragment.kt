package com.example.carousal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.carousal.databinding.ActivityHomeBinding
import kotlinx.coroutines.launch

class TestHomeFragment : Fragment() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // This is the inflation path that triggers the bug
        binding = DataBindingUtil.inflate(
            inflater, R.layout.activity_home, container, false
        )

        // 🔑 Keep commented to reproduce, uncomment to verify the fix
//        binding.rootOverlay.setActivity(requireActivity())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.openBottomSheet.setOnClickListener {
            TestBottomSheetFragment().show(parentFragmentManager, "TestBottomSheet")
        }
        binding.openMoreScreen.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), MoreActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        App.appStorys.getScreenCampaigns("Home Screen Kotlin XML", emptyList())
        lifecycleScope.launch {
            App.appStorys.setUserProperties(mapOf("hello" to "world"))
        }
    }
}