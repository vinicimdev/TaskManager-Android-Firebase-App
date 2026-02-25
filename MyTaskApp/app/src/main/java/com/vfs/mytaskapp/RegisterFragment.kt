package com.vfs.mytaskapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class RegisterFragment : Fragment() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var progressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        
        // Initialize views
        etName = view.findViewById(R.id.et_name)
        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        etConfirmPassword = view.findViewById(R.id.et_confirm_password)
        btnRegister = view.findViewById(R.id.btn_register)
        progressBar = view.findViewById(R.id.progress_bar)
        
        // Register button
        btnRegister.setOnClickListener {
            registerUser()
        }
    }
    
    private fun registerUser() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        
        if (!validateInput(name, email, password, confirmPassword)) {
            return
        }
        
        setLoading(true)
        
        // Create user with Firebase Authentication
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Registration successful
                    val user = auth.currentUser
                    val userId = user?.uid ?: ""
                    
                    // Save user data to Realtime Database
                    val userMap = hashMapOf(
                        "name" to name,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis()
                    )
                    
                    database.reference
                        .child("users")
                        .child(userId)
                        .setValue(userMap)
                        .addOnSuccessListener {
                            setLoading(false)
                            Toast.makeText(
                                context,
                                "Registration successful!",
                                Toast.LENGTH_SHORT
                            ).show()
                            navigateToMainActivity()
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Toast.makeText(
                                context,
                                "Failed to save user data: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    setLoading(false)
                    Toast.makeText(
                        context,
                        "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
    
    private fun validateInput(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        
        if (name.isEmpty()) {
            etName.error = "Name is required"
            etName.requestFocus()
            return false
        }
        
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            etEmail.requestFocus()
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email"
            etEmail.requestFocus()
            return false
        }
        
        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            etPassword.requestFocus()
            return false
        }
        
        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            etPassword.requestFocus()
            return false
        }
        
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.error = "Please confirm your password"
            etConfirmPassword.requestFocus()
            return false
        }
        
        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return false
        }
        
        return true
    }
    
    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            btnRegister.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        } else {
            btnRegister.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        }
    }
    
    private fun navigateToMainActivity() {
        val intent = Intent(requireContext(), GroupsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
}
