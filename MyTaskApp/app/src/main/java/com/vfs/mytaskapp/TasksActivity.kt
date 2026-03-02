package com.vfs.mytaskapp

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class TasksActivity : AppCompatActivity(), TaskListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var groupTasksRef: DatabaseReference

    private var groupId: String? = null
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var newTaskEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tasks_layout)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        groupId = intent.getStringExtra("GROUP_ID")
        val groupName = intent.getStringExtra("GROUP_NAME")

        if (auth.currentUser == null || groupId == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.grpNameTextView_id).text = groupName

        groupTasksRef = database.reference.child("groups").child(groupId!!).child("tasks")

        val tasksRv = findViewById<RecyclerView>(R.id.tasksRv_id)
        tasksRv.layoutManager = LinearLayoutManager(this)
        tasksAdapter = TasksAdapter(this)
        tasksRv.adapter = tasksAdapter

        newTaskEditText = findViewById(R.id.editTextText)
        setupAddTaskListener()
        fetchGroupTasks()
    }

    private fun fetchGroupTasks() {
        groupTasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksList = snapshot.children.mapNotNull { it.getValue(Task::class.java) }
                tasksAdapter.updateData(tasksList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupAddTaskListener() {
        newTaskEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                val taskName = newTaskEditText.text.toString().trim()
                if (taskName.isNotEmpty()) {
                    val taskId = groupTasksRef.push().key ?: return@setOnKeyListener true
                    val newTask = Task(taskId, taskName, false)
                    groupTasksRef.child(taskId).setValue(newTask)
                    newTaskEditText.text.clear()
                }
                return@setOnKeyListener true
            }
            false
        }
    }

    override fun taskClicked(task: Task) {
        task.taskId?.let {
            groupTasksRef.child(it).child("completed").setValue(!task.completed)
        }
    }

    override fun taskLongClicked(task: Task) {
        val options = arrayOf("Rename Task", "Delete Task", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Options for ${task.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showRenameTaskDialog(task)
                    1 -> task.taskId?.let { groupTasksRef.child(it).removeValue() }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showRenameTaskDialog(task: Task) {
        val input = TextInputEditText(this)
        input.setText(task.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Task")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && task.taskId != null) {
                    groupTasksRef.child(task.taskId!!).child("name").setValue(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
