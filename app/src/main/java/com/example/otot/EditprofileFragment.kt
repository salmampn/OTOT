package com.example.otot

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.util.*

class EditprofileFragment : Fragment() {

    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var btnSave: TextView
    private lateinit var profileImage: ImageView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View

    private val PICK_IMAGE_REQUEST = 1

    private var imageUri: Uri? = null // To store the selected image URI
    private var existingImageUrl: String? = null // To store the existing image URL from the database

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editprofile, container, false)

        // Initialize views
        etName = view.findViewById(R.id.et_name)
        etUsername = view.findViewById(R.id.et_username)
        spinnerGender = view.findViewById(R.id.spinner_gender)
        btnSave = view.findViewById(R.id.btn_save)
        profileImage = view.findViewById(R.id.profile_image)

        // Initialize the ProgressBar and loading overlay
        progressBar = view.findViewById(R.id.progressBar)
        loadingOverlay = view.findViewById(R.id.loading_overlay)

        profileImage.setOnClickListener{
            openGallery()
        }

        // Load data from Firestore
        loadUserData()

        // Set save button click listener
        btnSave.setOnClickListener {
            saveProfile(
                etName.text.toString(),
                etUsername.text.toString(),
                spinnerGender.selectedItem.toString()
            )
        }
        return view
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        etName.setText(document.getString("name"))
                        etUsername.setText(document.getString("username"))

                        // Set spinner selection based on gender
                        val gender = document.getString("gender")
                        val genderPosition = resources.getStringArray(R.array.gender_options).indexOf(gender)
                        spinnerGender.setSelection(genderPosition)

                        // Load profile image if it exists
                        existingImageUrl = document.getString("profileImageUrl")

                        Log.e("ExistingImageUrl: $existingImageUrl", "user: ${user.uid}")
                        if (!existingImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(existingImageUrl)
                                .placeholder(R.drawable.circle_profile) // Placeholder image
                                .error(R.drawable.default_profile)  // Error image
                                .centerInside()
                                .circleCrop()
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.circle_profile) // Default image if no profile picture
                        }
                    } else {
                        Toast.makeText(context, "No data found!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Failed to load data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data!!
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.circle_profile) // Add a placeholder image in your resources
                .error(R.drawable.default_profile)  // Add an error image in your resources
                .centerInside()
                .circleCrop()  // Optional, if you want to crop the image into a circular shape
                .into(profileImage)
        }
    }

    private fun saveProfile(name: String, username: String, selectedGender: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (imageUri != null) {
                // If user selected an image, upload it
                loadingOverlay.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                uploadImageToStorage(name, username, selectedGender)
            } else {
                // If no new image selected, save data with existing image URL
                saveProfileData(name, username, selectedGender, existingImageUrl)
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

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

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Get the download URL and save the profile data
                    saveProfileData(name, username, selectedGender, uri.toString())
                }
            }
            .addOnFailureListener { exception ->
                Log.e("UploadImage", "Upload failed: ${exception.message}")
                Toast.makeText(requireContext(), "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                loadingOverlay.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
    }

    private fun compressImage(uri: Uri): ByteArray {
        // Decode the image to a Bitmap
        val originalBitmap = BitmapFactory.decodeStream(imageUri?.let {
            requireContext().contentResolver.openInputStream(
                it
            )
        })

        // Set the initial quality
        var quality = 100
        var stream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        // Check the size and compress if necessary
        while (stream.toByteArray().size > 500 * 1024 && quality > 50) { // 300 KB
            stream.reset() // Reset the stream
            quality -= 50 // Decrease quality
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        return stream.toByteArray() // Return the compressed image as ByteArray
    }

    private fun saveProfileData(name: String, username: String, selectedGender: String, imageUrl: String?) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val profileData = hashMapOf(
                "name" to name,
                "username" to username,
                "gender" to selectedGender,
                "profileImageUrl" to imageUrl
            )

            db.collection("users").document(userId)
                .set(profileData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Profile saved successfully", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_editprofileFragment_to_profileFragment)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    loadingOverlay.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
        }
    }
}
