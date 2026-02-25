package com.example.instatracker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.R
import com.example.instatracker.data.Account

class AccountsAdapter(
    private val onClick: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : ListAdapter<Account, AccountsAdapter.VH>(
    object : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(a: Account, b: Account) = a.id == b.id
        override fun areContentsTheSame(a: Account, b: Account) = a == b
    }
) {
    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvCount: TextView = view.findViewById(R.id.tvCount)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snapshot, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvLabel.text = "@${item.username}"
        holder.tvDate.text = item.note.ifBlank { "Нажмите для просмотра" }
        holder.tvCount.text = "👤"
        holder.btnDelete.setOnClickListener { onDelete(item) }
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
