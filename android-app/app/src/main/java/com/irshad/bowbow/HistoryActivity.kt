package com.irshad.bowbow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.irshad.bowbow.databinding.ActivityHistoryBinding
import com.irshad.bowbow.databinding.ItemHistoryBinding
import kotlinx.coroutines.launch

/** Task history + reminders, loaded live from the backend.
 *  Two tabs (Tasks / Reminders), newest first. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private var showingTasks = true

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = HistoryAdapter()
        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        binding.tasksTab.setOnClickListener { showTasks() }
        binding.remindersTab.setOnClickListener { showReminders() }
        binding.refreshButton.setOnClickListener { load() }
        binding.backButton.setOnClickListener { finish() }

        showTasks()
    }

    private fun showTasks() {
        showingTasks = true
        binding.tasksTab.isSelected = true
        binding.remindersTab.isSelected = false
        load()
    }

    private fun showReminders() {
        showingTasks = false
        binding.tasksTab.isSelected = false
        binding.remindersTab.isSelected = true
        load()
    }

    private fun load() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyText.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = BowBowApi(Prefs.getBackendUrl(this@HistoryActivity))
                val items: List<HistoryItem> = if (showingTasks) {
                    api.listAllTasks()
                        .sortedByDescending { it.id }
                        .map { HistoryItem(it.title, it.status) }
                } else {
                    api.listAllReminders()
                        .sortedByDescending { it.scheduled_at }
                        .map {
                            HistoryItem(
                                it.title,
                                "${it.status} · ${it.scheduled_at}"
                            )
                        }
                }
                adapter.submit(items)
                binding.emptyText.visibility =
                    if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Toast.makeText(
                    this@HistoryActivity,
                    R.string.load_failed,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    data class HistoryItem(val title: String, val detail: String)

    class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {
        private var items: List<HistoryItem> = emptyList()

        fun submit(newItems: List<HistoryItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemHistoryBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class Holder(private val binding: ItemHistoryBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: HistoryItem) {
                binding.itemTitle.text = item.title
                binding.itemDetail.text = item.detail
            }
        }
    }
}
