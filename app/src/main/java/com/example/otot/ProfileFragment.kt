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
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
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

        // Initialize views
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

        btnLogout.setOnClickListener { showLogoutDialog() }
        btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }
        btnEditProfile.setOnClickListener { findNavController().navigate(R.id.action_profileFragment_to_editprofileFragment) }
        btnChangePassword.setOnClickListener { findNavController().navigate(R.id.action_profileFragment_to_changePasswordFragment) }

        return view
    }

    private fun showLogoutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)
        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val dialog: AlertDialog = builder.create()

        dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
            val googleSignInClient = GoogleSignIn.getClient(requireContext(), GoogleSignInOptions.DEFAULT_SIGN_IN)
            googleSignInClient.signOut().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    requireActivity().finish()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Failed to log out. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)
        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val dialog: AlertDialog = builder.create()

        dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
            promptReauthentication()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    private fun promptReauthentication() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reauthenticate, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.password_input)
        val confirmButton = dialogView.findViewById<Button>(R.id.btn_confirm)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
        val dialog = builder.create()

        dialogView.findViewById<TextView>(R.id.title).text = "Delete ${username ?: "user"}'s account?"

        confirmButton.setOnClickListener {
            val password = passwordInput.text.toString().trim()
            if (password.isNotEmpty()) {
                reauthenticateUser(password)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter your password", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun reauthenticateUser(password: String) {
        val user = firebaseAuth.currentUser
        user?.let {
            val email = it.email
            if (email != null) {
                val credential = EmailAuthProvider.getCredential(email, password)
                it.reauthenticate(credential).addOnCompleteListener { reAuthTask ->
                    if (reAuthTask.isSuccessful) {
                        deleteUserAccount()
                    } else {
                        Log.e("DeleteAccount", "Re-authentication failed: ${reAuthTask.exception?.message}")
                        Toast.makeText(requireContext(), "Wrong Password", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "User email not found.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), "User not authenticated.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteUserAccount() {
        val user = firebaseAuth.currentUser
        user?.let {
            val uid = it.uid
            firestore.collection("users").document(uid).delete()
                .addOnSuccessListener {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val profilePicRef = storageRef.child("profile_pictures/${uid}_profile.jpg")

                    profilePicRef.delete().addOnSuccessListener {
                        Log.d("DeleteAccount", "Profile picture deleted successfully.")
                        deleteUserRuns(uid)
                        deleteUserImages(uid)
                    }.addOnFailureListener { e ->
                        Log.e("DeleteAccount", "Error deleting profile picture: ${e.message}")
                    }
                }.addOnFailureListener { e ->
                    Log.e("DeleteAccount", "Error deleting user data: ${e.message}")
                }
        }
    }

    private fun deleteUserRuns(uid: String) {
        firestore.collection("history").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot.documents) {
                    document.reference.delete()
                        .addOnSuccessListener {
                            Log.d("DeleteAccount", "Run document deleted successfully.")
                        }.addOnFailureListener { e ->
                            Log.e("DeleteAccount", "Error deleting run document: ${e.message}")
                        }
                }
                deleteFirebaseUser()
            }.addOnFailureListener { e ->
                Log.e("DeleteAccount", "Error fetching runs: ${e.message}")
            }
    }

    private fun deleteUserImages(uid: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        val runsImagesRef = storageRef.child("images/$uid")

        // Delete all images under the user's directory
        runsImagesRef.listAll().addOnSuccessListener { listResult ->
            // Create a list to hold the deletion tasks
            val deleteTasks = mutableListOf<Task<Void>>()

            for (item in listResult.items) {
                // Add each delete task to the list
                deleteTasks.add(item.delete().addOnSuccessListener {
                    Log.d("DeleteAccount", "Image ${item.name} deleted successfully.")
                }.addOnFailureListener { e ->
                    Log.e("DeleteAccount", "Error deleting image ${item.name}: ${e.message}")
                })
            }

            // Wait for all delete tasks to complete
            Tasks.whenAllComplete(deleteTasks).addOnCompleteListener {
                // After all images are deleted, you can log or perform additional actions
                Log.d("DeleteAccount", "All images deleted successfully for user $uid.")
                // Optionally, you can delete the folder reference itself if needed
                // Note: The folder will be removed automatically if it's empty
            }
        }.addOnFailureListener { e ->
            Log.e("DeleteAccount", "Error listing images: ${e.message}")
        }
    }

    private fun deleteFirebaseUser() {
        val user = firebaseAuth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            } else {
                Log.e("DeleteAccount", "Error deleting account: ${task.exception?.message}")
            }
        }
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser
        currentUser?.let {
            val userId = it.uid
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        username = document.getString("username")
                        fullname = document.getString("name")
                        email = it.email

                        val profileImageUrl = document.getString("profileImageUrl")
                        Log.e("ProfileFragment", "Username: $username, Profile Image URL: $profileImageUrl")
                        tvUsername.text = username ?: "No username found"
                        tvFullname.text = fullname ?: "No name found"
                        tvEmail.text = email ?: "No email found"

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.circle_profile)
                                .error(R.drawable.default_profile)
                                .centerInside()
                                .circleCrop()
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.circle_profile)
                        }
                    } else {
                        Toast.makeText(requireContext(), "User data not found in Firestore", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            tvUsername.text = "No user found"
            tvEmail.text = "No email found"
        }
    }
}
