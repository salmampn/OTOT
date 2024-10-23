package com.example.otot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class InitProfileFragment : Fragment() {

    private lateinit var profileImageView: ImageView
    private lateinit var genderSpinner: Spinner
    private lateinit var continueButton: Button
    private lateinit var nameInput : EditText
    private lateinit var usernameInput: EditText
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private val PICK_IMAGE_REQUEST = 1
    private var imageUri: Uri? = null

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

        profileImageView = view.findViewById(R.id.profile_image)
        genderSpinner = view.findViewById(R.id.gender_spinner)
        continueButton = view.findViewById(R.id.btnContinue)
        nameInput = view.findViewById(R.id.name_input)
        usernameInput = view.findViewById(R.id.username_input)

        setupGenderSpinner()

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
                val intent = Intent(requireContext(), MainActivity::class.java)
                startActivity(intent)
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
            imageUri = data.data!!
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.circle_image_background) // Add a placeholder image in your resources
                .error(R.drawable.prev_image)  // Add an error image in your resources
                .circleCrop()  // Optional, if you want to crop the image into a circular shape
                .into(profileImageView)

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
            if (imageUri != null) {
                // If user selected an image, upload it
                uploadImageToStorage(name, username, selectedGender)
            } else {
                // If no image selected, save data without image URL
                saveProfileData(name, username, selectedGender, null)
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    // Upload image to Firebase Storage and get the URL
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
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}