package com.example.instatracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.R

class SnapshotsListFragment : Fragment() {

    private val viewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireActivity().application)
        )[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }

        val emptyView = TextView(requireContext()).apply {
            setText(R.string.snapshots_empty)
            textSize = 14f
            setTextColor(requireContext().getColor(R.color.textHint))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setPadding(48, 160, 48, 48)
            visibility = View.GONE
        }
        layout.addView(emptyView)

        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 8, 0, 200)
            clipToPadding = false
        }
        layout.addView(rv)

        val adapter = SnapshotsAdapter { viewModel.deleteSnapshot(it) }
        rv.adapter = adapter

        viewModel.snapshots.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            val isEmpty = list.isNullOrEmpty()
            emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }

        return layout
    }
}
