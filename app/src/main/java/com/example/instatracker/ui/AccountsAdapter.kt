package com.example.instatracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.data.Account
import com.example.instatracker.databinding.ItemAccountBinding

class AccountsAdapter(
    private val onClick: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : ListAdapter<Account, AccountsAdapter.VH>(
    object : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(a: Account, b: Account) = a.id == b.id
        override fun areContentsTheSame(a: Account, b: Account) = a == b
    }
) {
    inner class VH(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvUsername.text = "@${item.username}"
            tvNote.text = item.note.ifBlank { "Нажмите для просмотра" }
            btnDelete.setOnClickListener { onDelete(item) }
            root.setOnClickListener { onClick(item) }
        }
    }
}
