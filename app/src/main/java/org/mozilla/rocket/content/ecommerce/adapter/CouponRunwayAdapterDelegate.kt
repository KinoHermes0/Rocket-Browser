package org.mozilla.rocket.content.ecommerce.adapter

import android.view.View
import androidx.recyclerview.widget.LinearSnapHelper
import kotlinx.android.synthetic.main.item_runway_list.*
import org.mozilla.focus.R
import org.mozilla.rocket.adapter.AdapterDelegate
import org.mozilla.rocket.adapter.AdapterDelegatesManager
import org.mozilla.rocket.adapter.DelegateAdapter
import org.mozilla.rocket.content.ecommerce.HorizontalSpaceItemDecoration
import org.mozilla.rocket.content.ecommerce.ShoppingViewModel

class CouponRunwayAdapterDelegate(private val shoppingViewModel: ShoppingViewModel) : AdapterDelegate {
    override fun onCreateViewHolder(view: View): DelegateAdapter.ViewHolder =
            CouponRunwayListViewHolder(view, shoppingViewModel)
}

class CouponRunwayListViewHolder(
    override val containerView: View,
    private val shoppingViewModel: ShoppingViewModel
) : DelegateAdapter.ViewHolder(containerView) {

    private var adapter = DelegateAdapter(
        AdapterDelegatesManager().apply {
            add(CouponRunwayItem::class, R.layout.item_runway, CouponRunwayItemAdapterDelegate(shoppingViewModel))
        }
    )

    init {
        val spaceWidth = itemView.resources.getDimensionPixelSize(R.dimen.card_space_width)
        val padding = itemView.resources.getDimensionPixelSize(R.dimen.card_padding)
        runway_list.addItemDecoration(HorizontalSpaceItemDecoration(spaceWidth, padding))
        runway_list.adapter = this@CouponRunwayListViewHolder.adapter
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(runway_list)
    }

    override fun bind(uiModel: DelegateAdapter.UiModel) {
        val runway = uiModel as CouponRunway
        adapter.setData(runway.runwayItems)
    }
}

data class CouponRunway(val runwayItems: List<CouponRunwayItem>) : DelegateAdapter.UiModel()