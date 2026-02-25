package com.vfs.mytaskapp

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vfs.mytaskapp.TaskListener
import com.vfs.mytaskapp.TasksAdapter

class TasksActivity : AppCompatActivity(), TaskListener {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    // This reference will now point to /users/{uid}/groups/{gid}/tasks
    private lateinit var groupTasksRef: DatabaseReference

    // Group and UI
    private var thisGroup: Group? = null
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var newTaskEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tasks_layout)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // --- Receiving Group Info ---
        val groupId = intent.getStringExtra("GROUP_ID")
        val groupName = intent.getStringExtra("GROUP_NAME")

        val currentUser = auth.currentUser
        if (currentUser == null || groupId == null) {
            Toast.makeText(this, "Error: User not logged in or group not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        thisGroup = Group(groupId, groupName)
        val grpTextView = findViewById<TextView>(R.id.grpNameTextView_id)
        grpTextView.text = groupName

        // --- FIXED: Database Reference ---
        // Point to the 'tasks' node *inside* the specific group.
        groupTasksRef = database.reference
            .child("users")
            .child(currentUser.uid)
            .child("groups") // Point to 'groups'
            .child(groupId)  // Find the specific group
            .child("tasks")  // And then its 'tasks' child

        // --- RecyclerView and Adapter ---
        val tasksRv = findViewById<RecyclerView>(R.id.tasksRv_id)
        tasksRv.layoutManager = LinearLayoutManager(this)
        tasksAdapter = TasksAdapter(this)
        tasksRv.adapter = tasksAdapter

        // --- UI Setup ---
        newTaskEditText = findViewById(R.id.editTextText)
        setupAddTaskListener()

        // Fetch tasks from Firebase
        fetchGroupTasks()
    }

    private fun fetchGroupTasks() {
        groupTasksRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksList = mutableListOf<Task>()
                for (taskSnapshot in snapshot.children) {
                    val task = taskSnapshot.getValue(Task::class.java)
                    task?.let { tasksList.add(it) }
                }
                Log.d("TasksActivity", "Fetched ${tasksList.size} tasks from inside the group.")
                tasksAdapter.updateData(tasksList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TasksActivity", "Failed to fetch tasks: ${error.message}")
                Toast.makeText(baseContext, "Failed to load tasks.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupAddTaskListener() {
        newTaskEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                addNewTask()
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun addNewTask() {
        val taskName = newTaskEditText.text.toString().trim()
        if (taskName.isEmpty()) {
            Toast.makeText(this, "Task name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val taskId = groupTasksRef.push().key
        if (taskId == null) {
            Toast.makeText(this, "Could not create task ID.", Toast.LENGTH_SHORT).show()
            return
        }

        val newTask = Task(taskId, taskName, false)
        groupTasksRef.child(taskId).setValue(newTask)
            .addOnSuccessListener {
                Toast.makeText(this, "Task added!", Toast.LENGTH_SHORT).show()
                newTaskEditText.text.clear()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to add task: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun taskLongClicked(task: Task) {
        task.taskId?.let {
            groupTasksRef.child(it).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "Task removed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun taskClicked(task: Task) {
        task.taskId?.let {
            val newCompletedStatus = !task.completed
            groupTasksRef.child(it).child("completed").setValue(newCompletedStatus)
        }
    }
}
