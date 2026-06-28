package com.notifassist.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import com.notifassist.R
import com.notifassist.data.AppRule
import com.notifassist.data.RuleDatabase
import com.notifassist.databinding.FragmentHomeBinding
import com.notifassist.service.NotifListenerService
import com.notifassist.service.TtsService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private val handler = Handler(Looper.getMainLooper())

    private val postNotifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> updatePermissionStatus() }

    private val statusUpdater = object : Runnable {
        override fun run() {
            updatePermissionStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        FragmentHomeBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TtsService.start(requireContext())

        b.btnTestTts.setOnClickListener {
            TtsService.speak(requireContext(), "Notif Assist siap")
        }

        b.btnCheckPermissions.setOnClickListener { handlePermissionCheck() }
        b.btnManageApps.setOnClickListener { showAddAppDialog() }

        lifecycleScope.launch {
            RuleDatabase.getInstance(requireContext()).appRuleDao()
                .getAllRules()
                .collectLatest { rules -> updateAppsList(rules) }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdater)
    }

    // ── App list ───────────────────────────────────────────────────────────

    private fun updateAppsList(rules: List<AppRule>) {
        if (_b == null) return
        val ctx = context ?: return
        val dao = RuleDatabase.getInstance(ctx).appRuleDao()

        b.tvHomeAppsEmpty.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        b.llHomeApps.removeAllViews()

        rules.forEachIndexed { index, rule ->
            if (index > 0) b.llHomeApps.addView(dividerView())

            val row = layoutInflater.inflate(R.layout.item_home_app_toggle, b.llHomeApps, false)

            row.findViewById<TextView>(R.id.tvHomeAppName).text = rule.appLabel

            val flags = buildString {
                if (rule.readSender)  append("👤 ")
                if (rule.readContent) append("💬 ")
                if (rule.pauseMusic)  append("⏸ ")
            }.trim()
            row.findViewById<TextView>(R.id.tvHomeAppFlags).text = flags

            val sw = row.findViewById<SwitchMaterial>(R.id.switchHomeEnable)
            sw.isChecked = rule.isEnabled
            sw.setOnCheckedChangeListener { _, checked ->
                lifecycleScope.launch { dao.upsert(rule.copy(isEnabled = checked)) }
            }

            row.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHomeAppSettings)
                .setOnClickListener {
                    startActivity(
                        Intent(ctx, AppRuleActivity::class.java)
                            .putExtra(AppRuleActivity.EXTRA_PACKAGE, rule.packageName)
                    )
                }

            b.llHomeApps.addView(row)
        }
    }

    private fun dividerView(): View {
        val ctx = requireContext()
        val px1  = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        val px16 = (16 * resources.displayMetrics.density).toInt()
        val tv   = TypedValue()
        ctx.theme.resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, tv, true)
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px1
            ).apply { setMargins(px16, 0, px16, 0) }
            setBackgroundColor(tv.data)
        }
    }

    private fun showAddAppDialog() {
        val ctx = requireContext()
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(
            PackageManager.GET_META_DATA or PackageManager.MATCH_ALL
        ).filter { info ->
            val isSys = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            !isSys || pm.getLaunchIntentForPackage(info.packageName) != null
        }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val labels = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()

        AlertDialog.Builder(ctx)
            .setTitle("Pilih aplikasi (${apps.size})")
            .setItems(labels) { _, idx ->
                lifecycleScope.launch {
                    val dao = RuleDatabase.getInstance(ctx).appRuleDao()
                    if (dao.getRuleForPackage(apps[idx].packageName) == null) {
                        dao.upsert(AppRule(
                            packageName = apps[idx].packageName,
                            appLabel    = labels[idx]
                        ))
                        Toast.makeText(ctx, "${labels[idx]} ditambahkan", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "${labels[idx]} sudah ada", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ── Permission checks ──────────────────────────────────────────────────

    private fun isNotifListenerEnabled(): Boolean {
        val ctx = context ?: return false
        val cn = ComponentName(ctx, NotifListenerService::class.java)
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        return flat.contains(cn.flattenToString())
    }

    private fun isPostNotifGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBatteryOptExcluded(): Boolean {
        val ctx = context ?: return false
        return ctx.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    }

    // ── Permission UI ──────────────────────────────────────────────────────

    private fun updatePermissionStatus() {
        if (_b == null) return
        val hasNotifListener = isNotifListenerEnabled()
        val hasPostNotif     = isPostNotifGranted()
        val hasBatteryOpt    = isBatteryOptExcluded()
        val allOk = hasNotifListener && hasPostNotif && hasBatteryOpt
        val missingCount = listOf(hasNotifListener, hasPostNotif, hasBatteryOpt).count { !it }

        if (allOk) {
            b.tvMainStatus.text = "● Aktif"
            b.tvMainStatus.setTextColor(
                resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
            )
        } else {
            b.tvMainStatus.text = "⚠ $missingCount Izin Perlu"
            b.tvMainStatus.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
        }

        setChip(b.tvChipNotif,     hasNotifListener, "Akses Notif")
        setChip(b.tvChipPostNotif, hasPostNotif,     "Izin Sistem")
        setChip(b.tvChipBattery,   hasBatteryOpt,    "Baterai")

        b.btnCheckPermissions.visibility = if (allOk) View.GONE else View.VISIBLE
    }

    private fun setChip(tv: TextView, ok: Boolean, label: String) {
        val ctx = context ?: return
        tv.text = "${if (ok) "✓" else "✗"} $label"
        tv.setTextColor(
            if (ok) resources.getColor(com.google.android.material.R.color.design_default_color_primary, null)
            else ctx.getColor(android.R.color.holo_red_dark)
        )
    }

    // ── Permission flow ────────────────────────────────────────────────────

    private fun handlePermissionCheck() {
        val ctx = context ?: return
        when {
            !isNotifListenerEnabled() ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

            !isPostNotifGranted() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            !isBatteryOptExcluded() -> {
                try {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${ctx.packageName}")
                    ))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }

            else -> Toast.makeText(ctx, "Semua izin aktif!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
