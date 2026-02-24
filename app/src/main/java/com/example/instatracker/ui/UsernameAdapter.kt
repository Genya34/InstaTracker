package com.example.instatracker.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.databinding.ItemUsernameBinding

data class UsernameItem(val username: String, val isNew: Boolean)

class UsernameAdapter(
    private val items: List<UsernameItem>
) : RecyclerView.Adapter<UsernameAdapter.VH>() {

    inner class VH(val b: ItemUsernameBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemUsernameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvUsername.text = "@${item.username}"
        if (item.isNew) {
            holder.b.tvIcon.text = "✅"
            holder.b.tvUsername.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            holder.b.tvIcon.text = "❌"
            holder.b.tvUsername.setTextColor(Color.parseColor("#C62828"))
        }
    }
}
