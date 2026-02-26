package com.example.instatracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
import com.example.instatracker.databinding.FragmentChangesBinding
import com.example.instatracker.databinding.FragmentNonMutualBinding
import com.example.instatracker.ui.*
import com.example.instatracker.util.InstagramJsonParser
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel

    var currentScreen = "accounts"
    private var pendingLabel = ""

    private val jsonPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val stream = contentResolver.openInputStream(uri)!!
                    viewModel.createSnapshot(
                        InstagramJsonParser.parseFollowersJson(stream), pendingLabel)
                } catch (e: Exception) {
                    Toast.makeText(this, "ERROR: ${e.message}", Toast.LENGTH_LONG).show()
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
                val t = if (viewModel.currentListType == "followers") "FOLLOWERS" else "FOLLOWING"
                viewModel.createSnapshot(names, "AUTO $t (${names.size})")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this,
            MainViewModelFactory(application))[MainViewModel::class.java]

        // Help button
        binding.toolbar.menu.add("Help").apply {
            setIcon(android.R.drawable.ic_menu_help)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        binding.toolbar.setOnMenuItemClickListener { showHelp(); true }

        // Back arrow handler
        binding.toolbar.setNavigationOnClickListener { onBackPressedCompat() }

        showAccountsList()

        binding.fabAdd.setOnClickListener {
            when (currentScreen) {
                "accounts" -> showAddAccountDialog()
                "snapshots" -> showAddSnapshotDialog()
            }
        }

        viewModel.status.observe(this) {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBackArrow() {
        if (currentScreen == "accounts") {
            binding.toolbar.navigationIcon = null
        } else {
            binding.toolbar.setNavigationIcon(R.drawable.ic_pixel_back)
        }
    }

    private fun onBackPressedCompat() {
        when (currentScreen) {
            "snapshots" -> {
                viewModel.currentAccount.value?.let { showChooseType(it) } ?: showAccountsList()
            }
            "choose_type" -> showAccountsList()
            "stats" -> {
                val f = supportFragmentManager.findFragmentById(binding.mainContainer.id)
                if (f != null) supportFragmentManager.beginTransaction().remove(f).commit()
                viewModel.currentAccount.value?.let { showChooseType(it) } ?: showAccountsList()
            }
            else -> finish()
        }
    }

    // ══════════════════════════════════════
    // SCREENS
    // ══════════════════════════════════════

    fun showAccountsList() {
        currentScreen = "accounts"
        binding.toolbar.title = "INSTATRACKER"
        binding.toolbar.subtitle = "◆ TRACKED ACCOUNTS ◆"
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fabAdd.show(); binding.fabAdd.text = "+ ADD"
        updateBackArrow()

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val emptyView = TextView(this).apply {
            text = "◆ WELCOME ◆\n\nTAP [+ ADD] TO START\nTRACKING AN ACCOUNT"
            textSize = 15f; setTextColor(0xFF887898.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(48, 200, 48, 48); visibility = View.GONE
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        container.addView(emptyView)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 8, 0, 200); clipToPadding = false
        }
        container.addView(rv)

        val adapter = AccountsAdapter(
            onClick = { showChooseType(it) },
            onDelete = { acc ->
                AlertDialog.Builder(this)
                    .setTitle("DELETE @${acc.username}?")
                    .setMessage("All snapshots will be removed")
                    .setPositiveButton("DELETE") { _, _ -> viewModel.deleteAccount(acc) }
                    .setNegativeButton("CANCEL", null).show()
            }
        )
        rv.adapter = adapter
        viewModel.accounts.observe(this) { list ->
            adapter.submitList(list)
            emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rv.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    fun showChooseType(account: Account) {
        currentScreen = "choose_type"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = "◆ SELECT MODE ◆"
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

        fun makeCard(icon: String, title: String, sub: String, color: Int, onClick: () -> Unit) {
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 8f; cardElevation = 0f
                strokeWidth = 4; strokeColor = color
                setCardBackgroundColor(0xFFE8E0D0.toInt())
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 12; layoutParams = p
                setOnClickListener { onClick() }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
            }
            inner.addView(TextView(this).apply {
                text = "$icon $title"; textSize = 17f
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setTextColor(0xFF181830.toInt())
            })
            inner.addView(TextView(this).apply {
                text = sub; textSize = 12f
                setTypeface(android.graphics.Typeface.MONOSPACE)
                setTextColor(0xFF585068.toInt())
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = 4; layoutParams = p
            })
            card.addView(inner)
            layout.addView(card)
        }

        makeCard("►", "FOLLOWERS",
            "WHO FOLLOWS @${account.username}", 0xFF3868B8.toInt()) {
            viewModel.selectAccount(account.id, "followers")
            showSnapshotsScreen(account, "followers")
        }

        makeCard("►", "FOLLOWING",
            "WHO @${account.username} FOLLOWS", 0xFF38A858.toInt()) {
            viewModel.selectAccount(account.id, "following")
            showSnapshotsScreen(account, "following")
        }

        makeCard("★", "STATISTICS",
            "FANS / NOT MUTUAL / MUTUAL", 0xFFC8A828.toInt()) {
            viewModel.selectAccount(account.id, "followers")
            showStatsScreen(account)
        }

        container.addView(layout)
    }

    fun showSnapshotsScreen(account: Account, listType: String) {
        currentScreen = "snapshots"
        val t = if (listType == "followers") "FOLLOWERS" else "FOLLOWING"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = "◆ $t ◆"
        binding.fabAdd.show(); binding.fabAdd.text = "+ SNAP"
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
            tab.text = if (pos == 0) "SNAPSHOTS" else "CHANGES"
        }.attach()
    }

    fun showStatsScreen(account: Account) {
        currentScreen = "stats"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = "◆ STATISTICS ◆"
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

    // ══════════════════════════════════════
    // DIALOGS
    // ══════════════════════════════════════

    private fun showAddAccountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
        }
        val etU = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "USERNAME"
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT); p.bottomMargin = 12; layoutParams = p
        }
        val etUI = com.google.android.material.textfield.TextInputEditText(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
        }
        etU.addView(etUI)

        val etN = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "NOTE (OPTIONAL)"
        }
        val etNI = com.google.android.material.textfield.TextInputEditText(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
        }
        etN.addView(etNI)

        layout.addView(etU); layout.addView(etN)

        AlertDialog.Builder(this).setTitle("◆ ADD ACCOUNT ◆").setView(layout)
            .setPositiveButton("ADD") { _, _ ->
                viewModel.addAccount(etUI.text.toString(), etNI.text.toString())
            }.setNegativeButton("CANCEL", null).show()
    }

    fun showAddSnapshotDialog() {
        val dv = DialogAddSnapshotBinding.inflate(layoutInflater)
        val dlg = AlertDialog.Builder(this).setView(dv.root)
            .setPositiveButton("SAVE") { _, _ ->
                val n = InstagramJsonParser.parseSimpleList(dv.etUsernames.text.toString())
                if (n.isNotEmpty()) viewModel.createSnapshot(n, dv.etLabel.text.toString())
                else Toast.makeText(this, "ENTER USERNAMES", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("CANCEL", null).create()

        dv.btnBrowser.setOnClickListener {
            dlg.dismiss(); launchBrowser()
        }
        dv.btnImportJson.setOnClickListener {
            pendingLabel = dv.etLabel.text.toString()
            jsonPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }); dlg.dismiss()
        }
        dv.btnInstruction.setOnClickListener { dlg.dismiss(); showHelp() }
        dlg.show()
    }

    private fun launchBrowser() {
        val acc = viewModel.currentAccount.value ?: return
        browserLauncher.launch(Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_USERNAME, acc.username)
            putExtra(BrowserActivity.EXTRA_LIST_TYPE, viewModel.currentListType)
        })
    }

    // ══════════════════════════════════════
    // HELP
    // ══════════════════════════════════════

    fun showHelp() {
        val scroll = ScrollView(this)
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 24)
        }
        scroll.addView(l)

        fun t(s: String, sz: Float = 14f, bold: Boolean = false, c: Int = 0xFF181830.toInt()) {
            l.addView(TextView(this).apply {
                text = s; textSize = sz
                setTypeface(android.graphics.Typeface.MONOSPACE,
                    if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(c); setLineSpacing(3f, 1f)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 12; layoutParams = p
            })
        }

        t("◆ HELP ◆", 20f, true, 0xFF5B4BA8.toInt())
        t("═══════════════════════", 12f, false, 0xFFB0A890.toInt())
        t("► AUTO-COLLECT", 16f, true, 0xFF38A858.toInt())
        t("1. SELECT ACCOUNT → FOLLOWERS\n   OR FOLLOWING\n2. TAP [+ SNAP] → AUTO-COLLECT\n3. LOGIN TO INSTAGRAM (FIRST TIME)\n4. TAP [► SCROLL] — AUTO!\n5. NAMES COLLECTED → SAVE")
        t("═══════════════════════", 12f, false, 0xFFB0A890.toInt())
        t("★ STATISTICS", 16f, true, 0xFFC8A828.toInt())
        t("1. COLLECT BOTH FOLLOWERS\n   AND FOLLOWING\n2. TAP STATISTICS\n3. VIEW:\n   ♦ FANS — FOLLOW YOU BUT\n     YOU DON'T FOLLOW BACK\n   ♥ NOT MUTUAL — YOU FOLLOW\n     BUT THEY DON'T\n   ★ MUTUAL — FOLLOW EACH OTHER")
        t("═══════════════════════", 12f, false, 0xFFB0A890.toInt())
        t("TAP ANY USERNAME TO\nOPEN INSTAGRAM PROFILE", 13f, false, 0xFF585068.toInt())

        AlertDialog.Builder(this).setView(scroll).setPositiveButton("OK", null).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { onBackPressedCompat() }
}

// ═══════════════════════════════════
// FRAGMENTS
// ═══════════════════════════════════

class SnapshotsListFragment : Fragment() {
    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        val empty = TextView(requireContext()).apply {
            text = "◆ NO SNAPSHOTS YET ◆\n\nTAP [+ SNAP] TO SAVE\nCURRENT FOLLOWER LIST"
            textSize = 14f; setTextColor(0xFF887898.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setPadding(48, 160, 48, 48); visibility = View.GONE
        }
        layout.addView(empty)

        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 8, 0, 200); clipToPadding = false
        }
        layout.addView(rv)

        val vm = (requireActivity() as MainActivity).viewModel
        val adapter = SnapshotsAdapter { vm.deleteSnapshot(it) }
        rv.adapter = adapter
        vm.snapshots.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            empty.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
            rv.visibility = if (list.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        return layout
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
                b.tvSummary.text = "◆ ADD 2+ SNAPSHOTS\n  TO SEE CHANGES ◆"
                b.rvChanges.adapter = UsernameAdapter(emptyList()); return@observe
            }
            val n = if (vm.currentListType == "followers") "NEW FOLLOWERS" else "NEW FOLLOWING"
            val g = if (vm.currentListType == "followers") "UNFOLLOWED" else "UNFOLLOWED"
            b.tvSummary.text = "+ $n: ${r.newUsers.size}\nX $g: ${r.goneUsers.size}"
            b.rvChanges.adapter = UsernameAdapter(
                r.newUsers.map { UsernameItem(it, UsernameItem.NEW) } +
                r.goneUsers.map { UsernameItem(it, UsernameItem.GONE) })
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

class NonMutualFragment : Fragment() {
    private var _b: FragmentNonMutualBinding? = null
    private val b get() = _b!!
    private var filter = "fans"

    companion object {
        fun newInstance(accountId: Long) = NonMutualFragment().apply {
            arguments = Bundle().apply { putLong("aid", accountId) }
        }
    }

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentNonMutualBinding.inflate(inf, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).viewModel
        val aid = arguments?.getLong("aid") ?: return
        b.rvNonMutual.layoutManager = LinearLayoutManager(requireContext())

        vm.computeNonMutual(aid)

        b.btnShowFans.setOnClickListener { filter = "fans"; refresh(vm) }
        b.btnShowNotMutual.setOnClickListener { filter = "not_mutual"; refresh(vm) }
        b.btnShowMutual.setOnClickListener { filter = "mutual"; refresh(vm) }
        b.btnShowAll.setOnClickListener { filter = "all"; refresh(vm) }

        vm.nonMutual.observe(viewLifecycleOwner) { r ->
            if (r == null) {
                b.tvSummaryTitle.text = "◆ NOT ENOUGH DATA ◆"
                b.tvSummaryDetails.text = "COLLECT BOTH FOLLOWERS\nAND FOLLOWING FIRST"
                b.rvNonMutual.adapter = UsernameAdapter(emptyList())
                return@observe
            }

            b.tvSummaryTitle.text = "★ STATISTICS ★"
            b.tvSummaryDetails.text =
                "♦ FANS: ${r.fans.size}\n" +
                "♥ NOT MUTUAL: ${r.notFollowingBack.size}\n" +
                "★ MUTUAL: ${r.mutual.size}\n" +
                "─────────────────\n" +
                "FOLLOWERS: ${r.followersCount}\n" +
                "FOLLOWING: ${r.followingCount}"

            b.btnShowFans.text = "♦${r.fans.size}"
            b.btnShowNotMutual.text = "♥${r.notFollowingBack.size}"
            b.btnShowMutual.text = "★${r.mutual.size}"
            b.btnShowAll.text = "ALL"

            refresh(vm)
        }
    }

    private fun refresh(vm: MainViewModel) {
        val r = vm.nonMutual.value ?: return
        val items = mutableListOf<UsernameItem>()

        when (filter) {
            "fans" -> {
                if (r.fans.isNotEmpty()) {
                    items.add(UsernameItem("♦ FANS — FOLLOW YOU, YOU DON'T FOLLOW BACK", UsernameItem.HEADER))
                    r.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                } else items.add(UsernameItem("◆ NO FANS ◆", UsernameItem.HEADER))
            }
            "not_mutual" -> {
                if (r.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem("♥ YOU FOLLOW, THEY DON'T FOLLOW BACK", UsernameItem.HEADER))
                    r.notFollowingBack.forEach { items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL)) }
                } else items.add(UsernameItem("◆ ALL MUTUAL ◆", UsernameItem.HEADER))
            }
            "mutual" -> {
                if (r.mutual.isNotEmpty()) {
                    items.add(UsernameItem("★ MUTUAL — FOLLOW EACH OTHER (${r.mutual.size})", UsernameItem.HEADER))
                    r.mutual.forEach { items.add(UsernameItem(it, UsernameItem.MUTUAL)) }
                } else items.add(UsernameItem("◆ NO MUTUAL ◆", UsernameItem.HEADER))
            }
            "all" -> {
                if (r.fans.isNotEmpty()) {
                    items.add(UsernameItem("♦ FANS (${r.fans.size})", UsernameItem.HEADER))
                    r.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                }
                if (r.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem("♥ NOT MUTUAL (${r.notFollowingBack.size})", UsernameItem.HEADER))
                    r.notFollowingBack.forEach { items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL)) }
                }
                if (r.mutual.isNotEmpty()) {
                    items.add(UsernameItem("★ MUTUAL (${r.mutual.size})", UsernameItem.HEADER))
                    r.mutual.forEach { items.add(UsernameItem(it, UsernameItem.MUTUAL)) }
                }
            }
        }
        b.rvNonMutual.adapter = UsernameAdapter(items)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
