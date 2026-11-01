package com.example.leavetracker

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var db: Db
    private lateinit var prefs: SharedPreferences
    private lateinit var list: ListView
    private lateinit var summary: TextView
    private lateinit var yearView: TextView
    private lateinit var monthBtn: Button
    private var calMode = false
    private lateinit var grid: GridLayout
    private lateinit var calTitle: TextView
    private val DAY = 86_400_000L
    private val catColors = mapOf("Casual" to 0xFF81C784.toInt(), "Sick" to 0xFFE57373.toInt(), "Duty" to 0xFF64B5F6.toInt(),
        "Half Day" to 0xFFFFD54F.toInt(), "Short Leave" to 0xFFBA68C8.toInt())
    private var year = Calendar.getInstance().get(Calendar.YEAR)
    private var month = 0
    private var rows = listOf<Leave>()
    private var yearRows = listOf<Leave>()
    private val loc by lazy { resources.configuration.locales[0] }
    private val months by lazy { listOf(getString(R.string.all_months)) + DateFormatSymbols(loc).months.take(12) }
    private val utc = TimeZone.getTimeZone("UTC")
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utc }
    private val monthFmt by lazy { SimpleDateFormat("MMMM yyyy", loc).apply { timeZone = utc } }
    private val pretty by lazy { SimpleDateFormat("dd MMM yyyy", loc).apply { timeZone = utc } }
    private val multiDay = listOf("Casual", "Sick", "Duty")

    private val csvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { save(it, csvBytes()) }
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { save(it, pdfBytes()) }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) importCsv(u) }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(SCHEMES[getSharedPreferences("ui", MODE_PRIVATE).getInt("scheme", 0)], true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = Db(this)
        prefs = getSharedPreferences("quota", MODE_PRIVATE)
        list = findViewById(R.id.list); summary = findViewById(R.id.summary)
        yearView = findViewById(R.id.year); monthBtn = findViewById(R.id.month)
        findViewById<View>(R.id.fab).setOnClickListener { showForm(null) }
        findViewById<View>(R.id.prev).setOnClickListener { year--; refresh() }
        findViewById<View>(R.id.next).setOnClickListener { year++; refresh() }
        monthBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.month)
                .setItems(months.toTypedArray()) { _, i -> month = i; refresh() }.show()
        }
        grid = findViewById(R.id.grid); calTitle = findViewById(R.id.calTitle)
        findViewById<View>(R.id.calPrev).setOnClickListener { stepMonth(-1) }
        findViewById<View>(R.id.calNext).setOnClickListener { stepMonth(1) }
        findViewById<TabLayout>(R.id.tabs).apply {
            addTab(newTab().setText(R.string.tab_list)); addTab(newTab().setText(R.string.tab_calendar))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(t: TabLayout.Tab) { calMode = t.position == 1; applyMode() }
                override fun onTabUnselected(t: TabLayout.Tab) {}
                override fun onTabReselected(t: TabLayout.Tab) {}
            })
        }
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            inflateMenu(R.menu.main)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.graph -> showGraph()
                    R.id.yearly -> showYearly()
                    R.id.balances -> showBalances()
                    R.id.importcsv -> importLauncher.launch(arrayOf("*/*"))
                    R.id.csv -> csvLauncher.launch("leaves_${period()}.csv")
                    R.id.pdf -> pdfLauncher.launch("leaves_${period()}.pdf")
                    R.id.thememode -> pick(R.string.theme, R.array.themes, "mode") { AppCompatDelegate.setDefaultNightMode(nightModeFor(it)) }
                    R.id.colorscheme -> pick(R.string.color_scheme, R.array.schemes, "scheme") { recreate() }
                    R.id.language -> MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.language)
                        .setItems(arrayOf("English", "සිංහල")) { _, i ->
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(if (i == 0) "en" else "si"))
                        }.show()
                }
                true
            }
        }
        refresh()
    }

    private fun period() = if (month == 0) "$year" else String.format("%d-%02d", year, month)
    private fun fmt(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
    private fun quota(c: String) = prefs.getFloat("q_$c", -1f).toDouble()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun cn(c: String) = getString(when (c) {
        "Casual" -> R.string.casual; "Sick" -> R.string.sick; "Duty" -> R.string.duty
        "Half Day" -> R.string.half_day; else -> R.string.short_leave })

    private fun summaryLines(): List<String> {
        val dw = getString(R.string.days)
        val out = CATEGORIES.map { cat ->
            val r = rows.filter { it.category == cat }
            var t = "${cn(cat)}: ${r.size} ${getString(R.string.entries)} • ${fmt(r.sumOf { it.days() })} $dw"
            val q = quota(cat)
            if (cat in BALANCE_CATS && q >= 0) {
                val left = q - yearRows.filter { it.category == cat }.sumOf { it.days() }
                t += " • " + getString(R.string.balance_left, fmt(left), fmt(q))
            }
            t
        }
        return out + "${getString(R.string.total)}: ${fmt(rows.sumOf { it.days() })} $dw"
    }

    private fun refresh(): Job = lifecycleScope.launch {
        yearView.text = year.toString()
        monthBtn.text = months[month]
        rows = db.forPeriod(year, month)
        yearRows = db.forPeriod(year, 0)
        if (calMode) drawCalendar()
        summary.text = summaryLines().joinToString("\n")
        val items = ArrayList<Any>()
        rows.groupBy { it.date.substring(0, 7) }.forEach { (ym, g) ->
            items.add("${monthFmt.format(iso.parse("$ym-01")!!)}  •  ${fmt(g.sumOf { it.days() })} ${getString(R.string.days)}")
            items.addAll(g)
        }
        list.adapter = object : ArrayAdapter<Any>(this@MainActivity, android.R.layout.simple_list_item_2, android.R.id.text1, items) {
            override fun isEnabled(position: Int) = items[position] is Leave
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val v = super.getView(pos, cv, parent)
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                val item = items[pos]
                if (item is Leave) {
                    t1.text = "${pretty.format(iso.parse(item.date)!!)} — ${cn(item.category)} (${fmt(item.days())})"
                    t1.setTypeface(null, Typeface.NORMAL)
                    t2.visibility = View.VISIBLE
                    t2.text = item.reason.ifBlank { getString(R.string.no_reason) }
                    v.setBackgroundColor(0)
                } else {
                    t1.text = item as String
                    t1.setTypeface(null, Typeface.BOLD)
                    t2.visibility = View.GONE
                    v.setBackgroundColor(0x22888888)
                }
                return v
            }
        }
        list.setOnItemClickListener { _, _, pos, _ -> (items[pos] as? Leave)?.let { showForm(it) } }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val l = items[pos] as? Leave
            if (l != null) MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.delete_q)
                .setPositiveButton(R.string.delete) { _, _ -> lifecycleScope.launch { db.delete(l.id); refresh() } }
                .setNegativeButton(R.string.cancel, null).show()
            true
        }
    }

    // New entries open on the month chosen in the month filter (today if it is the current month)
    private fun defaultMillis(): Long {
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        if (month == 0) return today
        val now = Calendar.getInstance(utc).apply { timeInMillis = today }
        if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) + 1) return today
        val first = Calendar.getInstance(utc).apply { clear(); set(year, month - 1, 1) }.timeInMillis
        return minOf(first, today)
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>()
        val sb = StringBuilder(); var q = false; var i = 0
        fun endRow() { row.add(sb.toString()); sb.setLength(0); if (row.any { it.isNotBlank() }) rows.add(row); row = ArrayList() }
        while (i < text.length) {
            val ch = text[i]
            if (q) {
                if (ch == '"') { if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else q = false }
                else sb.append(ch)
            } else when (ch) {
                '"' -> q = true
                ',' -> { row.add(sb.toString()); sb.setLength(0) }
                '\r' -> {}
                '\n' -> endRow()
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    private fun importCsv(uri: Uri): Job = lifecycleScope.launch {
        val text = try {
            withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
        } catch (e: Exception) { toast(e.message ?: "Error"); return@launch }
        val strict = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false; timeZone = utc }
        val today = iso.format(Date(MaterialDatePicker.todayInUtcMilliseconds()))
        val have = db.existing("0000-01-01", "9999-12-31").toMutableSet()
        var ok = 0; var skip = 0
        for (r in parseCsv(text.removePrefix("\uFEFF"))) {
            val d = r.getOrNull(0)?.trim() ?: ""
            if (d.equals("Date", true)) continue   // header row
            val c = CATEGORIES.firstOrNull { it.equals(r.getOrNull(1)?.trim(), true) }
            val pd = if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(d)) try { strict.parse(d) } catch (e: Exception) { null } else null
            val valid = pd != null && !isWeekend(pd.time)   // weekends are not allowed
            if (!valid || c == null || d > today || d in have ||
                (c == "Short Leave" && db.shortCount(d.substring(0, 7)) >= SHORT_LIMIT)) skip++
            else { db.add(d, c, r.getOrNull(3)?.trim() ?: ""); have.add(d); ok++ }
        }
        toast(getString(R.string.import_done, ok, skip))
        refresh()
    }

    private fun curMonth() = if (month == 0) Calendar.getInstance().get(Calendar.MONTH) + 1 else month

    private fun applyMode() {
        val g = if (calMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.list).visibility = g
        findViewById<View>(R.id.historyLabel).visibility = g
        findViewById<View>(R.id.calendar).visibility = if (calMode) View.VISIBLE else View.GONE
        refresh()
    }

    private fun stepMonth(d: Int) {
        var m = curMonth() + d; var y = year
        if (m < 1) { m = 12; y-- } else if (m > 12) { m = 1; y++ }
        year = y; month = m; refresh()
    }

    private fun drawCalendar() {
        val cm = curMonth(); val dp = resources.displayMetrics.density
        calTitle.text = monthFmt.format(iso.parse(String.format("%d-%02d-01", year, cm))!!)
        grid.removeAllViews()
        fun cell(label: String, bg: Int = 0, onClick: (() -> Unit)? = null) {
            grid.addView(TextView(this).apply {
                text = label; gravity = Gravity.CENTER; textSize = 14f
                setBackgroundColor(bg); if (bg != 0) setTextColor(Color.BLACK)
                minHeight = (48 * dp).toInt()
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f))
                    .apply { width = 0; setMargins(2, 2, 2, 2) }
                onClick?.let { f -> setOnClickListener { f() } }
            })
        }
        val wd = DateFormatSymbols(loc).shortWeekdays
        for (i in 1..7) cell(wd[i])
        val first = Calendar.getInstance().apply { clear(); set(year, cm - 1, 1) }
        repeat(first.get(Calendar.DAY_OF_WEEK) - 1) { cell("") }
        val byDay = yearRows.filter { it.date.substring(5, 7).toInt() == cm }.associateBy { it.date.substring(8).toInt() }
        for (d in 1..first.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val l = byDay[d]
            cell(d.toString(), l?.let { catColors[it.category]!! } ?: 0, l?.let { x -> { showForm(x) } })
        }
        val legend = findViewById<LinearLayout>(R.id.legend); legend.removeAllViews()
        CATEGORIES.forEach { c ->
            legend.addView(TextView(this).apply {
                text = cn(c); textSize = 10f; gravity = Gravity.CENTER
                setTextColor(Color.BLACK); setBackgroundColor(catColors[c]!!); setPadding(4, 8, 4, 8)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 2, 2, 2) }
            })
        }
    }

    private fun isWeekend(ms: Long): Boolean {
        val d = Calendar.getInstance(utc).apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    private fun prevWeekday(ms: Long): Long { var t = ms; while (isWeekend(t)) t -= DAY; return t }

    private fun picker(sel: Long): MaterialDatePicker<Long> {
        val today = MaterialDatePicker.todayInUtcMilliseconds()   // future dates are not selectable
        return MaterialDatePicker.Builder.datePicker().setSelection(prevWeekday(minOf(sel, today)))
            .setCalendarConstraints(CalendarConstraints.Builder().setEnd(today)
                .setValidator(CompositeDateValidator.allOf(listOf<CalendarConstraints.DateValidator>(
                    DateValidatorPointBackward.before(today), WeekdayValidator()))).build()).build()
    }

    private fun showForm(edit: Leave?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null)
        val cat = v.findViewById<AutoCompleteTextView>(R.id.category)
        val dateBtn = v.findViewById<Button>(R.id.date)
        val endBtn = v.findViewById<Button>(R.id.endDate)
        val reason = v.findViewById<EditText>(R.id.reason)
        var selCat = edit?.category ?: CATEGORIES[0]
        cat.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, CATEGORIES.map { cn(it) }))
        cat.setText(cn(selCat), false)
        reason.setText(edit?.reason ?: "")
        var millis = edit?.let { iso.parse(it.date)!!.time } ?: prevWeekday(defaultMillis())
        var endMillis = millis
        val multi = v.findViewById<CompoundButton>(R.id.multi)
        fun canRange() = edit == null && selCat in multiDay
        fun ranged() = canRange() && multi.isChecked   // range of days only when the option is switched on
        fun labels() {
            if (endMillis < millis) endMillis = millis
            if (endMillis > millis + 9 * DAY) endMillis = millis + 9 * DAY   // max 10 entries at once
            multi.visibility = if (canRange()) View.VISIBLE else View.GONE
            dateBtn.text = getString(if (ranged()) R.string.from else R.string.date) + pretty.format(Date(millis))
            endBtn.text = getString(R.string.to) + pretty.format(Date(endMillis))
            endBtn.visibility = if (ranged()) View.VISIBLE else View.GONE
        }
        multi.setOnCheckedChangeListener { _, _ -> labels() }
        cat.setOnItemClickListener { _, _, pos, _ -> selCat = CATEGORIES[pos]; labels() }
        dateBtn.setOnClickListener {
            val p = picker(millis)
            p.addOnPositiveButtonClickListener { millis = it; labels() }
            p.show(supportFragmentManager, "date")
        }
        endBtn.setOnClickListener {
            val p = picker(endMillis)
            p.addOnPositiveButtonClickListener {
                if (it > millis + 9 * DAY) toast(getString(R.string.max_days))
                endMillis = it; labels()
            }
            p.show(supportFragmentManager, "end")
        }
        labels()
        MaterialAlertDialogBuilder(this).setTitle(if (edit == null) R.string.add_leave else R.string.edit_leave).setView(v)
            .setPositiveButton(R.string.save) { _, _ -> lifecycleScope.launch {
                val r = reason.text.toString().trim()
                val d = iso.format(Date(millis))
                if (isWeekend(millis) && (edit == null || edit.date != d)) { toast(getString(R.string.weekend)); return@launch }
                val shortFull = selCat == "Short Leave" && db.shortCount(d.substring(0, 7), edit?.id ?: 0L) >= SHORT_LIMIT
                if (shortFull) toast(getString(R.string.short_limit, SHORT_LIMIT))
                else if (edit != null) {
                    if (db.clash(d, edit.id)) toast(getString(R.string.skipped, 1)) else db.update(edit.id, d, selCat, r)
                } else {
                    // one entry per day; days that already have a leave are skipped (no duplicates)
                    val last = if (ranged()) maxOf(millis, endMillis) else millis
                    val have = db.existing(d, iso.format(Date(last)))
                    var t = millis; var skipped = 0
                    while (t <= last) {
                        val ds = iso.format(Date(t))
                        if (isWeekend(t)) { } else if (ds in have) skipped++ else db.add(ds, selCat, r)
                        t += 86_400_000L
                    }
                    if (skipped > 0) toast(getString(R.string.skipped, skipped))
                }
                year = d.substring(0, 4).toInt()
                if (month != 0) month = d.substring(5, 7).toInt()
                refresh()
            } }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showBalances() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }
        val fields = BALANCE_CATS.map { c ->
            EditText(this).apply {
                hint = getString(R.string.balances_hint, cn(c))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val q = quota(c); if (q >= 0) setText(fmt(q))
            }.also { box.addView(it) }
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.balances_title).setView(box)
            .setPositiveButton(R.string.save) { _, _ ->
                BALANCE_CATS.forEachIndexed { i, c ->
                    prefs.edit().putFloat("q_$c", fields[i].text.toString().toFloatOrNull() ?: -1f).apply()
                }
                refresh()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun showYearly() = lifecycleScope.launch {
        val byYear = db.all().groupBy { it.date.substring(0, 4) }.toSortedMap(reverseOrder())
        val dw = getString(R.string.days)
        val text = if (byYear.isEmpty()) getString(R.string.no_data) else byYear.map { (y, g) ->
            "$y — ${fmt(g.sumOf { it.days() })} $dw\n" +
                CATEGORIES.joinToString(" • ") { c -> "${cn(c)}: ${g.count { it.category == c }}" }
        }.joinToString("\n\n")
        val tv = TextView(this@MainActivity).apply { this.text = text; setPadding(64, 24, 64, 0); textSize = 15f; setLineSpacing(4f, 1f) }
        MaterialAlertDialogBuilder(this@MainActivity).setTitle(R.string.yearly_title).setView(ScrollView(this@MainActivity).apply { addView(tv) })
            .setPositiveButton(R.string.close, null).show()
    }

    // Saves the chosen option under `key` in the "ui" preferences, then runs `apply`
    private fun pick(title: Int, items: Int, key: String, apply: (Int) -> Unit) {
        val ui = getSharedPreferences("ui", MODE_PRIVATE)
        MaterialAlertDialogBuilder(this).setTitle(title)
            .setSingleChoiceItems(resources.getStringArray(items), ui.getInt(key, 0)) { d, i ->
                ui.edit().putInt(key, i).apply(); d.dismiss(); apply(i)
            }.show()
    }

    private fun showGraph() {
        val monthly = DoubleArray(12) { i -> yearRows.filter { it.date.substring(5, 7).toInt() == i + 1 }.sumOf { it.days() } }
        val ink = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 0) }
        box.addView(TextView(this).apply { text = getString(R.string.graph_legend) })
        box.addView(ChartView(this, monthly, DateFormatSymbols(loc).shortMonths.take(12), ink,
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.DKGRAY)))
        MaterialAlertDialogBuilder(this).setTitle("${getString(R.string.graph_title)} $year").setView(box)
            .setPositiveButton(R.string.close, null).show()
    }

    private fun save(uri: Uri?, bytes: ByteArray) {
        if (uri == null) return
        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        toast(getString(R.string.exported))
    }

    private fun csvBytes(): ByteArray {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("Date,Category,Days,Reason\n")
        rows.sortedBy { it.date }.forEach { sb.append("${it.date},${it.category},${fmt(it.days())},${q(it.reason)}\n") }
        return sb.toString().toByteArray()
    }

    private fun pdfBytes(): ByteArray {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        var n = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
        var y = 50f
        fun line(t: String, bold: Boolean = false) {
            if (y > 800f) {
                doc.finishPage(page); n++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create()); y = 50f
            }
            paint.isFakeBoldText = bold
            page.canvas.drawText(t, 40f, y, paint); y += 20f
        }
        line("Leave Report – ${period()}", true); y += 10f
        summaryLines().forEach { line(it) }
        y += 10f
        rows.sortedBy { it.date }.forEach {
            line("${it.date}   ${cn(it.category).padEnd(14)}  ${fmt(it.days()).padEnd(5)}  ${it.reason}")
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream(); doc.writeTo(out); doc.close()
        return out.toByteArray()
    }
}
