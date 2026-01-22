package com.slimepop.asmr

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.slimepop.asmr.databinding.RowShopItemBinding

class ShopAdapter(
    private var items: List<ShopItem>,
    private val state: () -> ShopState,
    private val onBuy: (ShopItem) -> Unit,
    private val onEquip: (ShopItem) -> Unit
) : RecyclerView.Adapter<ShopAdapter.VH>() {

    data class ShopState(
        val entitlements: Entitlements,
        val equippedSkinId: String,
        val equippedSoundId: String,
        val priceLookup: (String) -> String?
    )

    class VH(val vb: RowShopItemBinding) : RecyclerView.ViewHolder(vb.root)

    fun submit(newItems: List<ShopItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = RowShopItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(vb)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val s = state()
        val ent = s.entitlements

        val owned = when (item.category) {
            ShopCategory.SKIN, ShopCategory.SOUND -> ent.ownedContent.contains(item.productId)
            ShopCategory.BUNDLE, ShopCategory.REMOVE_ADS -> ent.ownedProducts.contains(item.productId)
        }

        val price = s.priceLookup(item.productId) ?: when (item.category) {
            ShopCategory.REMOVE_ADS -> "$4.99"
            ShopCategory.BUNDLE -> "$1.99"
            else -> "$0.99"
        }

        h.vb.tvTitle.text = item.title
        h.vb.tvSubtitle.text = "${item.subtitle}\n${if (owned) "Owned ✅" else "Price: $price"}"

        h.vb.btnPrimary.text = if (owned) "Owned" else "Buy"
        h.vb.btnPrimary.isEnabled = !owned
        h.vb.btnPrimary.setOnClickListener { onBuy(item) }

        val canEquip = item.category == ShopCategory.SKIN || item.category == ShopCategory.SOUND
        val equipped = when (item.category) {
            ShopCategory.SKIN -> item.productId == s.equippedSkinId
            ShopCategory.SOUND -> item.productId == s.equippedSoundId
            else -> false
        }

        h.vb.btnSecondary.text = when {
            !canEquip -> "Details"
            equipped -> "Equipped"
            else -> "Equip"
        }
        h.vb.btnSecondary.isEnabled = canEquip && owned && !equipped
        h.vb.btnSecondary.setOnClickListener { onEquip(item) }
    }
}