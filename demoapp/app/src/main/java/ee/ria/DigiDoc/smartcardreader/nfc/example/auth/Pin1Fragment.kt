/*
 * Copyright 2017 - 2025 Riigi Infosüsteemi Amet
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package ee.ria.DigiDoc.smartcardreader.nfc.example.auth

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentPin1Binding
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.HideInput

class Pin1Fragment : Fragment() {

    private lateinit var binding: FragmentPin1Binding
    private lateinit var cancelButton: Button
    private lateinit var nextButton: Button
    private lateinit var addPin1EditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPin1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancelButton = binding.buttonCancel
        nextButton = binding.buttonNext
        addPin1EditText = binding.editTextPIN1

        addPin1EditText.transformationMethod = HideInput()

        cancelButton.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        nextButton.setOnClickListener {
            val bundle = Bundle()
            bundle.putString("get", "auth")
            bundle.putByteArray("pin1", addPin1EditText.text.toString().toByteArray())
            findNavController().navigate(R.id.action_pin1Fragment_to_cardReaderFragment, bundle)
        }

        handleOnBackPressed()
    }

    private fun handleOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.homeFragment, false)
            }
        })
    }
}