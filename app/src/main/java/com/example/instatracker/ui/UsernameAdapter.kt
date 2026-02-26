package com.example.instatracker.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
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
        val card = holder.b.root as? com.google.android.material.card.MaterialCardView

        if (item.type == UsernameItem.HEADER) {
            holder.b.tvIcon.text = ""
            holder.b.tvUsername.text = item.username
            holder.b.tvUsername.setTextColor(0xFF585068.toInt())
            holder.b.tvUsername.textSize = 11f
            holder.b.tvOpen.visibility = View.GONE
            card?.setCardBackgroundColor(0x00000000)
            card?.strokeWidth = 0
            card?.isClickable = false
            return
        }

        holder.b.tvUsername.text = "@${item.username}"
        holder.b.tvUsername.textSize = 13f
        holder.b.tvOpen.visibility = View.VISIBLE

        // Клик → открыть Instagram
        holder.b.root.setOnClickListener {
            val ctx = holder.b.root.context
            try {
                // Попробуем открыть в приложении Instagram
                val igIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://instagram.com/_u/${item.username}"))
                igIntent.setPackage("com.instagram.android")
                ctx.startActivity(igIntent)
            } catch (e: Exception) {
                // Если Instagram не установлен — открыть в браузере
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://instagram.com/${item.username}"))
                ctx.startActivity(webIntent)
            }
        }

        when (item.type) {
            UsernameItem.NEW -> {
                holder.b.tvIcon.text = "+"
                holder.b.tvUsername.setTextColor(0xFF185818.toInt())
                card?.setCardBackgroundColor(0xFFD0F0D0.toInt())
                card?.strokeColor = 0xFF90C890.toInt()
            }
            UsernameItem.GONE -> {
                holder.b.tvIcon.text = "X"
                holder.b.tvUsername.setTextColor(0xFF781818.toInt())
                card?.setCardBackgroundColor(0xFFF0D0D0.toInt())
                card?.strokeColor = 0xFFC89090.toInt()
            }
            UsernameItem.FAN -> {
                holder.b.tvIcon.text = "♦"
                holder.b.tvUsername.setTextColor(0xFF182878.toInt())
                card?.setCardBackgroundColor(0xFFD0D8F0.toInt())
                card?.strokeColor = 0xFF9098C0.toInt()
            }
            UsernameItem.NOT_MUTUAL -> {
                holder.b.tvIcon.text = "♥"
                holder.b.tvUsername.setTextColor(0xFF784018.toInt())
                card?.setCardBackgroundColor(0xFFF0E0D0.toInt())
                card?.strokeColor = 0xFFC0A888.toInt()
            }
            UsernameItem.MUTUAL -> {
                holder.b.tvIcon.text = "★"
                holder.b.tvUsername.setTextColor(0xFF185828.toInt())
                card?.setCardBackgroundColor(0xFFD0E8D0.toInt())
                card?.strokeColor = 0xFF90B890.toInt()
            }
        }
    }
}
