package com.example.instatracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.instatracker.data.Account
import com.example.instatracker.databinding.ActivityMainBinding
import com.example.instatracker.databinding.DialogAddSnapshotBinding
import com.example.instatracker.ui.*
import com.example.instatracker.util.InstagramJsonParser
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    // Теперь экран — это enum, а не строка. Опечатка не скомпилируется.
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

        // Обычные статусные сообщения
        viewModel.status.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Ошибки показываем отдельно — можно будет легко поменять на диалог
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

        val emptyView = TextView(this).apply {
            setText(R.string.welcome_empty)
            textSize = 15f
            setTextColor(getColor(R.color.textHint))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(48, 200, 48, 48)
            visibility = View.GONE
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        container.addView(emptyView)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 8, 0, 200)
            clipToPadding = false
        }
        container.addView(rv)

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
        rv.adapter = adapter

        viewModel.accounts.observe(this) { list ->
            adapter.submitList(list)
            val isEmpty = list.isEmpty()
            emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
            rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 28)
        }

        makeCard("►", getString(R.string.mode_followers),
            getString(R.string.mode_followers_sub, account.username),
            getColor(R.color.colorFollowers), layout) {
            viewModel.selectAccount(account.id, "followers")
            showSnapshotsScreen(account, "followers")
        }
        makeCard("►", getString(R.string.mode_following),
            getString(R.string.mode_following_sub, account.username),
            getColor(R.color.colorFollowing), layout) {
            viewModel.selectAccount(account.id, "following")
            showSnapshotsScreen(account, "following")
        }
        makeCard("★", getString(R.string.mode_statistics),
            getString(R.string.mode_statistics_sub),
            getColor(R.color.colorStatistics), layout) {
            showStatsScreen(account)
        }

        container.addView(layout)
    }

    private fun makeCard(
        icon: String, title: String, sub: String,
        strokeColor: Int, layout: LinearLayout, onClick: () -> Unit
    ) {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            radius = 8f; cardElevation = 0f
            strokeWidth = 4; this.strokeColor = strokeColor
            setCardBackgroundColor(getColor(R.color.cardBackground))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 12; layoutParams = p
            setOnClickListener { onClick() }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 20, 24, 20)
        }
        inner.addView(TextView(this).apply {
            text = "$icon $title"; textSize = 17f
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.textPrimary))
        })
        inner.addView(TextView(this).apply {
            text = sub; textSize = 12f
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setTextColor(getColor(R.color.textSecondary))
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = 4; layoutParams = p
        })
        card.addView(inner)
        layout.addView(card)
    }

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

    private fun showAddAccountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
        }
        val etUsernameLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.hint_username)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 12; layoutParams = p
        }
        val etUsername = com.google.android.material.textfield.TextInputEditText(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
        }
        etUsernameLayout.addView(etUsername)

        val etNoteLayout = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = getString(R.string.hint_note)
        }
        val etNote = com.google.android.material.textfield.TextInputEditText(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
        }
        etNoteLayout.addView(etNote)

        layout.addView(etUsernameLayout)
        layout.addView(etNoteLayout)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_account_title)
            .setView(layout)
            .setPositiveButton(R.string.btn_add) { _, _ ->
                viewModel.addAccount(etUsername.text.toString(), etNote.text.toString())
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

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

    fun showHelp() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24)
        }
        scroll.addView(layout)

        fun addText(resId: Int, sizeSp: Float = 14f, bold: Boolean = false, colorResId: Int = R.color.textPrimary) {
            layout.addView(TextView(this).apply {
                setText(resId); textSize = sizeSp
                setTypeface(android.graphics.Typeface.MONOSPACE,
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(getColor(colorResId))
                setLineSpacing(3f, 1f)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 12; layoutParams = p
            })
        }

        addText(R.string.help_title, 20f, true, R.color.colorPrimary)
        addText(R.string.help_divider, 12f, false, R.color.textDivider)
        addText(R.string.help_auto_title, 16f, true, R.color.colorFollowing)
        addText(R.string.help_auto_body)
        addText(R.string.help_divider, 12f, false, R.color.textDivider)
        addText(R.string.help_stats_title, 16f, true, R.color.colorStatistics)
        addText(R.string.help_stats_body)
        addText(R.string.help_divider, 12f, false, R.color.textDivider)
        addText(R.string.help_tip, 13f, false, R.color.textSecondary)

        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton(R.string.btn_ok, null)
            .show()
    }
}
