package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

class InitProfileFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // get reference to the string array that we just created
        val gender = resources.getStringArray(R.array.Gender)
        // create an array adapter and pass the required parameter
        // in our case pass the context, drop down layout , and array.
        val arrayAdapter = ArrayAdapter(requireContext(), R.layout.list_item, gender)
        val autocompleteTV = view?.findViewById<AutoCompleteTextView>(R.id.autoCompleteTextView)
        // set adapter to the autocomplete tv to the arrayAdapter
        autocompleteTV?.setAdapter(arrayAdapter)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_init_profile, container, false)
    }
}