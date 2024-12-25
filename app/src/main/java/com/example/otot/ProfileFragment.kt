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
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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

    // Initialize views
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
        btnChangePassword.setOnClickListener { checkIfGoogleSignIn() }

        return view
    }

    // Check if user is signed in with Google before changing password
    private fun checkIfGoogleSignIn() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            for (profile in user.providerData) {
                if (profile.providerId == GoogleAuthProvider.PROVIDER_ID) {
                    showAlert()
                    return
                }
            }
            // Navigate to ChangePasswordFragment if not signed in with Google
            findNavController().navigate(R.id.action_profileFragment_to_changePasswordFragment)
        }
    }

    // Show alert dialog for Google sign-in users
    private fun showAlert() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_google_signin_alert, null)
        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val dialog: AlertDialog = builder.create()

        dialogView.findViewById<Button>(R.id.btn_ok).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.window?.setGravity(Gravity.BOTTOM)
    }

    // Show logout dialog
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

    // Show delete account dialog
    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)
        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val dialog: AlertDialog = builder.create()

        dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
            val user = firebaseAuth.currentUser
            user?.let {
                // Check authentication provider
                val providers = user.providerData.map { it.providerId }
                when {
                    providers.contains("google.com") -> promptGoogleReauthentication()
                    providers.contains("password") -> promptPasswordReauthentication()
                    else -> Toast.makeText(requireContext(), "Unsupported authentication method", Toast.LENGTH_SHORT).show()
                }
            }
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

    // Prompt user to reauthenticate with Google
    private fun promptGoogleReauthentication() {
        val googleSignInClient = GoogleSignIn.getClient(requireContext(),
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )

        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    // Prompt user to reauthenticate with password
    private fun promptPasswordReauthentication() {
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
                reauthenticateWithPassword(password)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Please enter your password", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // Reauthenticate user with password before deleting account
    private fun reauthenticateWithPassword(password: String) {
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
            }
        }
    }

    // Handle Google sign-in result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                account?.let {
                    val credential = GoogleAuthProvider.getCredential(it.idToken, null)
                    val user = firebaseAuth.currentUser
                    user?.reauthenticate(credential)?.addOnCompleteListener { reAuthTask ->
                        if (reAuthTask.isSuccessful) {
                            deleteUserAccount()
                        } else {
                            Log.e("DeleteAccount", "Google re-authentication failed: ${reAuthTask.exception?.message}")
                            Toast.makeText(requireContext(), "Re-authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: ApiException) {
                Log.e("DeleteAccount", "Google sign-in failed: ${e.message}")
                Toast.makeText(requireContext(), "Google sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Delete user account and associated data
    private fun deleteUserAccount() {
        val user = firebaseAuth.currentUser
        user?.let { firebaseUser ->
            val uid = firebaseUser.uid

            // Delete user document from Firestore
            firestore.collection("users").document(uid).delete()
                .addOnSuccessListener {
                    // After Firestore document is deleted, proceed with other deletions
                    deleteUserStorage(uid)
                }
                .addOnFailureListener { e ->
                    Log.e("DeleteAccount", "Error deleting user data: ${e.message}")
                    // Continue with storage deletion even if Firestore deletion fails
                    deleteUserStorage(uid)
                }
        }
    }

    // Delete user storage data
    private fun deleteUserStorage(uid: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        val profilePicRef = storageRef.child("profile_pictures/${uid}_profile.jpg")

        // Check if profile picture exists before attempting to delete
        profilePicRef.metadata
            .addOnSuccessListener {
                // Profile picture exists, delete it
                profilePicRef.delete()
                    .addOnSuccessListener {
                        Log.d("DeleteAccount", "Profile picture deleted successfully")
                        deleteUserImages(uid)
                    }
                    .addOnFailureListener { e ->
                        Log.e("DeleteAccount", "Error deleting profile picture: ${e.message}")
                        // Continue with other deletions even if profile picture deletion fails
                        deleteUserImages(uid)
                    }
            }
            .addOnFailureListener {
                // Profile picture doesn't exist, skip deletion and continue
                Log.d("DeleteAccount", "No profile picture found to delete")
                deleteUserImages(uid)
            }
    }

    // Delete user images
    private fun deleteUserImages(uid: String) {
        val storageRef = FirebaseStorage.getInstance().reference
        val runsImagesRef = storageRef.child("images/$uid")

        runsImagesRef.listAll()
            .addOnSuccessListener { listResult ->
                if (listResult.items.isEmpty()) {
                    // No images found, proceed to delete runs
                    Log.d("DeleteAccount", "No images found for user $uid")
                    deleteUserRuns(uid)
                    return@addOnSuccessListener
                }

                var deletedCount = 0
                val totalItems = listResult.items.size

                for (item in listResult.items) {
                    item.delete()
                        .addOnSuccessListener {
                            deletedCount++
                            Log.d("DeleteAccount", "Deleted image ${item.name}")

                            // Proceed to delete runs after all images are processed
                            if (deletedCount == totalItems) {
                                deleteUserRuns(uid)
                            }
                        }
                        .addOnFailureListener { e ->
                            deletedCount++
                            Log.e("DeleteAccount", "Failed to delete image ${item.name}: ${e.message}")

                            // Continue to delete runs even if some images fail to delete
                            if (deletedCount == totalItems) {
                                deleteUserRuns(uid)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "Error listing images: ${e.message}")
                // Continue with runs deletion even if listing images fails
                deleteUserRuns(uid)
            }
    }

    // Delete user runs
    private fun deleteUserRuns(uid: String) {
        firestore.collection("history").whereEqualTo("userId", uid).get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // No runs found, proceed to delete user account
                    deleteFirebaseUser()
                    return@addOnSuccessListener
                }

                var deletedCount = 0
                val totalDocuments = querySnapshot.size()

                for (document in querySnapshot.documents) {
                    document.reference.delete()
                        .addOnSuccessListener {
                            deletedCount++
                            Log.d("DeleteAccount", "Run document deleted successfully")

                            // Delete Firebase user after all runs are processed
                            if (deletedCount == totalDocuments) {
                                deleteFirebaseUser()
                            }
                        }
                        .addOnFailureListener { e ->
                            deletedCount++
                            Log.e("DeleteAccount", "Error deleting run document: ${e.message}")

                            // Continue to delete user even if some runs fail to delete
                            if (deletedCount == totalDocuments) {
                                deleteFirebaseUser()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("DeleteAccount", "Error fetching runs: ${e.message}")
                // Continue with user deletion even if fetching runs fails
                deleteFirebaseUser()
            }
    }

    // Delete Firebase user account
    private fun deleteFirebaseUser() {
        val user = firebaseAuth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("DeleteAccount", "User account deleted successfully")
                startActivity(Intent(requireContext(), SplashActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            } else {
                Log.e("DeleteAccount", "Error deleting account: ${task.exception?.message}")
                Toast.makeText(requireContext(),
                    "Failed to delete account. Please try again later.",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load user profile data from Firestore
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
                }.addOnFailureListener { exception ->
                    Log.e("ProfileFragment", "Error loading user profile: ${exception.message}")
                    Toast.makeText(requireContext(), "Failed to load user profile. Please try again later.", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            tvUsername.text = "No user found"
            tvEmail.text = "No email found"
        }
    }

    // Handle Google sign-in result
    companion object {
        private const val RC_SIGN_IN = 9001
    }
}