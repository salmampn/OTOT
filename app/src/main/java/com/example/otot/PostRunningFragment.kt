package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController

class PostRunningFragment : Fragment() {
        private lateinit var btnContinue : Button
        private lateinit var btnViewAnalysis : Button

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            // Inflate the layout for this fragment
            val view = inflater.inflate(R.layout.fragment_post_running, container, false)
            btnContinue = view.findViewById(R.id.continueButton)
            btnViewAnalysis = view.findViewById(R.id.viewAnalysisButton)

            btnViewAnalysis.setOnClickListener{
                findNavController().navigate(R.id.action_postRunningFragment_to_analysisFragment)
            }

            btnContinue.setOnClickListener{
                findNavController().navigate(R.id.action_postRunningFragment_to_homeFragment)
            }
            return view
        }

}