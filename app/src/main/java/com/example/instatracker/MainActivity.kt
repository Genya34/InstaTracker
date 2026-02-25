package com.example.instatracker

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
                    val names = InstagramJsonParser.parseFollowersJson(stream)
                    viewModel.createSnapshot(names, pendingLabel)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val browserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val namesString = result.data?.getStringExtra(
                BrowserActivity.EXTRA_RESULT_NAMES) ?: ""
            val names = namesString.lines()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()

            if (names.isNotEmpty()) {
                val typeText = if (viewModel.currentListType == "followers")
                    "подписчиков" else "подписок"
                val label = "Авто-снимок $typeText (${names.size})"
                viewModel.createSnapshot(names, label)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this,
            MainViewModelFactory(application))[MainViewModel::class.java]

        binding.toolbar.menu.add("Помощь").apply {
            setIcon(android.R.drawable.ic_menu_help)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        binding.toolbar.setOnMenuItemClickListener {
            showInstructionDialog()
            true
        }

        showAccountsList()

        binding.fabAdd.setOnClickListener {
            when (currentScreen) {
                "accounts" -> showAddAccountDialog()
                "choose_type" -> { }
                "snapshots" -> showAddSnapshotDialog()
                "non_mutual" -> { }
            }
        }

        viewModel.status.observe(this) {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchBrowser() {
        val account = viewModel.currentAccount.value
        if (account == null) {
            Toast.makeText(this, "Ошибка: аккаунт не выбран", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra(BrowserActivity.EXTRA_USERNAME, account.username)
            putExtra(BrowserActivity.EXTRA_LIST_TYPE, viewModel.currentListType)
        }
        browserLauncher.launch(intent)
    }

    // ══════════════════════════════════════
    // ИНСТРУКЦИЯ
    // ══════════════════════════════════════

    fun showInstructionDialog() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        scroll.addView(layout)

        fun addTitle(text: String, color: Int = 0xFF0F172A.toInt()) {
            layout.addView(TextView(this).apply {
                this.text = text
                textSize = 17f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(color)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = 24; p.bottomMargin = 8
                layoutParams = p
            })
        }

        fun addText(text: String) {
            layout.addView(TextView(this).apply {
                this.text = text; textSize = 14f
                setTextColor(0xFF334155.toInt())
                setLineSpacing(4f, 1f)
            })
        }

        layout.addView(TextView(this).apply {
            text = "📱 Инструкция"; textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF0F172A.toInt())
        })

        addTitle("✨ Способ 1: Автоматически", 0xFF10B981.toInt())
        addText("1. Выберите аккаунт → Подписчики или Подписки\n" +
                "2. Нажмите ➕ → «Получить автоматически»\n" +
                "3. Войдите в Instagram (только первый раз)\n" +
                "4. Нажмите «Автопрокрутка»\n" +
                "5. Имена соберутся автоматически\n" +
                "6. Нажмите «Сохранить»")

        addTitle("🔀 Не взаимные подписки", 0xFF8B5CF6.toInt())
        addText("1. Соберите И подписчиков И подписки\n" +
                "2. Нажмите «Не взаимные подписки»\n" +
                "3. Увидите:\n" +
                "   👤 Фанаты — подписаны на вас,\n" +
                "      но вы не подписаны на них\n" +
                "   💔 Не взаимные — вы подписаны,\n" +
                "      но они не подписаны на вас")

        addTitle("💡 Советы", 0xFF6366F1.toInt())
        addText("• Делайте снимки раз в несколько дней\n" +
                "• Для не взаимных нужны ОБА списка\n" +
                "• Все данные только на вашем телефоне\n" +
                "• Для закрытых профилей нужно быть подписанным")

        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("Понятно", null)
            .show()
    }

    // ══════════════════════════════════════
    // ЭКРАНЫ
    // ══════════════════════════════════════

    fun showAccountsList() {
        currentScreen = "accounts"
        binding.toolbar.title = "InstaTracker"
        binding.toolbar.subtitle = "Отслеживаемые аккаунты"
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fabAdd.show()
        binding.fabAdd.text = "Добавить"

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val emptyView = TextView(this).apply {
            text = "👋 Добро пожаловать!\n\nНажмите «Добавить», чтобы\nначать отслеживать аккаунт"
            textSize = 16f; setTextColor(0xFF94A3B8.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(48, 200, 48, 48); visibility = View.GONE
        }
        container.addView(emptyView)

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 12, 0, 200); clipToPadding = false
        }
        container.addView(rv)

        val adapter = AccountsAdapter(
            onClick = { showChooseType(it) },
            onDelete = { account ->
                AlertDialog.Builder(this)
                    .setTitle("Удалить @${account.username}?")
                    .setMessage("Все снимки этого аккаунта будут удалены")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.deleteAccount(account) }
                    .setNegativeButton("Отмена", null)
                    .show()
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
        binding.toolbar.subtitle = "Что отслеживать?"
        binding.fabAdd.hide()
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }

        fun createCard(
            emoji: String, title: String, subtitle: String, onClick: () -> Unit
        ): com.google.android.material.card.MaterialCardView {
            val card = com.google.android.material.card.MaterialCardView(this).apply {
                radius = 24f; cardElevation = 0f
                strokeWidth = 2; strokeColor = 0xFFE2E8F0.toInt()
                setCardBackgroundColor(0xFFFFFFFF.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                params.bottomMargin = 16; layoutParams = params
                setOnClickListener { onClick() }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 28, 32, 28)
            }
            inner.addView(TextView(this).apply {
                text = "$emoji $title"; textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF0F172A.toInt())
            })
            inner.addView(TextView(this).apply {
                text = subtitle; textSize = 14f
                setTextColor(0xFF64748B.toInt())
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = 4; layoutParams = p
            })
            card.addView(inner)
            return card
        }

        layout.addView(createCard(
            "📥", "Подписчики",
            "Кто подписан на @${account.username}"
        ) {
            viewModel.selectAccount(account.id, "followers")
            showSnapshotsScreen(account, "followers")
        })

        layout.addView(createCard(
            "📤", "Подписки",
            "На кого подписан @${account.username}"
        ) {
            viewModel.selectAccount(account.id, "following")
            showSnapshotsScreen(account, "following")
        })

        layout.addView(createCard(
            "🔀", "Не взаимные подписки",
            "Фанаты и те, кто не подписан в ответ"
        ) {
            viewModel.selectAccount(account.id, "followers")
            showNonMutualScreen(account)
        })

        container.addView(layout)
    }

    fun showSnapshotsScreen(account: Account, listType: String) {
        currentScreen = "snapshots"
        val typeText = if (listType == "followers") "Подписчики" else "Подписки"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = typeText
        binding.fabAdd.show()
        binding.fabAdd.text = "Снимок"
        binding.mainContainer.visibility = View.GONE
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int): Fragment =
                if (pos == 0) SnapshotsListFragment() else ChangesFragment()
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "📋 Снимки" else "🔄 Изменения"
        }.attach()
    }

    fun showNonMutualScreen(account: Account) {
        currentScreen = "non_mutual"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = "Не взаимные подписки"
        binding.fabAdd.hide()
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.mainContainer.visibility = View.VISIBLE
        binding.mainContainer.removeAllViews()

        val fragment = NonMutualFragment.newInstance(account.id)
        supportFragmentManager.beginTransaction()
            .replace(binding.mainContainer.id, fragment)
            .commit()
    }

    // ══════════════════════════════════════
    // ДИАЛОГИ
    // ══════════════════════════════════════

    private fun showAddAccountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etUsername = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Имя пользователя"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 16; layoutParams = params
        }
        val etUsernameInput = com.google.android.material.textfield.TextInputEditText(this)
        etUsername.addView(etUsernameInput)

        val etNote = com.google.android.material.textfield.TextInputLayout(this).apply {
            hint = "Заметка (необязательно)"
        }
        val etNoteInput = com.google.android.material.textfield.TextInputEditText(this)
        etNote.addView(etNoteInput)

        layout.addView(etUsername)
        layout.addView(etNote)

        AlertDialog.Builder(this)
            .setTitle("👤 Добавить аккаунт")
            .setView(layout)
            .setPositiveButton("Добавить") { _, _ ->
                viewModel.addAccount(
                    etUsernameInput.text.toString(),
                    etNoteInput.text.toString()
                )
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    fun showAddSnapshotDialog() {
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

        dv.btnBrowser.setOnClickListener {
            dlg.dismiss()
            launchBrowser()
        }

        dv.btnImportJson.setOnClickListener {
            pendingLabel = dv.etLabel.text.toString()
            jsonPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            })
            dlg.dismiss()
        }

        dv.btnInstruction.setOnClickListener {
            dlg.dismiss()
            showInstructionDialog()
        }

        dlg.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (currentScreen) {
            "snapshots" -> {
                viewModel.currentAccount.value?.let { showChooseType(it) }
                    ?: showAccountsList()
            }
            "choose_type" -> showAccountsList()
            "non_mutual" -> {
                // Удаляем фрагмент
                val f = supportFragmentManager.findFragmentById(binding.mainContainer.id)
                if (f != null) {
                    supportFragmentManager.beginTransaction().remove(f).commit()
                }
                viewModel.currentAccount.value?.let { showChooseType(it) }
                    ?: showAccountsList()
            }
            else -> super.onBackPressed()
        }
    }
}

// ══════════════════════════════════════════
// ФРАГМЕНТЫ
// ══════════════════════════════════════════

class SnapshotsListFragment : Fragment() {
    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        val emptyView = TextView(requireContext()).apply {
            text = "📸 Пока нет снимков\n\nНажмите «Снимок», чтобы сохранить\nтекущий список"
            textSize = 15f; setTextColor(0xFF94A3B8.toInt())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(48, 160, 48, 48); visibility = View.GONE
        }
        layout.addView(emptyView)

        val rv = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            setPadding(0, 12, 0, 200); clipToPadding = false
        }
        layout.addView(rv)

        val vm = (requireActivity() as MainActivity).viewModel
        val adapter = SnapshotsAdapter { vm.deleteSnapshot(it) }
        rv.adapter = adapter
        vm.snapshots.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            emptyView.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
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
                b.tvSummary.text = "📊 Добавьте минимум 2 снимка,\nчтобы увидеть изменения"
                b.rvChanges.adapter = UsernameAdapter(emptyList()); return@observe
            }
            val typeNew = if (vm.currentListType == "followers") "Подписались" else "Подписался на"
            val typeGone = if (vm.currentListType == "followers") "Отписались" else "Отписался от"
            b.tvSummary.text = "✅ $typeNew: ${r.newUsers.size}\n❌ $typeGone: ${r.goneUsers.size}"
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
    private var currentFilter = "fans"

    companion object {
        private const val ARG_ACCOUNT_ID = "accountId"
        fun newInstance(accountId: Long): NonMutualFragment {
            val f = NonMutualFragment()
            val args = Bundle()
            args.putLong(ARG_ACCOUNT_ID, accountId)
            f.arguments = args
            return f
        }
    }

    override fun onCreateView(inf: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentNonMutualBinding.inflate(inf, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val vm = (requireActivity() as MainActivity).viewModel
        val accountId = arguments?.getLong(ARG_ACCOUNT_ID) ?: return

        b.rvNonMutual.layoutManager = LinearLayoutManager(requireContext())

        // Запускаем вычисление
        vm.computeNonMutual(accountId)

        // Кнопки фильтра
        b.btnShowFans.setOnClickListener {
            currentFilter = "fans"
            updateFilterButtons()
            updateList(vm)
        }
        b.btnShowNotMutual.setOnClickListener {
            currentFilter = "not_mutual"
            updateFilterButtons()
            updateList(vm)
        }
        b.btnShowAll.setOnClickListener {
            currentFilter = "all"
            updateFilterButtons()
            updateList(vm)
        }

        vm.nonMutual.observe(viewLifecycleOwner) { result ->
            if (result == null) {
                b.tvSummaryTitle.text = "⚠️ Недостаточно данных"
                b.tvSummaryDetails.text =
                    "Для поиска не взаимных подписок нужны\n" +
                    "снимки И подписчиков И подписок.\n\n" +
                    "Вернитесь назад и соберите оба списка."
                b.rvNonMutual.adapter = UsernameAdapter(emptyList())
                b.btnShowFans.isEnabled = false
                b.btnShowNotMutual.isEnabled = false
                b.btnShowAll.isEnabled = false
                return@observe
            }

            b.btnShowFans.isEnabled = true
            b.btnShowNotMutual.isEnabled = true
            b.btnShowAll.isEnabled = true

            b.tvSummaryTitle.text = "🔀 Не взаимные подписки"
            b.tvSummaryDetails.text =
                "👤 Фанаты: ${result.fans.size}\n" +
                "     подписаны, но не подписаны в ответ\n\n" +
                "💔 Не взаимные: ${result.notFollowingBack.size}\n" +
                "     подписан на них, но они не подписаны\n\n" +
                "🤝 Взаимные: ${result.mutualCount}\n\n" +
                "📊 Подписчиков: ${result.followersCount}  " +
                "Подписок: ${result.followingCount}"

            b.btnShowFans.text = "👤 Фанаты (${result.fans.size})"
            b.btnShowNotMutual.text = "💔 Не вз. (${result.notFollowingBack.size})"
            b.btnShowAll.text = "📋 Все (${result.fans.size + result.notFollowingBack.size})"

            updateFilterButtons()
            updateList(vm)
        }
    }

    private fun updateFilterButtons() {
        val active = 0xFF6366F1.toInt()
        val white = 0xFFFFFFFF.toInt()

        b.btnShowFans.apply {
            if (currentFilter == "fans") {
                setBackgroundColor(active); setTextColor(white)
            } else {
                setBackgroundColor(white); setTextColor(active)
            }
        }
        b.btnShowNotMutual.apply {
            if (currentFilter == "not_mutual") {
                setBackgroundColor(active); setTextColor(white)
            } else {
                setBackgroundColor(white); setTextColor(active)
            }
        }
        b.btnShowAll.apply {
            if (currentFilter == "all") {
                setBackgroundColor(active); setTextColor(white)
            } else {
                setBackgroundColor(white); setTextColor(active)
            }
        }
    }

    private fun updateList(vm: MainViewModel) {
        val result = vm.nonMutual.value ?: return

        val items = mutableListOf<UsernameItem>()

        when (currentFilter) {
            "fans" -> {
                if (result.fans.isNotEmpty()) {
                    items.add(UsernameItem(
                        "👤 Фанаты — подписаны, но не подписаны в ответ",
                        UsernameItem.HEADER))
                    result.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                }
            }
            "not_mutual" -> {
                if (result.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem(
                        "💔 Подписан на них, но они не подписаны в ответ",
                        UsernameItem.HEADER))
                    result.notFollowingBack.forEach {
                        items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL))
                    }
                }
            }
            "all" -> {
                if (result.fans.isNotEmpty()) {
                    items.add(UsernameItem(
                        "👤 Фанаты (${result.fans.size})",
                        UsernameItem.HEADER))
                    result.fans.forEach { items.add(UsernameItem(it, UsernameItem.FAN)) }
                }
                if (result.notFollowingBack.isNotEmpty()) {
                    items.add(UsernameItem(
                        "💔 Не взаимные (${result.notFollowingBack.size})",
                        UsernameItem.HEADER))
                    result.notFollowingBack.forEach {
                        items.add(UsernameItem(it, UsernameItem.NOT_MUTUAL))
                    }
                }
            }
        }

        if (items.isEmpty()) {
            items.add(UsernameItem("Список пуст", UsernameItem.HEADER))
        }

        b.rvNonMutual.adapter = UsernameAdapter(items)
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
