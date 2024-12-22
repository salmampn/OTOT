package com.example.otot

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment() {
    private lateinit var currentPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_change_password, container, false)

        // Inisialisasi FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Inisialisasi EditText dan Button
        currentPasswordEditText = view.findViewById(R.id.current_password)
        newPasswordEditText = view.findViewById(R.id.new_password)
        confirmPasswordEditText = view.findViewById(R.id.confirm_password)
        saveButton = view.findViewById(R.id.btn_save)

        // Set listener untuk tombol simpan
        saveButton.setOnClickListener {
            changePassword()
        }
        return view
    }

    private fun changePassword() {
        val currentPassword = currentPasswordEditText.text.toString().trim()
        val newPassword = newPasswordEditText.text.toString().trim()
        val confirmPassword = confirmPasswordEditText.text.toString().trim()

        // Validasi input
        if (TextUtils.isEmpty(currentPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(activity, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(activity, "New password and confirm password do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Mendapatkan user saat ini
        val user = auth.currentUser
        if (user != null && user.email != null) {
            // Log email dan password saat ini
            Log.d("ChangePasswordFragment", "Current User Email: ${user.email}")
            Log.d("ChangePasswordFragment", "Current Password: $currentPassword")

            // Membuat credential untuk reauthentication
            val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
            user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    // Jika reauthentication berhasil, update password
                    user.updatePassword(newPassword).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            Toast.makeText(activity, "Password changed successfully", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_changePasswordFragment_to_profileFragment)
                        } else {
                            Toast.makeText(activity, "Failed to update password", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(activity, "Authentication failed: ${reauthTask.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(activity, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

}
