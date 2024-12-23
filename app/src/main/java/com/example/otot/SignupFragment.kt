package com.example.otot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class SignupFragment : Fragment() {

    private lateinit var btnSignup: TextView
    private lateinit var btnLogin: TextView
    private lateinit var btnSignupGoogle : LinearLayout
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view =  inflater.inflate(R.layout.fragment_signup, container, false)

        // Initialize buttons after inflating the view
        btnSignup = view.findViewById(R.id.btnSignup)
        btnLogin = view.findViewById(R.id.login_text)
        btnSignupGoogle = view.findViewById(R.id.googleSignup)

        // Email and password input fields
        emailInput = view.findViewById(R.id.email_input)
        passwordInput = view.findViewById(R.id.password_input)

        // Set up navigation for signup button
        btnSignup.setOnClickListener {
            // Get email and password from input fields
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()

            // Validate input fields
            if (email.isEmpty()) {
                emailInput.error = "Email is required"
                emailInput.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                passwordInput.error = "Password is required"
                passwordInput.requestFocus()
                return@setOnClickListener
            }

            // Validate password requirements
            if (!isPasswordValid(password)) {
                passwordInput.error = "Password must be 8-12 characters long, contain at least one digit and one uppercase letter."
                passwordInput.requestFocus()
                return@setOnClickListener
            }

            // Create a new user with email and password
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity()) { task ->
                    if (task.isSuccessful) {
                        // Sign up success, update UI with the signed-in user's information
                        val user = firebaseAuth.currentUser
                        Toast.makeText(requireContext(), "Sign up success", Toast.LENGTH_SHORT).show()
                        // Navigate to InitProfileFragment
                        viewLifecycleOwner.lifecycleScope.launch {
                            findNavController().navigate(R.id.action_signupFragment_to_initProfileFragment)
                        }
                    } else {
                        // If sign up fails, display a message to the user.
                        Toast.makeText(requireContext(), "Sign up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Set up navigation for login button
        btnLogin.setOnClickListener {
            findNavController().navigate(R.id.action_signupFragment_to_loginFragment)
        }

        btnSignupGoogle.setOnClickListener {
            signInWithGoogle()
        }
        return view
    }

    // Function to validate password
    private fun isPasswordValid(password: String): Boolean {
        // Check password length
        if (password.length < 8 || password.length > 12) {
            return false
        }

        // Check for at least one digit
        if (!password.any { it.isDigit() }) {
            return false
        }

        // Check for at least one uppercase letter
        if (!password.any { it.isUpperCase() }) {
            return false
        }
        return true
    }

    private fun signInWithGoogle() {
        // Sign out of GoogleSignInClient to clear the cached account
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account!!)
        } catch (e: ApiException) {
            // Google Sign In failed, update UI appropriately
            Toast.makeText(requireContext(), "Authentication failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(requireActivity()) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user = firebaseAuth.currentUser
                    // Check if the user already exists in Firestore
                    checkIfUserExists(user?.uid, acct.displayName, acct.photoUrl.toString())
                } else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(requireContext(), "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkIfUserExists(userId: String?, displayName: String?, photoUrl: String?) {
        if (userId != null) {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // User already exists, navigate to InitProfileFragment
                        Toast.makeText(requireContext(), "Account already exists. Signing you in.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(requireContext(), MainActivity::class.java)
                        startActivity(intent)
                        requireActivity().finish()
                    } else {
                        // User does not exist, save user information to Firestore
                        saveUserToFirestore(userId, displayName, photoUrl)
                        // Navigate to InitProfileFragment
                        findNavController().navigate(R.id.action_signupFragment_to_initProfileFragment)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error checking user existence: ${e.message}")
                    Toast.makeText(requireContext(), "Error checking user existence: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveUserToFirestore(userId: String?, displayName: String?, photoUrl: String?) {
        if (userId != null) {
            val userMap = hashMapOf(
                "name" to displayName,
                "profileImageUrl" to photoUrl
            )

            // Get a reference to Firestore
            val firestore = FirebaseFirestore.getInstance()

            // Save user data to Firestore under the "users" collection
            firestore.collection("users").document(userId)
                .set(userMap)
                .addOnSuccessListener {
                    Log.d("Firestore", "User data saved successfully.")
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error saving user data: ${e.message}")
                }
        }
    }

    companion object {
        private const val RC_SIGN_IN = 9001
    }
}