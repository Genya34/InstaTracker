package com.example.instatracker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.instatracker.R
import com.example.instatracker.databinding.FragmentChangesBinding

class ChangesFragment : Fragment() {

    private var _binding: FragmentChangesBinding? = null
    private val binding get() = _binding!!

    private val viewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            MainViewModelFactory.getInstance(requireActivity().application)
        )[MainViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvChanges.layoutManager = LinearLayoutManager(requireContext())

        viewModel.changes.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                binding.tvSummary.setText(R.string.changes_not_enough)
                binding.rvChanges.adapter = UsernameAdapter(emptyList())
                return@observe
            }

            val newLabel = if (viewModel.currentListType == "followers")
                getString(R.string.changes_new_followers)
            else
                getString(R.string.changes_new_following)

            val goneLabel = if (viewModel.currentListType == "followers")
                getString(R.string.changes_gone_followers)
            else
                getString(R.string.changes_gone_following)

            binding.tvSummary.text = getString(
                R.string.changes_summary,
                newLabel, result.newUsers.size,
                goneLabel, result.goneUsers.size
            )

            binding.rvChanges.adapter = UsernameAdapter(
                result.newUsers.map { UsernameItem(it, UsernameItem.NEW) } +
                result.goneUsers.map { UsernameItem(it, UsernameItem.GONE) }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
