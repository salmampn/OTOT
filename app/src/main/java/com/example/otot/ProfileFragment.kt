package com.example.otot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.EmailAuthProvider

class ProfileFragment : Fragment() {

    private lateinit var btnLogout: LinearLayout
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnDeleteAccount: LinearLayout
    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView
    private lateinit var tvFullname: TextView
    private lateinit var tvEmail: TextView

    private lateinit var firestore: FirebaseFirestore
    private var username: String? = null // Store username for reauthentication
    private var fullname: String? = null
    private var email: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        btnEditProfile = view.findViewById(R.id.edit_profile)
        btnLogout = view.findViewById(R.id.logout)
        tvUsername = view.findViewById(R.id.tv_username)
        btnChangePassword = view.findViewById(R.id.change_password)
        profileImage = view.findViewById(R.id.profile_image)
        btnDeleteAccount = view.findViewById(R.id.delete_account)
        tvFullname = view.findViewById(R.id.tv_fullname)
        tvEmail = view.findViewById(R.id.tv_email)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        loadUserProfile()

        btnLogout.setOnClickListener {
            // Inflate custom dialog layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)

            // Create an AlertDialog to confirm logout
            val builder = AlertDialog.Builder(requireContext())
                .setView(dialogView) // Set the custom layout
                .setCancelable(false) // Prevent dialog from being canceled by tapping outside

            val dialog: AlertDialog = builder.create()

            dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
                // User confirmed, proceed to log out
                val googleSignInClient = GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN)

                // Sign out from Google Sign-In
                googleSignInClient.signOut().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Sign out from Firebase Auth
                        FirebaseAuth.getInstance().signOut()

                        // Navigate to SplashActivity
                        val intent = Intent(requireContext(), SplashActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()

                        // Dismiss dialog
                        dialog.dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to log out. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
                // User cancelled the logout, dismiss the dialog
                dialog.dismiss()
            }

            // Show the dialog
            dialog.show()
            dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
            dialog.window?.setGravity(Gravity.BOTTOM)
        }

        btnDeleteAccount.setOnClickListener {
            // Inflate custom dialog layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)

            // Create an AlertDialog to confirm account deletion
            val builder = AlertDialog.Builder(requireContext())
                .setView(dialogView) // Set the custom layout
                .setCancelable(false) // Prevent dialog from being canceled by tapping outside

            val dialog: AlertDialog = builder.create()

            dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
                promptReauthentication()
                dialog.dismiss() // Dismiss dialog after operation
            }

            dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
                // User cancelled the deletion, dismiss the dialog
                dialog.dismiss()
            }

            // Show the dialog
            dialog.show()
            dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
            dialog.window?.setGravity(Gravity.BOTTOM)
        }

        btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editprofileFragment)
        }

        btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_changePasswordFragment)
        }

        return view
    }

    private fun promptReauthentication() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reauthenticate, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.password_input)
        val confirmButton = dialogView.findViewById<Button>(R.id.btn_confirm)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true) // Allow the dialog to be canceled
        val dialog = builder.create()

        // Set the title to "Delete [username]'s account?"
        dialogView.findViewById<TextView>(R.id.title).text = "Delete ${username}'s account?"

        confirmButton.setOnClickListener {
            val password = passwordInput.text.toString().trim()
            if (password.isNotEmpty()) {
                reauthenticateUser(password)
                dialog.dismiss() // Dismiss the dialog after processing
            } else {
                Toast.makeText(requireContext(), "Please enter your password", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun reauthenticateUser(password: String) {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val email = user.email // Get the user's email
            if (email != null) {
                val credential = EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                    if (reAuthTask.isSuccessful) {
                        // Re-authentication successful, proceed with account deletion
                        deleteUserAccount()
                    } else {
                        // Handle re-authentication failure
                        Log.e("DeleteAccount", "Re-authentication failed: ${reAuthTask.exception?.message}")
                        Toast.makeText(requireContext(), "Wrong Password", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "User email not found.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteUserAccount() {
        val user = firebaseAuth.currentUser
        user?.let {
            val uid = it.uid
            // Step 1: Remove related data from Firestore
            firestore.collection("users").document(uid).delete()
                .addOnSuccessListener {
                    // Step 2: Delete user profile picture from Firebase Storage
                    val storageRef = FirebaseStorage.getInstance().reference
                    val profilePicRef = storageRef.child("profile_pictures/${uid}_profile.jpg") // Fixed path

                    profilePicRef.delete().addOnSuccessListener {
                        // Profile picture deleted successfully
                        Log.d("DeleteAccount", "Profile picture deleted successfully.")

                        // Step 3: Delete related documents from the runs collection
                        firestore.collection("runs").whereEqualTo("userId", uid).get()
                            .addOnSuccessListener { querySnapshot ->
                                for (document in querySnapshot.documents) {
                                    document.reference.delete()
                                        .addOnSuccessListener {
                                            Log.d("DeleteAccount", "Run document deleted successfully.")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("DeleteAccount", "Error deleting run document: ${e.message}")
                                        }
                                }

                                // Step 4: Delete user from Firebase Authentication
                                user.delete()
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            // Account deletion successful
                                            val intent = Intent(requireContext(), SplashActivity::class.java)
                                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            startActivity(intent)
                                            requireActivity().finish()
                                        } else {
                                            // Handle failure in account deletion
                                            Log.e("DeleteAccount", "Error deleting account: ${task.exception?.message}")
                                        }
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("DeleteAccount", "Error fetching runs: ${e.message}")
                            }
                    }.addOnFailureListener { e ->
                        // Handle any errors in deleting the profile picture
                        Log.e("DeleteAccount", "Error deleting profile picture: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    // Handle any errors in deleting user data
                    Log.e("DeleteAccount", "Error deleting user data: ${e.message}")
                }
        }
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            // Fetch user data from Firestore
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        username = document.getString("username") // Store username for reauthentication
                        fullname = document.getString("name")

                        // Get email from Firebase Authentication
                        email = currentUser.email // Fetch email from Firebase Authentication

                        val profileImageUrl = document.getString("profileImageUrl")
                        Log.e("ProfileFragment", "Username: $username, Profile Image URL: $profileImageUrl")
                        tvUsername.text = username ?: "No username found"
                        tvFullname.text = fullname ?: "No name found"
                        tvEmail.text = email ?: "No email found" // Display email

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.circle_profile) // Add a placeholder image in your resources
                                .error(R.drawable.default_profile)  // Add an error image in your resources
                                .centerInside()
                                .circleCrop()
                                .into(profileImage)
                        } else {
                            // Set a default circular image or handle missing image case
                            profileImage.setImageResource(R.drawable.circle_profile)
                        }
                    } else {
                        Toast.makeText(requireContext(), "User data not found in Firestore", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
        } else {
            tvUsername.text = "No user found"
            tvEmail.text = "No email found"
        }
    }
}