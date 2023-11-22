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