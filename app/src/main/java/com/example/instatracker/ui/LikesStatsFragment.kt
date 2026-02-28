package com.example.instatracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.instatracker.R
import com.example.instatracker.data.LikerStat
import com.example.instatracker.databinding.FragmentLikesStatsBinding
import com.example.instatracker.databinding.ItemLikerBinding

class LikesStatsFragment : Fragment() {

    private var _binding: FragmentLikesStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            MainViewModelFactory.getInstance(requireActivity().application)
        )[MainViewModel::class.java]
    }

    companion object {
        private const val ARG_ACCOUNT_ID = "aid"

        fun newInstance(accountId: Long) = LikesStatsFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ACCOUNT_ID, accountId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLikesStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val accountId = arguments?.getLong(ARG_ACCOUNT_ID) ?: return
        binding.rvLikers.layoutManager = LinearLayoutManager(requireContext())

        viewModel.loadLikerStats(accountId)

        viewModel.likerStats.observe(viewLifecycleOwner) { stats ->
            if (stats.isNullOrEmpty()) {
                binding.tvLikesSummary.text = getString(R.string.likes_stats_empty)
                binding.rvLikers.adapter = LikersAdapter(emptyList())
                return@observe
            }

            val totalPosts = stats.firstOrNull()?.totalPosts ?: 0
            val totalLikers = stats.size
            val superFans = stats.count { it.likeCount == totalPosts }

            binding.tvLikesSummary.text = getString(
                R.string.likes_stats_summary,
                totalPosts, totalLikers, superFans
            )

            binding.rvLikers.adapter = LikersAdapter(stats)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class LikersAdapter(
    private val items: List<LikerStat>
) : RecyclerView.Adapter<LikersAdapter.VH>() {

    inner class VH(val binding: ItemLikerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLikerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val totalPosts = item.totalPosts

        holder.binding.tvRank.text = "#${position + 1}"
        holder.binding.tvUsername.text = "@${item.username}"
        holder.binding.tvLikeCount.text = "${item.likeCount}/$totalPosts ❤"

        // Выделяем тех кто лайкнул все посты
        val color = if (item.likeCount == totalPosts)
            holder.itemView.context.getColor(R.color.colorDanger)
        else
            holder.itemView.context.getColor(R.color.textPrimary)
        holder.binding.tvUsername.setTextColor(color)
    }
}
