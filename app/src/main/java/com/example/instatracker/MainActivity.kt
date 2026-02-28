package com.example.instatracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.instatracker.data.Account
import com.example.instatracker.databinding.ActivityMainBinding
import com.example.instatracker.databinding.DialogAddAccountBinding
import com.example.instatracker.databinding.DialogAddSnapshotBinding
import com.example.instatracker.databinding.ScreenAccountsBinding
import com.example.instatracker.databinding.ScreenChooseTypeBinding
import com.example.instatracker.databinding.ScreenHelpBinding
import com.example.instatracker.ui.*
import com.example.instatracker.util.InstagramJsonParser
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private var currentScreen = Screen.ACCOUNTS
    private var pendingLabel = ""

    private val jsonPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val stream = contentResolver.openInputStream(uri)
                        ?: run {
                            Toast.makeText(this, getString(R.string.status_error, "файл недоступен"), Toast.LENGTH_LONG).show()
                            return@let
                        }
                    viewModel.createSnapshot(
                        InstagramJsonParser.parseFollowersJson(stream), pendingLabel
                    )
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.status_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val browserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val names = (result.data?.getStringExtra(BrowserActivity.EXTRA_RESULT_NAMES) ?: "")
                .lines().map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
            if (names.isNotEmpty()) {
                val label = if (viewModel.currentListType == "followers")
                    getString(R.string.snapshot_label_auto_followers, names.size)
                else
                    getString(R.string.snapshot_label_auto_following, names.size)
                viewModel.createSnapshot(names, label)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory.getInstance(application)
        )[MainViewModel::class.java]

        binding.toolbar.menu.add(getString(R.string.btn_help)).apply {
            setIcon(android.R.drawable.ic_menu_help)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        binding.toolbar.setOnMenuItemClickListener { showHelp(); true }
        binding.toolbar.setNavigationOnClickListener { handleBack() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        showAccountsList()

        binding.fabAdd.setOnClickListener {
            when (currentScreen) {
                Screen.ACCOUNTS -> showAddAccountDialog()
                Screen.SNAPSHOTS -> showAddSnapshotDialog()
                else -> { }
            }
        }

        viewModel.status.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        viewModel.error.observe(this) { message ->
            Toast.makeText(this, "⚠️ $message", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateBackArrow() {
        if (currentScreen == Screen.ACCOUNTS) {
            binding.toolbar.navigationIcon = null
        } else {
            binding.toolbar.setNavigationIcon(R.drawable.ic_pixel_back)
        }
    }

    private fun handleBack() {
        when (currentScreen) {
            Screen.SNAPSHOTS -> viewModel.currentAccount.value?.let { showChooseType(it) } ?: showAccountsList()
            Screen.CHOOSE_TYPE -> showAccountsList()
            Screen.STATS -> {
                val f = supportFragmentManager.findFragmentById(binding.mainContainer.id)
                if (f != null) supportFragmentManager.beginTransaction().remove(f).commit()
                viewModel.currentAccount.value?.let { showChooseType(it) } ?: showAccountsList()
            }
            Screen.ACCOUNTS -> finish()
        }
    }

    // ── Экран аккаунтов ───────────────────────────────────────────────────

    private fun showAccountsList() {
        currentScreen = Screen.ACCOUNTS
        binding.toolbar.title = getString(R.string.toolbar_title)
        binding.toolbar.subtitle = getString(R.string.toolbar_subtitle_accounts)
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fabAdd.show()
        binding.fabAdd.text = getString(R.string.btn_add)
        updateBackArrow()

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val screen = ScreenAccountsBinding.inflate(layoutInflater, container, true)
        screen.rvAccounts.layoutManager = LinearLayoutManager(this)

        val adapter = AccountsAdapter(
            onClick = { showChooseType(it) },
            onDelete = { acc ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_delete_title, acc.username))
                    .setMessage(R.string.dialog_delete_message)
                    .setPositiveButton(R.string.btn_delete) { _, _ -> viewModel.deleteAccount(acc) }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        )
        screen.rvAccounts.adapter = adapter

        viewModel.accounts.observe(this) { list ->
            adapter.submitList(list)
            val isEmpty = list.isEmpty()
            screen.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            screen.rvAccounts.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    // ── Экран выбора режима ───────────────────────────────────────────────

    private fun showChooseType(account: Account) {
        currentScreen = Screen.CHOOSE_TYPE
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = getString(R.string.toolbar_subtitle_select_mode)
        binding.fabAdd.hide()
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        updateBackArrow()

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val screen = ScreenChooseTypeBinding.inflate(layoutInflater, container, true)
        screen.tvFollowersSub.text = getString(R.string.mode_followers_sub, account.username)
        screen.tvFollowingSub.text = getString(R.string.mode_following_sub, account.username)

        screen.cardFollowers.setOnClickListener {
            viewModel.selectAccount(account.id, "followers")
            showSnapshotsScreen(account, "followers")
        }
        screen.cardFollowing.setOnClickListener {
            viewModel.selectAccount(account.id, "following")
            showSnapshotsScreen(account, "following")
        }
        screen.cardStatistics.setOnClickListener {
            showStatsScreen(account)
        }
    }

    // ── Экран снимков ─────────────────────────────────────────────────────

    fun showSnapshotsScreen(account: Account, listType: String) {
        currentScreen = Screen.SNAPSHOTS
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = if (listType == "followers")
            getString(R.string.toolbar_subtitle_followers)
        else
            getString(R.string.toolbar_subtitle_following)
        binding.fabAdd.show()
        binding.fabAdd.text = getString(R.string.btn_snap)
        binding.mainContainer.visibility = View.GONE
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE
        updateBackArrow()

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int): Fragment =
                if (pos == 0) SnapshotsListFragment() else ChangesFragment()
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.tab_snapshots) else getString(R.string.tab_changes)
        }.attach()
    }

    // ── Экран статистики ──────────────────────────────────────────────────

    private fun showStatsScreen(account: Account) {
        currentScreen = Screen.STATS
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = getString(R.string.toolbar_subtitle_statistics)
        binding.fabAdd.hide()
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.mainContainer.visibility = View.VISIBLE
        binding.mainContainer.removeAllViews()
        updateBackArrow()

        supportFragmentManager.beginTransaction()
            .replace(binding.mainContainer.id, NonMutualFragment.newInstance(account.id))
            .commit()
    }

    // ── Диалог добавления аккаунта — теперь из XML ────────────────────────

    private fun showAddAccountDialog() {
        val dv = DialogAddAccountBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_account_title)
            .setView(dv.root)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                viewModel.addAccount(
                    dv.etUsername.text.toString(),
                    dv.etNote.text.toString()
                )
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    // ── Диалог добавления снимка ──────────────────────────────────────────

    fun showAddSnapshotDialog() {
        val dv = DialogAddSnapshotBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dv.root)
            .setPositiveButton(R.string.btn_save) { _, _ ->
                val names = InstagramJsonParser.parseSimpleList(dv.etUsernames.text.toString())
                if (names.isNotEmpty()) {
                    viewModel.createSnapshot(names, dv.etLabel.text.toString())
                } else {
                    Toast.makeText(this, R.string.status_enter_usernames, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        dv.btnBrowser.setOnClickListener { dialog.dismiss(); launchBrowser() }
        dv.btnImportJson.setOnClickListener {
            pendingLabel = dv.etLabel.text.toString()
            jsonPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            })
            dialog.dismiss()
        }
        dv.btnInstruction.setOnClickListener { dialog.dismiss(); showHelp() }
        dialog.show()
    }

    private fun launchBrowser() {
        val acc = viewModel.currentAccount.value ?: return
        browserLauncher.launch(Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_USERNAME, acc.username)
            putExtra(BrowserActivity.EXTRA_LIST_TYPE, viewModel.currentListType)
        })
    }

    // ── Помощь — теперь из XML ────────────────────────────────────────────

    fun showHelp() {
        val dv = ScreenHelpBinding.inflate(layoutInflater)

        AlertDialog.Builder(this)
            .setView(dv.root)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }
}
