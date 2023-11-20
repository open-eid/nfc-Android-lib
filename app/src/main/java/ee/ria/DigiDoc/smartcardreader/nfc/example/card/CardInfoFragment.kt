package ee.ria.DigiDoc.smartcardreader.nfc.example.card

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentCardInfoBinding
import ee.ria.DigiDoc.smartcardreader.nfc.example.viewmodel.DataViewModel

class CardInfoFragment : Fragment() {

    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var binding: FragmentCardInfoBinding
    private lateinit var givenNameTextView: TextView
    private lateinit var surnameTextView: TextView
    private lateinit var personalCodeTextView: TextView
    private lateinit var citizenshipTextView: TextView
    private lateinit var dateOfExpiryTextView: TextView
    private lateinit var pin1TextView: TextView
    private lateinit var pin2TextView: TextView

    private lateinit var closeButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCardInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        givenNameTextView = binding.textViewFirstName
        surnameTextView = binding.textViewLastName
        personalCodeTextView = binding.textViewPersonalCode
        citizenshipTextView = binding.textViewCitizenship
        dateOfExpiryTextView = binding.textViewExpirationDate
        pin1TextView = binding.textViewPin1
        pin2TextView = binding.textViewPin2

        closeButton = binding.buttonClose

        closeButton.setOnClickListener {
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        displayCardInfo()

        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack(R.id.homeFragment, false)
            }
        })
    }

    private fun displayCardInfo() {
        givenNameTextView.text = getString(R.string.first_name, dataViewModel.getGivenNames())
        surnameTextView.text = getString(R.string.last_name, dataViewModel.getSurname())
        personalCodeTextView.text =
            getString(R.string.personal_code, dataViewModel.getPersonalCode())
        citizenshipTextView.text = getString(R.string.citizenship, dataViewModel.getCitizenship())
        dateOfExpiryTextView.text =
            getString(R.string.expiration, dataViewModel.getExpiryDate())

        pin1TextView.text = getString(R.string.pin1_counter, dataViewModel.getPin1Counter())
        pin2TextView.text = getString(R.string.pin2_counter, dataViewModel.getPin2Counter())

    }
}