package com.example.instatracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.databinding.ItemUsernameBinding

data class UsernameItem(
    val username: String,
    val type: Int
) {
    companion object {
        const val NEW = 0
        const val GONE = 1
        const val FAN = 2
        const val NOT_MUTUAL = 3
        const val MUTUAL = 4
        const val HEADER = 5
    }
}

class UsernameAdapter(
    private val items: List<UsernameItem>
) : RecyclerView.Adapter<UsernameAdapter.VH>() {

    inner class VH(val b: ItemUsernameBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUsernameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        if (item.type == UsernameItem.HEADER) {
            holder.b.tvIcon.text = ""
            holder.b.tvUsername.text = item.username
            holder.b.tvUsername.setTextColor(0xFF64748B.toInt())
            holder.b.tvUsername.textSize = 13f
            holder.b.tvUsername.setTypeface(null, android.graphics.Typeface.BOLD)

            val card = holder.b.root as? com.google.android.material.card.MaterialCardView
            card?.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
            card?.strokeWidth = 0
            card?.cardElevation = 0f
            return
        }

        holder.b.tvUsername.text = "@${item.username}"
        holder.b.tvUsername.textSize = 15f
        holder.b.tvUsername.setTypeface(null, android.graphics.Typeface.BOLD)

        val card = holder.b.root as? com.google.android.material.card.MaterialCardView

        when (item.type) {
            UsernameItem.NEW -> {
                holder.b.tvIcon.text = "✅"
                holder.b.tvUsername.setTextColor(0xFF065F46.toInt())
                card?.setCardBackgroundColor(0xFFECFDF5.toInt())
            }
            UsernameItem.GONE -> {
                holder.b.tvIcon.text = "❌"
                holder.b.tvUsername.setTextColor(0xFF991B1B.toInt())
                card?.setCardBackgroundColor(0xFFFEF2F2.toInt())
            }
            UsernameItem.FAN -> {
                holder.b.tvIcon.text = "👤"
                holder.b.tvUsername.setTextColor(0xFF1E40AF.toInt())
                card?.setCardBackgroundColor(0xFFEFF6FF.toInt())
            }
            UsernameItem.NOT_MUTUAL -> {
                holder.b.tvIcon.text = "💔"
                holder.b.tvUsername.setTextColor(0xFF9A3412.toInt())
                card?.setCardBackgroundColor(0xFFFFF7ED.toInt())
            }
            UsernameItem.MUTUAL -> {
                holder.b.tvIcon.text = "🤝"
                holder.b.tvUsername.setTextColor(0xFF065F46.toInt())
                card?.setCardBackgroundColor(0xFFF0FDF4.toInt())
            }
        }
    }
}
