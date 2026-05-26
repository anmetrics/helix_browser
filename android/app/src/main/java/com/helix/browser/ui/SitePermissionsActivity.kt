package com.helix.browser.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.helix.browser.R

/**
 * Lists every per-origin permission decision the user has saved, grouped by
 * origin, and lets the user revoke them. Decisions are stored under the
 * SharedPreferences key prefix "site_perm:<permission>:<origin>" by
 * [com.helix.browser.utils.Prefs].
 */
class SitePermissionsActivity : BaseActivity() {

    private data class Row(val origin: String, val permission: String, val decision: String)

    private val rows = mutableListOf<Row>()
    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_permissions)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        adapter = Adapter()
        findViewById<RecyclerView>(R.id.recyclerSitePermissions).apply {
            layoutManager = LinearLayoutManager(this@SitePermissionsActivity)
            adapter = this@SitePermissionsActivity.adapter
        }
        load()
    }

    private fun load() {
        rows.clear()
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.all.forEach { (key, value) ->
            // Key format: site_perm:<permission>:<origin>
            if (key.startsWith(PREFIX) && value is String) {
                val rest = key.removePrefix(PREFIX)
                val colon = rest.indexOf(':')
                if (colon > 0) {
                    val permission = rest.substring(0, colon)
                    val origin = rest.substring(colon + 1)
                    rows.add(Row(origin, permission, value))
                }
            }
        }
        rows.sortWith(compareBy({ it.origin }, { it.permission }))
        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tvEmpty).visibility =
            if (rows.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun revoke(row: Row) {
        val key = PREFIX + row.permission + ":" + row.origin
        PreferenceManager.getDefaultSharedPreferences(this).edit().remove(key).apply()
        val idx = rows.indexOf(row)
        if (idx >= 0) {
            rows.removeAt(idx)
            adapter.notifyItemRemoved(idx)
        }
        if (rows.isEmpty()) {
            findViewById<TextView>(R.id.tvEmpty).visibility = android.view.View.VISIBLE
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val tvOrigin: TextView = itemView.findViewById(R.id.tvOrigin)
            val tvDetail: TextView = itemView.findViewById(R.id.tvPermissionDetail)
            val btnRevoke: MaterialButton = itemView.findViewById(R.id.btnRevoke)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_site_permission, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = rows.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = rows[position]
            holder.tvOrigin.text = row.origin
            holder.tvDetail.text = "${labelFor(row.permission)} · ${decisionLabel(row.decision)}"
            holder.btnRevoke.setOnClickListener { revoke(row) }
        }
    }

    private fun labelFor(permission: String): String = when (permission) {
        "geolocation"   -> getString(R.string.site_permission_label_location)
        "camera"        -> getString(R.string.site_permission_label_camera)
        "microphone"    -> getString(R.string.site_permission_label_microphone)
        "notifications" -> getString(R.string.site_permission_label_notifications)
        else            -> permission
    }

    private fun decisionLabel(decision: String): String = when (decision) {
        "allow" -> getString(R.string.site_permission_label_allow)
        "deny"  -> getString(R.string.site_permission_label_deny)
        else    -> decision
    }

    companion object {
        private const val PREFIX = "site_perm:"
    }
}
