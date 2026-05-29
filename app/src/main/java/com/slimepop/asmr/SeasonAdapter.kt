package com.slimepop.asmr

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.slimepop.asmr.databinding.RowSeasonCardBinding

class SeasonAdapter(
    private val seasons: List<Season>,
    private val state: () -> SeasonState,
    private val onBuy: (Season) -> Unit
) : RecyclerView.Adapter<SeasonAdapter.VH>() {

    data class SeasonState(
        val entitlements: Entitlements,
        val priceLookup: (String) -> String?,
        val canPurchase: (String) -> Boolean
    )

    class VH(val vb: RowSeasonCardBinding) : RecyclerView.ViewHolder(vb.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vb = RowSeasonCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(vb)
    }

    override fun getItemCount() = seasons.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val season = seasons[position]
        val s = state()
        val owned = s.entitlements.ownedProducts.contains(season.id)
        val price = s.priceLookup(season.id) ?: season.priceHint
        val canPurchase = s.canPurchase(season.id)

        // Gradient banner background
        val gd = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(season.gradientStart, season.gradientEnd)
        )
        h.vb.vSeasonBanner.background = gd

        h.vb.tvSeasonName.text = "${season.emoji}  ${season.name}"
        h.vb.tvSeasonTagline.text = season.tagline
        h.vb.tvSeasonSkinList.text = season.previewLabel()
        h.vb.tvSeasonContentSummary.text = season.contentSummary

        // Savings label
        val priceFloat = price.replace(Regex("[^0-9.]"), "").toFloatOrNull()
        h.vb.tvSeasonSavings.text = when {
            owned -> "✓ Owned"
            priceFloat != null -> {
                val savings = SeasonCatalog.individualValue(season) - priceFloat
                if (savings > 0) "Save $${"%.2f".format(savings)}" else ""
            }
            else -> ""
        }

        // Buy button
        h.vb.btnSeasonBuy.text = when {
            owned       -> "Owned"
            !canPurchase -> "..."
            else         -> "Buy $price"
        }
        h.vb.btnSeasonBuy.isEnabled = !owned && canPurchase
        h.vb.btnSeasonBuy.setOnClickListener { if (!owned) onBuy(season) }

        // Contents dialog
        h.vb.btnSeasonDetails.setOnClickListener {
            val skinNames = season.skinIds
                .mapNotNull { id -> SkinCatalog.skins.find { it.id == id }?.name }
                .joinToString("\n") { "  • $it" }
            val soundNames = season.soundIds
                .mapNotNull { id -> SoundCatalog.sounds.find { it.id == id }?.name }
                .joinToString("\n") { "  • $it" }
            val msg = "Skins:\n$skinNames\n\nSounds:\n$soundNames"
            AlertDialog.Builder(h.vb.root.context)
                .setTitle("${season.emoji} ${season.name}")
                .setMessage(msg)
                .setPositiveButton("Got it", null)
                .show()
        }
    }
}
