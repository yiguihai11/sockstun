/*
 ============================================================================
 Name        : AppListActivity.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : App List Activity
 ============================================================================
 */

package hev.sockstun

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

class AppListActivity : AppCompatActivity() {
    private lateinit var prefs: Preferences
    private var isChanged = false
    private lateinit var allPackages: List<Package>
    private lateinit var adapter: AppArrayAdapter
    private lateinit var searchEdit: TextInputEditText
    private lateinit var filterGroup: ChipGroup
    private lateinit var listView: ListView

    private data class Package(
        val info: PackageInfo,
        var selected: Boolean,
        val label: String,
        val isSystemApp: Boolean
    )

    private inner class AppArrayAdapter(context: Context) : ArrayAdapter<Package>(context, R.layout.appitem) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val rowView = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.appitem, parent, false)

            val imageView = rowView.findViewById<ImageView>(R.id.icon)
            val nameView = rowView.findViewById<TextView>(R.id.name)
            val uidView = rowView.findViewById<TextView>(R.id.uid)
            val checkBox = rowView.findViewById<CheckBox>(R.id.checked)

            val pkg = getItem(position)
            val pm = context.packageManager
            val appinfo = pkg?.info?.applicationInfo

            appinfo?.let {
                imageView.setImageDrawable(it.loadIcon(pm))
                nameView.text = it.loadLabel(pm).toString()
                val systemTag = if (pkg.isSystemApp) " [System]" else ""
                uidView.text = getString(R.string.uid_format, it.uid.toString(), systemTag)
            }

            checkBox.isChecked = pkg?.selected ?: false

            return rowView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.applist)

        // Initialize ListView
        listView = findViewById(R.id.list_view)
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        prefs = Preferences(this)
        adapter = AppArrayAdapter(this)

        // Initialize search and filter controls
        searchEdit = findViewById(R.id.search_edit)
        filterGroup = findViewById(R.id.filter_group)

        // Get the filter mode from intent
        val filterMode = intent.getIntExtra("filter_mode", MainActivity.APP_FILTER_MODE_ONLY)
        supportActionBar?.title = when (filterMode) {
            MainActivity.APP_FILTER_MODE_BYPASS -> getString(R.string.bypass_apps)
            else -> getString(R.string.select_apps)
        }

        // Load all apps
        loadApps()

        // Set search listener
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps()
            }
        })

        // Set filter listener
        filterGroup.setOnCheckedStateChangeListener { _, _ ->
            filterApps()
        }

        // Set item click listener
        listView.setOnItemClickListener { _, view, position, _ ->
            adapter.getItem(position)?.let { pkg ->
                pkg.selected = !pkg.selected
                view.findViewById<CheckBox>(R.id.checked).isChecked = pkg.selected
                isChanged = true
            }
        }
    }

    private fun loadApps() {
        val apps = prefs.getApps()
        val pm = packageManager
        
        // Using queryIntentActivities is deprecated, but we're using getInstalledPackages directly
        allPackages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .filter { info ->
                if (info.packageName == packageName) return@filter false
                val requestedPermissions = info.requestedPermissions ?: return@filter false
                requestedPermissions.contains(Manifest.permission.INTERNET)
            }
            .map { info ->
                val selected = apps.contains(info.packageName)
                val label = info.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                val isSystemApp = info.applicationInfo?.let {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: false
                Package(info, selected, label, isSystemApp)
            }
            .sortedWith(compareBy({ !it.selected }, { it.label }))

        filterApps()
    }

    private fun filterApps() {
        val searchText = searchEdit.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        val filterId = filterGroup.checkedChipIds.firstOrNull()

        val filteredApps = allPackages.filter { pkg ->
            if (searchText.isNotEmpty() && !pkg.label.lowercase(Locale.getDefault()).contains(searchText)) {
                return@filter false
            }
            when (filterId) {
                R.id.filter_user -> !pkg.isSystemApp
                R.id.filter_system -> pkg.isSystemApp
                else -> true
            }
        }

        adapter.clear()
        adapter.addAll(filteredApps)
        listView.adapter = adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isChanged) {
            saveSelectedApps()
        }
    }
    
    private fun saveSelectedApps() {
        val apps = mutableSetOf<String>()
        for (i in 0 until adapter.count) {
            adapter.getItem(i)?.let { pkg ->
                if (pkg.selected) {
                    apps.add(pkg.info.packageName)
                }
            }
        }
        
        // Save selected apps
        prefs.setApps(apps)
        
        // Send result back to MainActivity
        val resultIntent = Intent()
        resultIntent.putExtra("app_count", apps.size)
        setResult(RESULT_OK, resultIntent)
    }
}