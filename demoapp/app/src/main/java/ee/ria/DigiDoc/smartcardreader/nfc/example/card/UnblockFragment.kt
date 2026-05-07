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

package ee.ria.DigiDoc.smartcardreader.nfc.example.card

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentUnblockBinding
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.HideInput

class UnblockFragment : Fragment() {

    private lateinit var binding: FragmentUnblockBinding
    private lateinit var cancelButton: Button
    private lateinit var nextButton: Button
    private lateinit var pukEditText: EditText
    private lateinit var newPinEditText: EditText
    private lateinit var pinTypeGroup: RadioGroup

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUnblockBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancelButton = binding.buttonCancel
        nextButton = binding.buttonNext
        pukEditText = binding.editTextPUK
        newPinEditText = binding.editTextNewPIN
        pinTypeGroup = binding.radioGroupPinType

        pukEditText.transformationMethod = HideInput()
        newPinEditText.transformationMethod = HideInput()

        cancelButton.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }

        nextButton.setOnClickListener {
            val pinType = if (pinTypeGroup.checkedRadioButtonId == R.id.radio_pin1) {
                "PIN1"
            } else {
                "PIN2"
            }
            val bundle = Bundle()
            bundle.putString("get", "unblock")
            bundle.putByteArray("puk", pukEditText.text.toString().toByteArray())
            bundle.putByteArray("newPin", newPinEditText.text.toString().toByteArray())
            bundle.putString("pinType", pinType)
            findNavController().navigate(R.id.action_unblockFragment_to_cardReaderFragment, bundle)
        }

        handleOnBackPressed()
    }

    private fun handleOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(),
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    findNavController().popBackStack(R.id.homeFragment, false)
                }
            })
    }
}
