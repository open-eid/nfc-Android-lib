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
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentCanBinding
import ee.ria.DigiDoc.smartcardreader.nfc.example.viewmodel.DataViewModel

class CanFragment : Fragment() {

    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var binding: FragmentCanBinding
    private lateinit var cancelButton: Button
    private lateinit var nextButton: Button
    private lateinit var canFieldEditText: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cancelButton = binding.buttonCancel
        nextButton = binding.buttonNext
        canFieldEditText = binding.editTextCAN

        val get = arguments?.getString("get")

        cancelButton.setOnClickListener {
            if (get.equals("cert")) {
                findNavController().popBackStack(R.id.containerFragment, false)
            } else if (get.equals("auth")) {
                findNavController().popBackStack(R.id.homeFragment,false)
            } else {
                findNavController().popBackStack()
            }
        }
        nextButton.setOnClickListener {
            dataViewModel.setCan(canFieldEditText.text.toString())
            val bundle = Bundle()
            if (get.equals("signature")) {
                findNavController().navigate(R.id.action_canFragment_to_pin2Fragment)
            } else if (get.equals("auth")) {
                bundle.putString("get", "auth")
                findNavController().navigate(R.id.action_canFragment_to_pin1Fragment, bundle)
            } else {
                bundle.putString("get", "cardInfo")
                findNavController().navigate(R.id.action_canFragment_to_cardReaderFragment, bundle)
            }
        }
        handleOnBackPressed()
    }

    private fun handleOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dataViewModel.clearCan()
                findNavController().popBackStack()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        canFieldEditText.text = Editable.Factory.getInstance().newEditable(dataViewModel.getCan())
    }
}