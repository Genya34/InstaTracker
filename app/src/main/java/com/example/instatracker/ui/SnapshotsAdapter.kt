package com.example.instatracker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.data.Snapshot
import com.example.instatracker.databinding.ItemSnapshotBinding
import java.text.SimpleDateFormat
import java.util.*

class SnapshotsAdapter(
    private val onDelete: (Snapshot) -> Unit
) : ListAdapter<Snapshot, SnapshotsAdapter.VH>(
    object : DiffUtil.ItemCallback<Snapshot>() {
        override fun areItemsTheSame(a: Snapshot, b: Snapshot) = a.id == b.id
        override fun areContentsTheSame(a: Snapshot, b: Snapshot) = a == b
    }
) {
    private val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    inner class VH(val b: ItemSnapshotBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSnapshotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.b.tvLabel.text = item.label
        holder.b.tvDate.text = fmt.format(Date(item.timestamp))
        holder.b.tvCount.text = "${item.count} чел."
        holder.b.btnDelete.setOnClickListener { onDelete(item) }
    }
}
