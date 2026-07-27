package com.example.carousal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

/**
 * Replicates the client's "next screen".
 *
 * It is pushed ON TOP of TestHomeFragment with add() + addToBackStack()
 * (see TestHomeFragment). The moment this fragment calls
 * getScreenCampaigns("Cashbook Tab"), the SDK's single global campaign
 * state switches screens:
 *
 *   - OverlayContainer.clearAll()
 *   - campaigns.emit(emptyList())
 *   - currentScreen = "Cashbook Tab"
 *   - fetch + emit Cashbook Tab campaigns
 *
 * The Home fragment's WidgetView is still alive underneath, collecting
 * that same flow, so its widget disappears immediately.
 */
class TestNextFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_test_next, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Same pattern as the client: fetch in onViewCreated
        App.appStorys.getScreenCampaigns("Cashbook Tab", emptyList())

        view.findViewById<Button>(R.id.next_back_btn).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }
}