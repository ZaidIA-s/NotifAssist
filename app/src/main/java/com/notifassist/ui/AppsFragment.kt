package com.notifassist.ui

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notifassist.data.AppRule
import com.notifassist.data.RuleDatabase
import com.notifassist.databinding.FragmentAppsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppsFragment : Fragment() {

    private var _b: FragmentAppsBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: AppRuleAdapter

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentAppsBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AppRuleAdapter(
            onToggle = { rule, enabled ->
                lifecycleScope.launch {
                    RuleDatabase.getInstance(requireContext())
                        .appRuleDao().upsert(rule.copy(isEnabled = enabled))
                }
            },
            onSettings = { rule ->
                startActivity(
                    android.content.Intent(requireContext(), AppRuleActivity::class.java)
                        .putExtra(AppRuleActivity.EXTRA_PACKAGE, rule.packageName)
                )
            }
        )
        b.rvApps.layoutManager = LinearLayoutManager(requireContext())
        b.rvApps.adapter = adapter

        lifecycleScope.launch {
            RuleDatabase.getInstance(requireContext())
                .appRuleDao().getAllRules()
                .collectLatest { rules ->
                    adapter.submitList(rules)
                    b.tvEmptyState.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
                }
        }

        b.fabAddApp.setOnClickListener { showAddAppDialog() }
    }

    private fun showAddAppDialog() {
        val pm = requireContext().packageManager
        // Gunakan MATCH_ALL agar semua app (termasuk WA) muncul
        val apps = pm.getInstalledApplications(
            android.content.pm.PackageManager.GET_META_DATA or
            android.content.pm.PackageManager.MATCH_ALL
        ).filter { info ->
            val isSys = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            !isSys || pm.getLaunchIntentForPackage(info.packageName) != null
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Pilih aplikasi (${apps.size})")
            .setItems(labels) { _, idx ->
                lifecycleScope.launch {
                    val dao = RuleDatabase.getInstance(requireContext()).appRuleDao()
                    if (dao.getRuleForPackage(apps[idx].packageName) == null) {
                        dao.upsert(AppRule(
                            packageName = apps[idx].packageName,
                            appLabel    = labels[idx]
                        ))
                        Toast.makeText(requireContext(), "${labels[idx]} ditambahkan", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "${labels[idx]} sudah ada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
