package com.example.instatracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.instatracker.databinding.ActivityMainBinding
import com.example.instatracker.databinding.DialogAddSnapshotBinding
import com.example.instatracker.databinding.FragmentChangesBinding
import com.example.instatracker.ui.*
import com.example.instatracker.util.InstagramJsonParser
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel
    private var pendingLabel = ""

    private val jsonPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val stream = contentResolver.openInputStream(uri)!!
                    val names = InstagramJsonParser.parseFollowersJson(stream)
                    viewModel.createSnapshot(names, pendingLabel)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this,
            MainViewModelFactory(application))[MainViewModel::class.java]

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int): Fragment =
                if (pos == 0) SnapshotsFragment() else ChangesFragment()
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Снимки" else "Изменения"
        }.attach()

        binding.fabAdd.setOnClickListener { showAddDialog() }
        viewModel.status.observe(this) {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddDialog() {
        val dv = DialogAddSnapshotBinding.inflate(layoutInflater)
        val dlg = AlertDialog.Builder(this).setView(dv.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val names = InstagramJsonParser.parseSimpleList(
                    dv.etUsernames.text.toString())
                if (names.isNotEmpty())
                    viewModel.createSnapshot(names, dv.etLabel.text.toString())
                else Toast.makeText(this, "Введите имена", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null).create()

        dv.btnImportJson.setOnClickListener {
            pendingLabel = dv.etLabel.text.toString()
            jsonPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            })
            dlg.dismiss()
        }
        dlg.show()
    }
}

class SnapshotsFragment : Fragment() {
    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 8, 0, 200); clipToPadding = false
        }
        val vm = (requireActivity() as MainActivity).viewModel
        val adapter = SnapshotsAdapter { vm.deleteSnapshot(it) }
        rv.adapter = adapter
        vm.snapshots.observe(viewLifecycleOwner) { adapter.submitList(it) }
        return rv
    }
}

class ChangesFragment : Fragment() {
    private var _b: FragmentChangesBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentChangesBinding.inflate(inf, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).viewModel
        b.rvChanges.layoutManager = LinearLayoutManager(requireContext())
        vm.compareLastTwo()
        vm.changes.observe(viewLifecycleOwner) { r ->
            if (r == null) {
                b.tvSummary.text = "Добавьте минимум 2 снимка"
                b.rvChanges.adapter = UsernameAdapter(emptyList()); return@observe
            }
            b.tvSummary.text = "✅ Подписались: ${r.newFollowers.size}   ❌ Отписались: ${r.unfollowers.size}"
            b.rvChanges.adapter = UsernameAdapter(
                r.newFollowers.map { UsernameItem(it, true) } +
                r.unfollowers.map { UsernameItem(it, false) })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
