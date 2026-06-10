package com.notifassist.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notifassist.data.AppRule
import com.notifassist.databinding.ItemAppRuleBinding

class AppRuleAdapter(
    private val onToggle: (AppRule, Boolean) -> Unit,
    private val onSettings: (AppRule) -> Unit
) : ListAdapter<AppRule, AppRuleAdapter.ViewHolder>(DIFF) {

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppRule>() {
            override fun areItemsTheSame(a: AppRule, b: AppRule) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppRule, b: AppRule) = a == b
        }
    }

    inner class ViewHolder(private val b: ItemAppRuleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(rule: AppRule) {
            b.tvAppName.text  = rule.appLabel
            b.tvPackage.text  = rule.packageName
            b.switchEnable.isChecked = rule.isEnabled

            // Hindari trigger callback saat binding
            b.switchEnable.setOnCheckedChangeListener(null)
            b.switchEnable.setOnCheckedChangeListener { _, checked ->
                onToggle(rule, checked)
            }
            b.btnSettings.setOnClickListener { onSettings(rule) }

            // Tunjukkan indikator apa yang aktif
            val flags = buildString {
                if (rule.readSender)  append("👤 ")
                if (rule.readContent) append("💬 ")
                if (rule.pauseMusic)  append("⏸ ")
            }
            b.tvFlags.text = flags.trim().ifEmpty { "hanya judul" }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAppRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))
}
