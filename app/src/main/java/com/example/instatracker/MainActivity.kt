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
import com.example.instatracker.ui.*
import com.example.instatracker.util.InstagramJsonParser
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    lateinit var viewModel: MainViewModel

    var currentScreen = "accounts"
    private var pendingLabel = ""

    companion object {
        const val JS_SCRIPT = """javascript:void(function(){var u=[];document.querySelectorAll('span').forEach(function(e){var t=e.textContent.trim();if(t.length>1&&t.length<31&&t.indexOf(' ')===-1&&/^[a-zA-Z0-9._]+$/.test(t)){u.push(t.toLowerCase())}});var r=[];u.forEach(function(v){if(r.indexOf(v)===-1)r.push(v)});document.open();document.write('<html><head><title>'+r.length+'</title></head><body style="margin:16px"><h3>Найдено: '+r.length+' имён</h3><p>Выделите всё, скопируйте, вставьте в InstaTracker</p><textarea id="t" style="width:100%;height:80vh;font-size:14px" readonly>'+r.join('\n')+'</textarea></body></html>');document.close()})()"""
    }

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

        binding.toolbar.menu.add("Инструкция").apply {
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

    fun showInstructionDialog() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        scroll.addView(layout)

        val title = TextView(this).apply {
            text = "📱 Как получить список подписчиков"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(title)
        addSpacer(layout, 24)

        val m1 = TextView(this).apply {
            text = "━━ Способ 1: Автоматически ━━"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF4CAF50.toInt())
        }
        layout.addView(m1)

        val s1 = TextView(this).apply {
            text = """
                |
                |1. Выберите аккаунт → Подписчики
                |   или Подписки
                |2. Нажмите ➕ → «Получить
                |   автоматически через браузер»
                |3. Войдите в Instagram (только
                |   в первый раз)
                |4. Прокрутите список ВНИЗ ДО КОНЦА
                |5. Нажмите зелёную кнопку
                |6. Приложение само соберёт все имена!
                |
            """.trimMargin()
            textSize = 14f
        }
        layout.addView(s1)
        addSpacer(layout, 16)

        val m2 = TextView(this).apply {
            text = "━━ Способ 2: Через Chrome + скрипт ━━"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF7B1FA2.toInt())
        }
        layout.addView(m2)

        val s2 = TextView(this).apply {
            text = """
                |
                |1. Откройте Chrome
                |2. Перейдите на instagram.com
                |3. Откройте профиль → подписчики
                |4. Прокрутите вниз
                |5. Скопируйте скрипт (кнопка ниже)
                |6. Вставьте в адресную строку Chrome
                |7. Допишите javascript: в начало
                |8. Нажмите Enter
                |9. Скопируйте список имён
                |10. Вставьте в InstaTracker
                |
            """.trimMargin()
            textSize = 14f
        }
        layout.addView(s2)

        val btnCopy = com.google.android.material.button.MaterialButton(this).apply {
            text = "📋 Скопировать скрипт"
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 8
            layoutParams = params
            setOnClickListener {
                copyToClipboard("js", JS_SCRIPT)
                Toast.makeText(context, "✅ Скопировано!", Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(btnCopy)
        addSpacer(layout, 16)

        val m3 = TextView(this).apply {
            text = "━━ Способ 3: Вручную / JSON ━━"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF7B1FA2.toInt())
        }
        layout.addView(m3)

        val s3 = TextView(this).apply {
            text = """
                |
                |Вручную: перепишите имена из
                |Instagram по одному на строку
                |
                |JSON: скачайте данные из Instagram
                |(Настройки → Ваши действия →
                |Скачать данные → Подписчики → JSON)
                |
            """.trimMargin()
            textSize = 14f
        }
        layout.addView(s3)

        AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun addSpacer(layout: LinearLayout, height: Int) {
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height)
        })
    }

    private fun copyToClipboard(label: String, text: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun showAccountsList() {
        currentScreen = "accounts"
        binding.toolbar.title = "InstaTracker"
        binding.toolbar.subtitle = "Отслеживаемые аккаунты"
        binding.tabLayout.visibility = View.GONE
        binding.viewPager.visibility = View.GONE
        binding.fabAdd.show()

        val container = binding.mainContainer
        container.visibility = View.VISIBLE
        container.removeAllViews()

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            setPadding(0, 8, 0, 200)
            clipToPadding = false
        }
        container.addView(rv)

        val adapter = AccountsAdapter(
            onClick = { showChooseType(it) },
            onDelete = { account ->
                AlertDialog.Builder(this)
                    .setTitle("Удалить @${account.username}?")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.deleteAccount(account) }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        )
        rv.adapter = adapter
        viewModel.accounts.observe(this) { adapter.submitList(it) }
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
            setPadding(32, 48, 32, 32)
        }

        val btnFollowers = com.google.android.material.button.MaterialButton(this).apply {
            text = "📥 Подписчики\n(кто подписан на @${account.username})"
            textSize = 16f
            setPadding(32, 48, 32, 48)
            setOnClickListener {
                viewModel.selectAccount(account.id, "followers")
                showSnapshotsScreen(account, "followers")
            }
        }

        val btnFollowing = com.google.android.material.button.MaterialButton(this).apply {
            text = "📤 Подписки\n(на кого подписан @${account.username})"
            textSize = 16f
            setPadding(32, 48, 32, 48)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            params.topMargin = 32
            layoutParams = params
            setOnClickListener {
                viewModel.selectAccount(account.id, "following")
                showSnapshotsScreen(account, "following")
            }
        }

        layout.addView(btnFollowers)
        layout.addView(btnFollowing)
        container.addView(layout)
    }

    fun showSnapshotsScreen(account: Account, listType: String) {
        currentScreen = "snapshots"
        val typeText = if (listType == "followers") "Подписчики" else "Подписки"
        binding.toolbar.title = "@${account.username}"
        binding.toolbar.subtitle = typeText
        binding.fabAdd.show()
        binding.mainContainer.visibility = View.GONE
        binding.tabLayout.visibility = View.VISIBLE
        binding.viewPager.visibility = View.VISIBLE

        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int): Fragment =
                if (pos == 0) SnapshotsListFragment() else ChangesFragment()
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, pos ->
            tab.text = if (pos == 0) "Снимки" else "Изменения"
        }.attach()
    }

    private fun showAddAccountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        val etUsername = EditText(this).apply { hint = "Имя пользователя (без @)" }
        val etNote = EditText(this).apply { hint = "Заметка (необязательно)" }
        layout.addView(etUsername)
        layout.addView(etNote)

        AlertDialog.Builder(this)
            .setTitle("Добавить аккаунт")
            .setView(layout)
            .setPositiveButton("Добавить") { _, _ ->
                viewModel.addAccount(etUsername.text.toString(), etNote.text.toString())
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
            else -> super.onBackPressed()
        }
    }
}

class SnapshotsListFragment : Fragment() {
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
                b.tvSummary.text = "Добавьте минимум 2 снимка для сравнения"
                b.rvChanges.adapter = UsernameAdapter(emptyList()); return@observe
            }
            val typeNew = if (vm.currentListType == "followers") "Подписались" else "Подписался на"
            val typeGone = if (vm.currentListType == "followers") "Отписались" else "Отписался от"
            b.tvSummary.text = "✅ $typeNew: ${r.newUsers.size}   ❌ $typeGone: ${r.goneUsers.size}"
            b.rvChanges.adapter = UsernameAdapter(
                r.newUsers.map { UsernameItem(it, true) } +
                r.goneUsers.map { UsernameItem(it, false) })
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
