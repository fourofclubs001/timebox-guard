package com.timebox.guard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

/** One installed, launchable app the user can choose to guard. */
data class AppEntry(val label: String, val packageName: String)

/**
 * Backs the app list on the setup screen. Each row shows an app's label and
 * package name with an on/off switch. Tapping anywhere on the row toggles it.
 * Selection is a set of package names; [onSelectionChanged] is called with the
 * updated set every time it changes so the caller can persist it.
 */
class AppListAdapter(
    private val allApps: List<AppEntry>,
    private val selected: MutableSet<String>,
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var visibleApps: List<AppEntry> = allApps

    fun filter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        visibleApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.lowercase(Locale.getDefault()).contains(q) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(q)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun getItemCount(): Int = visibleApps.size

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = visibleApps[position]
        holder.label.text = app.label
        holder.packageName.text = app.packageName
        holder.guardSwitch.isChecked = selected.contains(app.packageName)
        holder.itemView.setOnClickListener {
            val nowChecked = !selected.contains(app.packageName)
            if (nowChecked) selected.add(app.packageName) else selected.remove(app.packageName)
            holder.guardSwitch.isChecked = nowChecked
            onSelectionChanged(selected)
        }
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val label: TextView = itemView.findViewById(R.id.textLabel)
        val packageName: TextView = itemView.findViewById(R.id.textPackage)
        val guardSwitch: Switch = itemView.findViewById(R.id.switchGuard)
    }
}
