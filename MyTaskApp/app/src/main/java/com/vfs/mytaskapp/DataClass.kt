package com.vfs.mytaskapp

import com.google.firebase.database.Exclude

// This data class is ready for Firebase
data class Task(
    var taskId: String? = null,
    val name: String? = null,
    var completed: Boolean = false
)

// This data class is ready for Firebase
data class Group(
    var groupId: String? = null,
    val name: String? = null,
    // The tasks will be stored as a map in Firebase.
    // The @Exclude annotation prevents this list from being saved directly with the group,
    // as we will save tasks under their own "tasks" node for better management.
    @get:Exclude
    var tasks: MutableList<Task> = mutableListOf()
)
