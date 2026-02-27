package com.example.instatracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.instatracker.R
import com.example.instatracker.data.NonMutualResult
import com.example.instatracker.databinding.FragmentNonMutualBinding

class NonMutualFragment : Fragment() {

    private var _binding: FragmentNonMutualBinding? = null
    private val binding get() = _binding!!

    private var filter = "fans"

    private val viewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            MainViewModelFactory(requireActivity().application)
        )[MainViewModel::class.java]
    }

    companion object {
        private const val ARG_ACCOUNT_ID = "aid"

        fun newInstance(accountId: Long) = NonMutualFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ACCOUNT_ID, accountId) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNonMutualBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val accountId = arguments?.getLong(ARG_ACCOUNT_ID) ?: return
        binding.rvNonMutual.layoutManager = LinearLayoutManager(requireContext())

        viewModel.computeNonMutual(accountId)

        binding.btnShowFans.setOnClickListener { filter = "fans"; refresh() }
        binding.btnShowNotMutual.setOnClickListener { filter = "not_mutual"; refresh() }
        binding.btnShowMutual.setOnClickListener { filter = "mutual"; refresh() }
        binding.btnShowAll.setOnClickListener { filter = "all"; refresh() }

        viewModel.nonMutual.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                binding.tvSummaryTitle.setText(R.string.stats_not_enough_title)
                binding.tvSummaryDetails.setText(R.string.stats_not_enough_message)
                binding.rvNonMutual.adapter = UsernameAdapter(emptyList())
                return@observe
            }

            binding.tvSummaryTitle.setText(R.string.stats_title)
            binding.tvSummaryDetails.text = getString(
                R.string.stats_details,
                result.fans.size,
                result.notFollowingBack.size,
                result.mutual.size,
                result.followersCount,
                result.followingCount
            )

            binding.btnShowFans.text = getString(R.string.btn_fans, result.fans.size)
            binding.btnShowNotMutual.text = getString(R.string.btn_not_mutual, result.notFollowingBack.size)
            binding.btnShowMutual.text = getString(R.string.btn_mutual, result.mutual.size)
            binding.btnShowAll.setText(R.string.btn_all)

            refresh()
        }
    }

    private fun refresh() {
        val result = viewModel.nonMutual.value ?: return
        binding.rvNonMutual.adapter = UsernameAdapter(buildList(result))
    }

    private fun buildList(r: NonMutualResult): List<UsernameItem> {
        val items = mutableListOf<UsernameItem>()
        when (filter) {
            "fans" -> {
                if (r.fans.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.header_fans), UsernameItem.HEADER))
                    r.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                } else {
                    items.add(UsernameItem(getString(R.string.no_fans), UsernameItem.HEADER))
                }
            }
            "not_mutual" -> {
                if (r.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.header_not_mutual), UsernameItem.HEADER))
                    r.notFollowingBack.forEach { items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL)) }
                } else {
                    items.add(UsernameItem(getString(R.string.no_not_mutual), UsernameItem.HEADER))
                }
            }
            "mutual" -> {
                if (r.mutual.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.header_mutual, r.mutual.size), UsernameItem.HEADER))
                    r.mutual.forEach { items.add(UsernameItem(it, UsernameItem.MUTUAL)) }
                } else {
                    items.add(UsernameItem(getString(R.string.no_mutual), UsernameItem.HEADER))
                }
            }
            "all" -> {
                if (r.fans.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.btn_fans, r.fans.size), UsernameItem.HEADER))
                    r.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                }
                if (r.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.btn_not_mutual, r.notFollowingBack.size), UsernameItem.HEADER))
                    r.notFollowingBack.forEach { items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL)) }
                }
                if (r.mutual.isNotEmpty()) {
                    items.add(UsernameItem(getString(R.string.btn_mutual, r.mutual.size), UsernameItem.HEADER))
                    r.mutual.forEach { items.add(UsernameItem(it, UsernameItem.MUTUAL)) }
                }
            }
        }
        return items
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
