package ee.ria.DigiDoc.smartcardreader.nfc.example.card

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import ee.ria.DigiDoc.idcard.CertificateType
import ee.ria.DigiDoc.idcard.TokenWithPace
import ee.ria.DigiDoc.smartcardreader.SmartCardReaderException
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager
import ee.ria.DigiDoc.smartcardreader.nfc.NfcSmartCardReaderManager.NfcStatus
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentCardReaderBinding
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.container
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.signature
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.signatureIsAdded
import ee.ria.DigiDoc.smartcardreader.nfc.example.viewmodel.DataViewModel
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.Hex
import timber.log.Timber

class CardReaderFragment : Fragment() {

    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var binding: FragmentCardReaderBinding
    private lateinit var infoTextView: TextView
    private lateinit var communicationTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var resultIcon: ImageView

    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var nfcSmartCardReaderManager: NfcSmartCardReaderManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCardReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        infoTextView = binding.textViewInfo
        communicationTextView = binding.textViewCommunication
        progressBar = binding.progressbar
        resultIcon = binding.imageViewResult

        progressBar.visibility = View.INVISIBLE
        resultIcon.visibility = View.GONE

        nfcSmartCardReaderManager = NfcSmartCardReaderManager()

        val get = arguments?.getString("get")

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                when (get) {
                    "cardInfo" -> readCardData()
                    "signature" -> getSignature()
                    "auth" -> auth()
                }
            }
        }
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        requireActivity().registerReceiver(broadcastReceiver, filter)

        when (get) {
            "cardInfo" -> readCardData()
            "signature" -> getSignature()
            "auth" -> auth()
        }

        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                findNavController().popBackStack()
            }
        })
    }

    @Suppress("EmptyMethod")
    private fun auth() {}

    private fun getSignature() {
        checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader ->
            requireActivity().runOnUiThread {
                progressBar.visibility = View.VISIBLE
                communicationTextView.text = getString(R.string.card_detected)
            }

            try {
                val card = TokenWithPace.create(nfcReader)
                card.tunnel(dataViewModel.getCan())
                val signerCert = card.certificate(CertificateType.SIGNING)
                Timber.log(Log.DEBUG, Base64.toBase64String(signerCert))
                signature = container.prepareWebSignature(signerCert, Utils.signatureProfile)
                val dataToSign = signature.dataToSign()
                val pin2 = arguments?.getByteArray("pin2")
                val signatureArray = card.calculateSignature(pin2!!, dataToSign!!, true)
                Timber.log(Log.DEBUG, "PIN1: %s", Hex.toHexString(signatureArray))
                addSignature(signatureArray)
                setReaderResult(R.drawable.success)
            } catch (ex: SmartCardReaderException) {
                setReaderResult(R.drawable.error)
                requireActivity().runOnUiThread {
                    exceptionToast(ex)
                }
                Timber.log(Log.ERROR, ex, ex.message)
            } catch (ex: RuntimeException) {
                setReaderResult(R.drawable.error)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), ex.message, Toast.LENGTH_SHORT).show()
                }
                Timber.log(Log.ERROR, ex, ex.message)
            }
            requireActivity().runOnUiThread {
                findNavController().navigate(R.id.action_cardReaderFragment_to_containerFragment)
            }
        })
    }

    private fun addSignature(signatureArray: ByteArray) {
        signature.setSignatureValue(signatureArray)
        signature.extendSignatureProfile(Utils.signatureProfile)
        container.save()
        signatureIsAdded = true
    }

    private fun readCardData() {
        checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader ->
            requireActivity().runOnUiThread {
                progressBar.visibility = View.VISIBLE
                communicationTextView.text = getString(R.string.card_detected)
            }
            try {
                val card = TokenWithPace.create(nfcReader)
                card.tunnel(dataViewModel.getCan())
                val cardData = card.personalData()
                Timber.log(Log.DEBUG, cardData.toString())
                dataViewModel.setUserValues(cardData)
                setReaderResult(R.drawable.success)
                requireActivity().runOnUiThread {
                    findNavController().navigate(R.id.action_cardReaderFragment_to_cardInfoFragment)
                }
            } catch (ex: SmartCardReaderException) {
                setReaderResult(R.drawable.error)
                requireActivity().runOnUiThread {
                    findNavController().popBackStack(R.id.canFragment, false)
                    exceptionToast(ex)
                }
                Timber.log(Log.ERROR, ex, ex.message)
            }
        })
    }

    private fun checkNfcStatus(status: NfcStatus) {
        when (status) {
            NfcStatus.NFC_NOT_SUPPORTED -> communicationTextView.text = getString(R.string.nfc_not_supported)
            NfcStatus.NFC_NOT_ACTIVE -> communicationTextView.text = getString(R.string.nfc_not_turned_on)
            NfcStatus.NFC_ACTIVE -> communicationTextView.text = getString(R.string.card_detect_info)
        }
    }

    private fun setReaderResult(drawable: Int) {
        requireActivity().runOnUiThread {
            progressBar.visibility = View.GONE
            resultIcon.visibility = View.VISIBLE
            resultIcon.setImageResource(drawable)
        }
        Thread.sleep(1500)
    }

    private fun exceptionToast(ex: SmartCardReaderException) {
        if (ex.message!!.contains("TagLostException")) {
            Toast.makeText(requireContext(), "Tag was lost", Toast.LENGTH_SHORT).show()
        } else if (ex.message!!.contains("verification failed")) {
            Toast.makeText(requireContext(), "PIN verification failed", Toast.LENGTH_SHORT).show()
        } else if (ex.message!!.contains("ApduResponseException")) {
            Toast.makeText(requireContext(), "Wrong CAN", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), ex.message!!, Toast.LENGTH_SHORT).show()
        }
    }
}