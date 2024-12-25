package com.example.otot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class InitProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var genderSpinner: Spinner
    private lateinit var continueButton: TextView
    private lateinit var nameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View

    private val PICK_IMAGE_REQUEST = 1
    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_init_profile, container, false)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize the ProgressBar and loading overlay
        progressBar = view.findViewById(R.id.progressBar)
        loadingOverlay = view.findViewById(R.id.loading_overlay)

        profileImageView = view.findViewById(R.id.profile_image)
        genderSpinner = view.findViewById(R.id.gender_spinner)
        continueButton = view.findViewById(R.id.btnContinue)
        nameInput = view.findViewById(R.id.name_input)
        usernameInput = view.findViewById(R.id.username_input)

        setupGenderSpinner()

        // Load data from Firestore
        loadUserData()

        // Handle profile picture click
        profileImageView.setOnClickListener {
            openGallery()
        }

        continueButton.setOnClickListener {
            val selectedGender = genderSpinner.selectedItem.toString()
            val name = nameInput.text.toString().trim()
            val username = usernameInput.text.toString().trim()

            if (name.isEmpty() || username.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show()
            } else {
                // Upload image and save profile
                saveProfile(name, username, selectedGender)
            }
        }
        return view
    }

    // Method to open gallery for profile picture selection
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    // Handle result after choosing an image from the gallery
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.circle_profile) // Add a placeholder image in your resources
                .error(R.drawable.default_profile)  // Add an error image in your resources
                .centerInside()
                .circleCrop()  // Optional, if you want to crop the image into a circular shape
                .into(profileImageView)
        }
    }

    // Load user data from Firestore
    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val name = document.getString("name")
                        if (!name.isNullOrEmpty()) {
                            nameInput.setText(name)
                        }
                        usernameInput.setText(document.getString("username"))

                        // Load profile image if it exists
                        existingImageUrl = document.getString("profileImageUrl")

                        if (!existingImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(existingImageUrl)
                                .placeholder(R.drawable.circle_profile) // Placeholder image
                                .error(R.drawable.default_profile)  // Error image
                                .centerInside()
                                .circleCrop()
                                .into(profileImageView)
                        } else {
                            profileImageView.setImageResource(R.drawable.circle_profile) // Default image if no profile picture
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        context,
                        "Failed to load data: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    // Set up gender dropdown
    private fun setupGenderSpinner() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_options, // This array will be defined in strings.xml
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            genderSpinner.adapter = adapter
        }
    }

    // Save profile data to Firestore and upload image to Firebase Storage
    private fun saveProfile(name: String, username: String, selectedGender: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Show the loading overlay and ProgressBar
            loadingOverlay.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE

            if (imageUri != null) {
                // If user selected an image, upload it
                uploadImageToStorage(name, username, selectedGender)
            } else {
                // If no image selected, save data without image URL
                saveProfileData(name, username, selectedGender, existingImageUrl)
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    // Upload image to Firebase Storage and get the URL
    private fun uploadImageToStorage(name: String, username: String, selectedGender: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.e("UploadImage", "User ID: $userId")
        if (userId == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageUri == null) {
            Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
            return
        }

        // Compress the image
        val compressedImage = compressImage(imageUri!!)

        // Use the userId in the path
        val storageRef = storage.reference.child("profile_pictures/${userId}_profile.jpg")

        // Upload the compressed image
        storageRef.putBytes(compressedImage)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Get the download URL and save the profile data
                    saveProfileData(name, username, selectedGender, uri.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.e("UploadImage", "Upload failed: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                // Hide the loading overlay and ProgressBar on failure
                loadingOverlay.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
    }

    private fun compressImage(imageUri: Uri): ByteArray {
        // Decode the image to a Bitmap
        val originalBitmap = BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(imageUri))

        // Set the initial quality
        var quality = 100
        var stream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        // Check the size and compress if necessary
        while (stream.toByteArray().size > 500 * 1024 && quality > 0) { // 500 KB
            stream.reset() // Reset the stream
            quality -= 10 // Decrease quality
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        return stream.toByteArray() // Return the compressed image as ByteArray
    }

    // Save profile data to Firestore
    private fun saveProfileData(name: String, username: String, selectedGender: String, imageUrl: String?) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val profileData = hashMapOf(
                "name" to name,
                "username" to username,
                "gender" to selectedGender,
                "profileImageUrl" to imageUrl
            )

            firestore.collection("users").document(userId)
                .set(profileData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Profile saved successfully", Toast.LENGTH_SHORT).show()
                    // Navigate to MainActivity after successful profile save
                    val intent = Intent(requireContext(), MainActivity::class.java)
                    startActivity(intent)
                    requireActivity().finish() // Optional: finish the current activity
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    // Hide the loading overlay and ProgressBar on failure
                    loadingOverlay.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
        }
    }
}