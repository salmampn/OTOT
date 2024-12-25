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

/**
 * Fragment untuk mengedit profil pengguna.
 */
class EditprofileFragment : Fragment() {

    // Deklarasi variabel untuk elemen UI
    private lateinit var etName: EditText
    private lateinit var etUsername: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var btnSave: TextView
    private lateinit var profileImage: ImageView

    // Inisialisasi Firebase Firestore, Auth, dan Storage
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // Variabel untuk ProgressBar dan overlay loading
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View

    // Konstanta untuk permintaan pemilihan gambar
    private val PICK_IMAGE_REQUEST = 1

    // Variabel untuk menyimpan URI gambar yang dipilih dan URL gambar yang ada
    private var imageUri: Uri? = null
    private var existingImageUrl: String? = null

    /**
     * Memanggil saat fragment dibuat.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_editprofile, container, false)

        // Inisialisasi elemen UI
        etName = view.findViewById(R.id.et_name)
        etUsername = view.findViewById(R.id.et_username)
        spinnerGender = view.findViewById(R.id.spinner_gender)
        btnSave = view.findViewById(R.id.btn_save)
        profileImage = view.findViewById(R.id.profile_image)

        // Inisialisasi ProgressBar dan overlay loading
        progressBar = view.findViewById(R.id.progressBar)
        loadingOverlay = view.findViewById(R.id.loading_overlay)

        // Set listener untuk gambar profil
        profileImage.setOnClickListener {
            openGallery()
        }

        // Memuat data pengguna dari Firestore
        loadUserData()

        // Set listener untuk tombol simpan
        btnSave.setOnClickListener {
            saveProfile(
                etName.text.toString(),
                etUsername.text.toString(),
                spinnerGender.selectedItem.toString()
            )
        }
        return view
    }

    /**
     * Memuat data pengguna dari Firestore.
     */
    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        // Mengisi EditText dengan data yang ada
                        etName.setText(document.getString("name"))
                        etUsername.setText(document.getString("username"))

                        // Mengatur pemilihan spinner berdasarkan gender
                        val gender = document.getString("gender")
                        val genderPosition = resources.getStringArray(R.array.gender_options).indexOf(gender)
                        spinnerGender.setSelection(genderPosition)

                        // Memuat gambar profil jika ada
                        existingImageUrl = document.getString("profileImageUrl")

                        Log.e("ExistingImageUrl: $existingImageUrl", "user: ${user.uid}")
                        if (!existingImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(existingImageUrl)
                                .placeholder(R.drawable.circle_profile) // Gambar placeholder
                                .error(R.drawable.default_profile)  // Gambar error
                                .centerInside()
                                .circleCrop()
                                .into(profileImage)
                        } else {
                            profileImage.setImageResource(R.drawable.circle_profile) // Gambar default jika tidak ada gambar profil
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

    /**
     * Membuka galeri untuk memilih gambar.
     */
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    /**
     * Menangani hasil pemilihan gambar dari galeri.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data!!
            Glide.with(this)
                .load(imageUri)
                .placeholder(R.drawable.circle_profile) // Gambar placeholder
                .error(R.drawable.default_profile)  // Gambar error
                .centerInside()
                .circleCrop()  // Memotong gambar menjadi bentuk lingkaran
                .into(profileImage)
        }
    }

    /**
     * Menyimpan profil pengguna.
     */
    private fun saveProfile(name: String, username: String, selectedGender: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            if (imageUri != null) {
                // Jika pengguna memilih gambar, unggah gambar tersebut
                loadingOverlay.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                uploadImageToStorage(name, username, selectedGender)
            } else {
                // Jika tidak ada gambar baru yang dipilih, simpan data dengan URL gambar yang ada
                saveProfileData(name, username, selectedGender, existingImageUrl)
            }
        } else {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Mengunggah gambar ke Firebase Storage.
     */
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

        // Mengompres gambar
        val compressedImage = compressImage(imageUri!!)

        // Menggunakan userId dalam path
        val storageRef = storage.reference.child("profile_pictures/${userId}_profile.jpg")

        storageRef.putFile(imageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Mendapatkan URL unduhan dan menyimpan data profil
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

    /**
     * Mengompres gambar untuk mengurangi ukuran file.
     */
    private fun compressImage(uri: Uri): ByteArray {
        // Mendekode gambar menjadi Bitmap
        val originalBitmap = BitmapFactory.decodeStream(imageUri?.let {
            requireContext().contentResolver.openInputStream(
                it
            )
        })

        // Mengatur kualitas awal
        var quality = 100
        var stream = ByteArrayOutputStream()
        originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

        // Memeriksa ukuran dan mengompres jika perlu
        while (stream.toByteArray().size > 500 * 1024 && quality > 50) { // 500 KB
            stream.reset() // Mengatur ulang stream
            quality -= 50 // Mengurangi kualitas
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }

        return stream.toByteArray() // Mengembalikan gambar yang dikompres sebagai ByteArray
    }

    /**
     * Menyimpan data profil pengguna ke Firestore.
     */
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