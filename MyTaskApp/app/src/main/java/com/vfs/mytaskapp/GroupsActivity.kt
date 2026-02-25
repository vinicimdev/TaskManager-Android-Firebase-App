package com.vfs.mytaskapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

// Implement the GroupListener interface
class GroupsActivity : AppCompatActivity(), GroupListener {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userGroupsRef: DatabaseReference

    // UI Views
    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var logoutButton: Button
    private lateinit var groupsAdapter: GroupsAdapter // Declare the adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.groups_layout)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            showLogin()
            return
        }

        groupsRecyclerView = findViewById(R.id.groupsRv_id)
        logoutButton = findViewById(R.id.statusButton_id)

        // Setup database reference
        userGroupsRef = database.reference.child("users").child(currentUser.uid).child("groups")

        // --- Setup RecyclerView and Adapter ---
        groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupsAdapter = GroupsAdapter(this) // Initialize the adapter with the listener
        groupsRecyclerView.adapter = groupsAdapter // Set the adapter

        fetchUserGroups()
    }

    private fun fetchUserGroups() {
        userGroupsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupsList = mutableListOf<Group>()
                for (groupSnapshot in snapshot.children) {
                    val group = groupSnapshot.getValue(Group::class.java)
                    group?.let { groupsList.add(it) }
                }

                Log.d("GroupsActivity", "Fetched ${groupsList.size} groups from Firebase.")
                // --- Update the adapter with the new list ---
                groupsAdapter.updateData(groupsList)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GroupsActivity", "Failed to fetch groups: ${error.message}")
                Toast.makeText(baseContext, "Failed to fetch groups.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun addNewGroup(view: View) {
        // Show a dialog to get the group name from the user
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Group")
        val input = TextInputEditText(this)
        input.hint = "Enter group name"
        builder.setView(input)

        builder.setPositiveButton("Add") { dialog, _ ->
            val groupName = input.text.toString().trim()
            if (groupName.isNotEmpty()) {
                val groupId = userGroupsRef.push().key
                if (groupId == null) {
                    Toast.makeText(this, "Couldn't get a unique ID.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newGroup = Group(groupId, groupName)
                userGroupsRef.child(groupId).setValue(newGroup)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Group '$groupName' added!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add group: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun showLogin(view: View? = null) {
        auth.signOut()
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // --- Implementing the GroupListener methods ---

    override fun groupClicked(group: Group) {
        val intent = Intent(this, TasksActivity::class.java)
        // Pass the group ID and name to the TasksActivity
        intent.putExtra("GROUP_ID", group.groupId)
        intent.putExtra("GROUP_NAME", group.name)
        startActivity(intent)
    }

    override fun groupLongClicked(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure you want to delete the group '${group.name}' and all its tasks?")
            .setPositiveButton("Delete") { _, _ ->
                group.groupId?.let { groupId ->
                    // Because tasks are nested, deleting the group automatically deletes its tasks.
                    userGroupsRef.child(groupId).removeValue()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Group and all its tasks removed", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

