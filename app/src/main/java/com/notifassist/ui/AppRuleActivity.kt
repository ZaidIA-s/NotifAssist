package com.notifassist.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notifassist.data.AppRule
import com.notifassist.data.RuleDatabase
import com.notifassist.databinding.ActivityAppRuleBinding
import kotlinx.coroutines.launch

class AppRuleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    private lateinit var binding: ActivityAppRuleBinding
    private var currentRule: AppRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppRuleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }

        lifecycleScope.launch {
            currentRule = RuleDatabase.getInstance(this@AppRuleActivity)
                .appRuleDao().getRuleForPackage(pkg)
            currentRule?.let { populateUi(it) }
        }

        binding.btnSave.setOnClickListener { saveRule() }
        binding.btnDelete.setOnClickListener { deleteRule() }
    }

    private fun populateUi(rule: AppRule) {
        supportActionBar?.title = rule.appLabel
        binding.switchEnabled.isChecked    = rule.isEnabled
        binding.switchReadSender.isChecked = rule.readSender
        binding.switchReadContent.isChecked= rule.readContent
        binding.switchPauseMusic.isChecked = rule.pauseMusic
        binding.etTemplate.setText(rule.messageTemplate)
        binding.sliderInterval.value       = rule.minIntervalSeconds.toFloat()
        binding.sliderHourStart.value      = rule.activeHourStart.toFloat()
        binding.sliderHourEnd.value        = rule.activeHourEnd.toFloat()
        updateIntervalLabel(rule.minIntervalSeconds)
        updateHourLabel(rule.activeHourStart, rule.activeHourEnd)

        binding.sliderInterval.addOnChangeListener { _, v, _ -> updateIntervalLabel(v.toInt()) }
        binding.sliderHourStart.addOnChangeListener { _, _, _ ->
            updateHourLabel(binding.sliderHourStart.value.toInt(), binding.sliderHourEnd.value.toInt())
        }
        binding.sliderHourEnd.addOnChangeListener { _, _, _ ->
            updateHourLabel(binding.sliderHourStart.value.toInt(), binding.sliderHourEnd.value.toInt())
        }
    }

    private fun updateIntervalLabel(sec: Int) {
        binding.tvIntervalLabel.text = "Jeda minimal: ${sec}s"
    }

    private fun updateHourLabel(start: Int, end: Int) {
        binding.tvHourLabel.text = "Aktif: ${start}:00 – ${end}:00"
    }

    private fun saveRule() {
        val rule = currentRule ?: return
        val updated = rule.copy(
            isEnabled       = binding.switchEnabled.isChecked,
            readSender      = binding.switchReadSender.isChecked,
            readContent     = binding.switchReadContent.isChecked,
            pauseMusic      = binding.switchPauseMusic.isChecked,
            messageTemplate = binding.etTemplate.text.toString().ifBlank { rule.messageTemplate },
            minIntervalSeconds = binding.sliderInterval.value.toInt(),
            activeHourStart = binding.sliderHourStart.value.toInt(),
            activeHourEnd   = binding.sliderHourEnd.value.toInt()
        )
        lifecycleScope.launch {
            RuleDatabase.getInstance(this@AppRuleActivity).appRuleDao().upsert(updated)
            Toast.makeText(this@AppRuleActivity, "Tersimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun deleteRule() {
        val rule = currentRule ?: return
        lifecycleScope.launch {
            RuleDatabase.getInstance(this@AppRuleActivity).appRuleDao().delete(rule)
            Toast.makeText(this@AppRuleActivity, "${rule.appLabel} dihapus", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
