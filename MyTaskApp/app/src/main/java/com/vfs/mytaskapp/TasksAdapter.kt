package com.vfs.mytaskapp // FIXED: Correct package name

// FIXED: Removed incorrect and duplicate imports, added necessary ones
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TasksViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    // Get references to the views in your task_row.xml
    private val taskTextView: TextView = itemView.findViewById(R.id.taskTextView_id)
    private val checkBox: CheckBox = itemView.findViewById(R.id.taskCompletionCheckBox_id)
    private val divider: View = itemView.findViewById(R.id.tasksDividerView_id)

    fun bind(task: Task, isLastItem: Boolean) {
        taskTextView.text = task.name
        checkBox.isChecked = task.completed

        // Strike-through text if task is completed
        if (task.completed) {
            taskTextView.paintFlags = taskTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            taskTextView.paintFlags = taskTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Hide the divider for the last item
        divider.visibility = if (isLastItem) View.INVISIBLE else View.VISIBLE
    }
}

class TasksAdapter(private val listener: TaskListener) : RecyclerView.Adapter<TasksViewHolder>() {

    // This list will hold the task data from Firebase
    private var tasks: List<Task> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TasksViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.task_row, parent, false)
        return TasksViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: TasksViewHolder, position: Int) {
        val task = tasks[position]
        holder.bind(task, position == tasks.size - 1)

        // Set listeners and pass the whole Task object
        holder.itemView.setOnClickListener {
            listener.taskClicked(task)
        }
        holder.itemView.setOnLongClickListener {
            listener.taskLongClicked(task)
            true // Indicate the event is handled
        }
        // Also handle the checkbox click directly for immediate visual feedback
        holder.itemView.findViewById<CheckBox>(R.id.taskCompletionCheckBox_id).setOnClickListener {
            listener.taskClicked(task)
        }
    }

    // This function is called from TasksActivity to update the adapter's data
    fun updateData(newTasks: List<Task>) {
        this.tasks = newTasks
        notifyDataSetChanged()
    }
}

// This is the new, correct listener interface. It matches the one in TasksActivity.
interface TaskListener {
    fun taskClicked(task: Task)
    fun taskLongClicked(task: Task)
}
