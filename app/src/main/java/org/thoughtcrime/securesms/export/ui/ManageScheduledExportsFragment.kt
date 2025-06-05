package org.thoughtcrime.securesms.export.ui

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
// import org.thoughtcrime.securesms.R // Conceptual R file: Assume R.layout.fragment_manage_scheduled_exports and R.layout.list_item_scheduled_export exist
import org.thoughtcrime.securesms.export.ChatExportScheduler
import org.thoughtcrime.securesms.export.model.PersistedScheduleData


class ManageScheduledExportsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noSchedulesTextView: TextView
    private lateinit var scheduler: ChatExportScheduler
    private lateinit var scheduledExportsAdapter: ScheduledExportsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // In a real scenario, this would be:
        // val view = inflater.inflate(R.layout.fragment_manage_scheduled_exports, container, false)
        // recyclerView = view.findViewById(R.id.scheduled_exports_recycler_view)
        // noSchedulesTextView = view.findViewById(R.id.no_schedules_text_view)
        // For now, create basic components programmatically
        return createConceptualView(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduler = ChatExportScheduler(requireContext().applicationContext)

        scheduledExportsAdapter = ScheduledExportsAdapter { schedule ->
            scheduler.cancelScheduledExport(schedule.uniqueWorkName)
            Toast.makeText(requireContext(), "Cancelled schedule for ${schedule.chatName}", Toast.LENGTH_SHORT).show()
            loadScheduledExports() // Refresh list
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduledExportsAdapter
        }

        loadScheduledExports()
    }

    private fun loadScheduledExports() {
        val schedules = scheduler.getAllScheduledExportConfigs()
        if (schedules.isEmpty()) {
            recyclerView.visibility = View.GONE
            noSchedulesTextView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            noSchedulesTextView.visibility = View.GONE
            scheduledExportsAdapter.submitList(schedules)
        }
    }

    private fun createConceptualView(context: Context): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(16) // For dp, density would be needed. Using raw pixels for simplicity here.
        }
        noSchedulesTextView = TextView(context).apply {
            text = "No chat exports currently scheduled."
            // id = R.id.no_schedules_text_view // Conceptual
            visibility = View.GONE
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                this.gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 16
            }
        }
        recyclerView = RecyclerView(context).apply {
            // id = R.id.scheduled_exports_recycler_view // Conceptual
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        layout.addView(noSchedulesTextView)
        layout.addView(recyclerView)
        return layout
    }
}

class ScheduledExportsAdapter(
    private val onCancelClick: (PersistedScheduleData) -> Unit
) : RecyclerView.Adapter<ScheduledExportsAdapter.ViewHolder>() {

    private var schedules: List<PersistedScheduleData> = emptyList()

    fun submitList(newSchedules: List<PersistedScheduleData>) {
        schedules = newSchedules
        notifyDataSetChanged() // In a real app, use DiffUtil for better performance
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // In a real scenario, this would be:
        // val view = LayoutInflater.from(parent.context)
        //    .inflate(R.layout.list_item_scheduled_export, parent, false)
        val view = createConceptualListItemView(parent.context)
        return ViewHolder(view, onCancelClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(schedules[position])
    }

    override fun getItemCount(): Int = schedules.size

    class ViewHolder(
        itemView: View,
        private val onCancelClick: (PersistedScheduleData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        // Accessing views created in createConceptualListItemView
        private val chatNameTextView: TextView = itemView.findViewWithTag("chatNameTextViewTag")
        private val detailsTextView: TextView = itemView.findViewWithTag("detailsTextViewTag")
        private val cancelButton: Button = itemView.findViewWithTag("cancelButtonTag")

        fun bind(schedule: PersistedScheduleData) {
            chatNameTextView.text = schedule.chatName
            val details = "Format: ${schedule.format}, Dest: ${schedule.destination}, Freq: ${schedule.frequency}" +
                          (if (!schedule.apiUrl.isNullOrBlank()) ", API: Hidden" else "") // Don't show full API URL here
            detailsTextView.text = details
            cancelButton.text = "Cancel" // Ensure button has text
            cancelButton.setOnClickListener { onCancelClick(schedule) }
        }
    }

    private fun createConceptualListItemView(context: Context): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(16, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val chatName = TextView(context).apply {
            tag = "chatNameTextViewTag"
            // Example: setTextAppearance(context, android.R.style.TextAppearance_Material_Subtitle1)
        }
        val details = TextView(context).apply {
            tag = "detailsTextViewTag"
            // Example: setTextAppearance(context, android.R.style.TextAppearance_Material_Body2)
        }
        textLayout.addView(chatName)
        textLayout.addView(details)

        val button = Button(context).apply {
            tag = "cancelButtonTag"
            // Example: layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
         } // Text set in bind
        layout.addView(textLayout)
        layout.addView(button)
        return layout
    }
}
