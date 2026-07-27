//package com.example.carousal
//
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import androidx.databinding.DataBindingUtil
//import androidx.fragment.app.Fragment
//import androidx.lifecycle.lifecycleScope
//import com.example.carousal.databinding.ActivityHomeBinding
//import kotlinx.coroutines.launch
//
//class TestHomeFragment : Fragment() {
//
//    private lateinit var binding: ActivityHomeBinding
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View {
//        // This is the inflation path that triggers the bug
//        binding = DataBindingUtil.inflate(
//            inflater, R.layout.activity_home, container, false
//        )
//
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        App.appStorys.getScreenCampaigns("Home Screen Kotlin XML", listOf("widget_one"))
//
//        binding.openBottomSheet.setOnClickListener {
//            TestBottomSheetFragment().show(parentFragmentManager, "TestBottomSheet")
//        }
//        binding.openMoreScreen.setOnClickListener {
//            startActivity(android.content.Intent(requireContext(), MoreActivity::class.java))
//        }
//
//    }
//
//    override fun onResume() {
//        super.onResume()
////        App.appStorys.getScreenCampaigns("Home Screen Kotlin XML", listOf("widget_one"))
//    }
//}

package com.example.carousal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.carousal.databinding.ActivityHomeBinding

/**
 * ── THE BUG (client's setup) ────────────────────────────────────────────────
 *
 * The client pushes the next screen ON TOP of this fragment:
 *
 *     parentFragmentManager.beginTransaction()
 *         .add(R.id.fragment_container, TestNextFragment())   // add, NOT replace
 *         .addToBackStack(null)
 *         .commit()
 *
 * With add(), this fragment NEVER leaves the RESUMED state — its view is
 * simply covered by the new fragment's view. Consequences:
 *
 *  1. TestNextFragment calls getScreenCampaigns("Cashbook Tab"). The SDK
 *     keeps ONE global campaign state, so it clears Home's campaigns
 *     (campaigns.emit(emptyList()) + OverlayContainer.clearAll()) and sets
 *     currentScreen = "Cashbook Tab". The Home WidgetView underneath is
 *     still collecting that flow → it goes blank right away.
 *
 *  2. When the user pops back, NO lifecycle callback fires on this fragment:
 *       - onViewCreated: view was never destroyed
 *       - onStart:       fragment never stopped
 *       - onResume:      fragment never paused
 *     That is exactly why putting getScreenCampaigns() in onStart()/onResume()
 *     changed nothing — those methods are simply never invoked on back-pop
 *     in this navigation pattern.
 *
 * ── THE FIX ─────────────────────────────────────────────────────────────────
 *
 * The only reliable signal for "the user came back to me" in an add()-based
 * back stack is FragmentManager.OnBackStackChangedListener. When the back
 * stack changes and this fragment is the top visible fragment again, we
 * re-fetch its campaigns. getScreenCampaigns() sees currentScreen changed
 * ("Cashbook Tab" → "Home Screen Kotlin XML"), clears the next screen's
 * state, and re-emits the Home campaigns → the widgets come back.
 */
class TestHomeFragment : Fragment() {

    private lateinit var binding: ActivityHomeBinding

    companion object {
        private const val SCREEN_NAME = "Home Screen Kotlin XML"
        private val POSITIONS = listOf("widget_one")
    }

    // ── THE FIX: detect back-stack pops and re-fetch when we are on top again
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        // Are we the top-most fragment in the container again?
        val topFragment = parentFragmentManager.fragments.lastOrNull()
        if (topFragment === this && isAdded) {
            // We're visible again → restore this screen's campaigns.
            // Safe to call unconditionally: if currentScreen is already
            // SCREEN_NAME the SDK skips the clear-and-reset branch.
            App.appStorys.getScreenCampaigns(SCREEN_NAME, POSITIONS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(
            inflater, R.layout.activity_home, container, false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initial fetch — same as the client does today
        App.appStorys.getScreenCampaigns(SCREEN_NAME, POSITIONS)

        // ── THE FIX: register the back-stack listener.
        // (Comment this line out to reproduce the client's bug 1:1 —
        //  push next fragment, pop back, widgets stay gone.)
//        parentFragmentManager.addOnBackStackChangedListener(backStackListener)

        binding.openBottomSheet.setOnClickListener {
            TestBottomSheetFragment().show(parentFragmentManager, "TestBottomSheet")
        }

        binding.openMoreScreen.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), MoreActivity::class.java))
        }

        // ── THE REPRO: push the next screen ON TOP of Home, exactly like
        // the client's app. add() keeps this fragment RESUMED underneath,
        // so no lifecycle callback fires when the user comes back.
        binding.pushNextFragment.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .add(R.id.fragment_container, TestNextFragment())  // add, NOT replace
                .addToBackStack("next")
                .commit()
        }
    }

    override fun onDestroyView() {
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        // NOTE: with add()-on-top navigation this is NOT called when the user
        // pops back from TestNextFragment — the fragment never paused.
        // It only fires when returning from another Activity (e.g. MoreActivity),
        // which is why we keep the fetch here too for that path:
        App.appStorys.getScreenCampaigns(SCREEN_NAME, POSITIONS)
    }
}