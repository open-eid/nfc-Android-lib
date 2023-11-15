package ee.ria.DigiDoc.smartcardreader.nfc.example

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var authButton: Button
    private lateinit var signatureButton: Button
    private lateinit var cardButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authButton = binding.buttonAuth
        signatureButton = binding.buttonSignature
        cardButton = binding.buttonCardView

        signatureButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_containerFragment)
        }

        cardButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_canFragment)
        }

        authButton.setOnClickListener {
            Toast.makeText(requireContext(), "Not implemented!", Toast.LENGTH_SHORT).show()
        }
        handleOnBackPressed()
    }

    private fun handleOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requireActivity().finish()
            }
        })
    }
}