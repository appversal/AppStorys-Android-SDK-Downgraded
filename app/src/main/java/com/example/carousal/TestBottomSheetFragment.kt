package com.example.carousal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appversal.appstorys.ui.xml.OverlayLayoutView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TestBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_test_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // The root OverlayLayoutView (insideBottomSheet=true) needs the Activity
        // so TestUserButton can resolve it for screen capture.
        view.findViewById<OverlayLayoutView>(R.id.bs_overlay)
            .setActivity(requireActivity())

        view.findViewById<Button>(R.id.xml_bs_close_btn)
            .setOnClickListener { dismiss() }

        // Wire up the RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.bs_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = BsProductAdapter(makeFakeProducts())

        // Expand fully so the list has room to scroll — makes Bug #3/#4 easier to trigger
        recyclerView.post {
            val sheet = (dialog as? BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                BottomSheetBehavior.from(it).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }
        }

        // Tell the SDK which screen this bottom sheet represents
        App.appStorys.getScreenCampaigns("Bottom Sheet XML")
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    data class Product(val name: String, val category: String, val price: String)

    private fun makeFakeProducts() = listOf(
        Product("Wireless Earbuds",   "Electronics", "₹1,299"),
        Product("Yoga Mat",           "Sports",      "₹799"),
        Product("Coffee Mug",         "Kitchen",     "₹349"),
//        Product("Running Shoes",      "Sports",      "₹2,499"),
//        Product("Desk Lamp",          "Home Décor",  "₹899"),
//        Product("Phone Stand",        "Accessories", "₹249"),
//        Product("Water Bottle",       "Kitchen",     "₹599"),
//        Product("Notebook Set",       "Stationery",  "₹199"),
//        Product("Bluetooth Speaker",  "Electronics", "₹1,799"),
//        Product("Sunglasses",         "Fashion",     "₹699"),
//        Product("Backpack",           "Bags",        "₹1,499"),
//        Product("Resistance Bands",   "Sports",      "₹449"),
    )

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class BsProductAdapter(
        private val items: List<Product>
    ) : RecyclerView.Adapter<BsProductAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon:     ImageView = itemView.findViewById(R.id.bs_item_icon)
            val name:     TextView  = itemView.findViewById(R.id.bs_item_name)
            val category: TextView  = itemView.findViewById(R.id.bs_item_category)
            val price:    TextView  = itemView.findViewById(R.id.bs_item_price)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bs_product, parent, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.name.text     = item.name
            holder.category.text = item.category
            holder.price.text    = item.price

            // Alternate background tint so recycled views are visually obvious during testing
            val tint = if (position % 2 == 0) "#EEF2FF" else "#FFF3E0"
            holder.icon.setBackgroundColor(android.graphics.Color.parseColor(tint))
        }
    }
}