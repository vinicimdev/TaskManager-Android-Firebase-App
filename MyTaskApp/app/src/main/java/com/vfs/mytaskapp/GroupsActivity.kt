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

class GroupsActivity : AppCompatActivity(), GroupListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userGroupsMappingRef: DatabaseReference
    private lateinit var allGroupsRef: DatabaseReference

    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var groupsAdapter: GroupsAdapter

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
        
        userGroupsMappingRef = database.reference.child("users").child(currentUser.uid).child("groups")
        allGroupsRef = database.reference.child("groups")

        groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupsAdapter = GroupsAdapter(this)
        groupsRecyclerView.adapter = groupsAdapter

        fetchUserGroups()
    }

    private fun fetchUserGroups() {
        // Use ValueEventListener so it updates whenever the list of group IDs changes
        userGroupsMappingRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val groupIds = snapshot.children.mapNotNull { it.key }
                Log.d("GroupsActivity", "Found group IDs: $groupIds")
                loadGroupDetails(groupIds)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("GroupsActivity", "Failed to fetch group IDs: ${error.message}")
            }
        })
    }

    private fun loadGroupDetails(groupIds: List<String>) {
        if (groupIds.isEmpty()) {
            groupsAdapter.updateData(emptyList())
            return
        }

        // We use a listener on the whole groups node or individual listeners.
        // For efficiency with updates, let's listen to the specific groups.
        val groupsList = mutableMapOf<String, Group>()
        var loadedCount = 0

        for (id in groupIds) {
            allGroupsRef.child(id).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val group = snapshot.getValue(Group::class.java)
                    if (group != null) {
                        groupsList[id] = group
                    } else {
                        groupsList.remove(id)
                    }
                    
                    // Convert map to list and update adapter
                    // We sort by name or ID to keep consistency
                    groupsAdapter.updateData(groupsList.values.toList().sortedBy { it.name })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("GroupsActivity", "Failed to load group $id: ${error.message}")
                }
            })
        }
    }

    fun addNewGroup(view: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("New Group")
        val input = TextInputEditText(this)
        input.hint = "Enter group name"
        builder.setView(input)

        builder.setPositiveButton("Add") { dialog, _ ->
            val groupName = input.text.toString().trim()
            val currentUser = auth.currentUser
            if (groupName.isNotEmpty() && currentUser != null) {
                val groupId = allGroupsRef.push().key ?: return@setPositiveButton
                
                val newGroup = Group(
                    groupId = groupId,
                    name = groupName,
                    ownerId = currentUser.uid,
                    members = mapOf(currentUser.uid to true)
                )

                val updates = hashMapOf<String, Any>(
                    "/groups/$groupId" to newGroup,
                    "/users/${currentUser.uid}/groups/$groupId" to true
                )

                database.reference.updateChildren(updates).addOnSuccessListener {
                    Toast.makeText(this, "Group created!", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener {
                    Toast.makeText(this, "Error creating group: ${it.message}", Toast.LENGTH_SHORT).show()
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

    override fun groupClicked(group: Group) {
        val intent = Intent(this, TasksActivity::class.java)
        intent.putExtra("GROUP_ID", group.groupId)
        intent.putExtra("GROUP_NAME", group.name)
        startActivity(intent)
    }

    override fun groupLongClicked(group: Group) {
        val options = arrayOf("Invite Member", "Rename Group", "Delete Group", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Options for ${group.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showInviteDialog(group)
                    1 -> showRenameGroupDialog(group)
                    2 -> confirmDeleteGroup(group)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showRenameGroupDialog(group: Group) {
        val input = TextInputEditText(this)
        input.setText(group.name)
        AlertDialog.Builder(this)
            .setTitle("Rename Group")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && group.groupId != null) {
                    allGroupsRef.child(group.groupId!!).child("name").setValue(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInviteDialog(group: Group) {
        val input = TextInputEditText(this)
        input.hint = "Receiver Email"
        AlertDialog.Builder(this)
            .setTitle("Invite to ${group.name}")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val email = input.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendInvitation(group, email)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendInvitation(group: Group, receiverEmail: String) {
        val currentUser = auth.currentUser ?: return
        val invitationsRef = database.reference.child("invitations")
        val encodedEmail = receiverEmail.replace(".", ",")
        val inviteId = invitationsRef.child(encodedEmail).push().key ?: return

        val invitation = Invitation(
            invitationId = inviteId,
            groupId = group.groupId,
            groupName = group.name,
            senderEmail = currentUser.email,
            senderUid = currentUser.uid,
            receiverEmail = receiverEmail
        )

        invitationsRef.child(encodedEmail).child(inviteId).setValue(invitation)
            .addOnSuccessListener {
                Toast.makeText(this, "Invitation sent!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteGroup(group: Group) {
        AlertDialog.Builder(this)
            .setTitle("Delete Group")
            .setMessage("Are you sure? This will remove access for all members.")
            .setPositiveButton("Delete") { _, _ ->
                val groupId = group.groupId ?: return@setPositiveButton
                // To properly delete for everyone, we'd need to loop through members.
                // For now, we delete the group and the current user's link.
                val updates = hashMapOf<String, Any?>(
                    "/groups/$groupId" to null,
                    "/users/${auth.currentUser?.uid}/groups/$groupId" to null
                )
                database.reference.updateChildren(updates)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun checkInvitations(view: View? = null) {
        val currentUser = auth.currentUser ?: return
        val encodedEmail = currentUser.email?.replace(".", ",") ?: return
        val invitesRef = database.reference.child("invitations").child(encodedEmail)

        invitesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@GroupsActivity, "No pending invitations", Toast.LENGTH_SHORT).show()
                    return
                }
                for (inviteSnapshot in snapshot.children) {
                    val invite = inviteSnapshot.getValue(Invitation::class.java) ?: continue
                    showAcceptRejectDialog(invite)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAcceptRejectDialog(invite: Invitation) {
        AlertDialog.Builder(this)
            .setTitle("Group Invitation")
            .setMessage("${invite.senderEmail} invited you to join '${invite.groupName}'")
            .setPositiveButton("Accept") { _, _ -> acceptInvite(invite) }
            .setNegativeButton("Reject") { _, _ -> rejectInvite(invite) }
            .show()
    }

    private fun acceptInvite(invite: Invitation) {
        val currentUser = auth.currentUser ?: return
        val groupId = invite.groupId ?: return
        val encodedEmail = currentUser.email?.replace(".", ",") ?: return

        val updates = hashMapOf<String, Any?>(
            "/users/${currentUser.uid}/groups/$groupId" to true,
            "/groups/$groupId/members/${currentUser.uid}" to true,
            "/invitations/$encodedEmail/${invite.invitationId}" to null
        )

        database.reference.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Joined ${invite.groupName}!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rejectInvite(invite: Invitation) {
        val encodedEmail = auth.currentUser?.email?.replace(".", ",") ?: return
        database.reference.child("invitations").child(encodedEmail)
            .child(invite.invitationId!!).removeValue()
    }
}
