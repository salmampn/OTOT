package com.example.otot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class EditprofileFragment : Fragment() {

    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var btnSave: Button
    private lateinit var profileImage: ImageView

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

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
        val storageRef = storage.reference.child("profile_pictures/${UUID.randomUUID()}.jpg")

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Get the download URL and save the profile data
                    saveProfileData(name, username, selectedGender, uri.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
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
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
