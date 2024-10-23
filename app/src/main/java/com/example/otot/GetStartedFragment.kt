package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController

class GetStartedFragment : Fragment() {

    private lateinit var btnLogin: Button
    private lateinit var btnSignUp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_get_started, container, false)

        // Initialize buttons after inflating the view
        btnLogin = view.findViewById(R.id.login)
        btnSignUp = view.findViewById(R.id.signup)

        // Set up navigation for login button
        btnLogin.setOnClickListener {
            findNavController().navigate(R.id.action_getStartedFragment_to_loginFragment)
        }

        // Set up navigation for signup button
        btnSignUp.setOnClickListener {
            findNavController().navigate(R.id.action_getStartedFragment_to_signupFragment)
        }

        return view
    }
}