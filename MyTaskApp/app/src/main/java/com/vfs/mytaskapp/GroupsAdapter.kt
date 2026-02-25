package com.vfs.mytaskapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroupsViewHolder(rootView: View) : RecyclerView.ViewHolder(rootView) {
    // Corrected the view IDs to match your group_row.xml
    private val groupNameTextView: TextView = itemView.findViewById(R.id.groupNameTextView_id)
    private val groupCountTextView: TextView = itemView.findViewById(R.id.groupCountTextView_id)
    private val dividerView: View = itemView.findViewById(R.id.dividerView_id)

    fun bind(group: Group, isLastItem: Boolean) {
        groupNameTextView.text = group.name
        // Since tasks are loaded separately, we'll show "0 tasks" for now.
        // We can update this later if we fetch task counts.
        groupCountTextView.text = "0 tasks"

        // Hide the divider for the last item in the list
        dividerView.visibility = if (isLastItem) View.GONE else View.VISIBLE
    }
}

class GroupsAdapter(private val listener: GroupListener) : RecyclerView.Adapter<GroupsViewHolder>() {

    // This list will hold the group data from Firebase
    private var groups: List<Group> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupsViewHolder {
        // Inflate your group_row layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.group_row, parent, false)
        return GroupsViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroupsViewHolder, position: Int) {
        val group = groups[position]
        // Bind the data to the view holder
        holder.bind(group, position == groups.size - 1)

        // Pass the actual group object to the listener, which is more robust than passing an index.
        holder.itemView.setOnClickListener {
            listener.groupClicked(group)
        }

        holder.itemView.setOnLongClickListener {
            listener.groupLongClicked(group)
            true // Return true to indicate the event was handled
        }
    }

    override fun getItemCount(): Int = groups.size

    // This function will be called from GroupsActivity to update the data
    fun updateData(newGroups: List<Group>) {
        this.groups = newGroups
        notifyDataSetChanged() // Notify the adapter that the data has changed
    }
}

// Updated the listener to pass the whole Group object instead of just an index.
interface GroupListener {
    fun groupClicked(group: Group)
    fun groupLongClicked(group: Group)
}
