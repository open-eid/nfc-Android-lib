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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.TagLostException
import android.os.Bundle
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
import ee.ria.DigiDoc.idcard.CodeType
import ee.ria.DigiDoc.idcard.CodeVerificationException
import ee.ria.DigiDoc.idcard.PaceTunnelException
import ee.ria.DigiDoc.idcard.TokenWithPace
import ee.ria.DigiDoc.smartcardreader.ApduResponseException
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
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.debugLog
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.errorLog
import org.bouncycastle.util.encoders.Base64
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * CardReaderFragment is direct integration point with NFC library
 */
class CardReaderFragment : Fragment() {
    private val logTag = javaClass.simpleName
    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var binding: FragmentCardReaderBinding
    private lateinit var infoTextView: TextView
    private lateinit var communicationTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var resultIcon: ImageView

    private lateinit var broadcastReceiver: BroadcastReceiver

    /**
     * NfcSmartCardReaderManager is used to acquire NFC tag
     */
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

        // Create the manager to be used for serving different requests
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

    /**
     * Authentication workflow for Web-eID over NFC. NB! Serverless mock.
     */
    private fun auth() {
        // Start NFC discovery
        checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader, exc ->
            requireActivity().runOnUiThread {
                progressBar.visibility = View.VISIBLE
                communicationTextView.text = getString(R.string.card_detected)
            }

            if ((nfcReader != null) && (exc == null)) {
                try {
                    // Create card session over NFC
                    val card = TokenWithPace.create(nfcReader)
                    // Establish PACE tunnel with previously captured CAN
                    card.tunnel(dataViewModel.getCan())
                    // Get auth certificate
                    val authCert = card.certificate(CertificateType.AUTHENTICATION)
                    debugLog(logTag, Base64.toBase64String(authCert))
                    val pin1 = arguments?.getByteArray("pin1")

                    // NB! This is mock authentication, we are only interested in the correct
                    // behaviour of ID1 PIN1 API
                    val nonce = Base64.decode(
                        "Tldaa01EZ3hZV0kwWmpkallXUXpaalV3TkdKbVpUZ3lOamhqWVRZMVlqVUsK"
                    )

                    val nonceHash = MessageDigest.getInstance("SHA-512").digest(nonce)
                    debugLog(logTag,
                        String.format("NONCE %s, %s",
                        Hex.toHexString(nonce),
                        Hex.toHexString(nonceHash)))

                    val origin = "https://" + Utils.origin
                    val originHash =
                        MessageDigest.getInstance("SHA-512").digest(origin.toByteArray())

                    debugLog(logTag, String.format("ORIGIN %s, %s", origin, Hex.toHexString(originHash)))

                    val tbsData = originHash + nonceHash
                    val tbsHash = MessageDigest.getInstance("SHA-384").digest(tbsData)

                    // Use PIN1 to sign created challenge-response
                    val signedHash = card.authenticate(pin1, tbsHash)
                    debugLog(logTag, String.format("TBSDATA %s", Hex.toHexString(tbsData)))
                    debugLog(logTag, String.format("TBSHASH %s", Hex.toHexString(tbsHash)))
                    debugLog(logTag, String.format("SIGNEDHASH %s", Hex.toHexString(signedHash)))

                    val inps = ByteArrayInputStream(authCert)
                    val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
                    val cert: X509Certificate = cf.generateCertificate(inps) as X509Certificate

                    setReaderResult(R.drawable.success)
                    requireActivity().runOnUiThread {
                        val bundle = Bundle()
                        bundle.putString("user", cert.subjectDN.toString())
                        findNavController()
                            .navigate(R.id.action_cardReaderFragment_to_authFragment, bundle)
                    }
                } catch (ex: SmartCardReaderException) {
                    setReaderResult(R.drawable.error)
                    requireActivity().runOnUiThread {
                        exceptionToast(ex)
                        findNavController().popBackStack(R.id.pin1Fragment, false)
                    }
                    val message =  ex.message ?: "Error communicating with card"
                    errorLog(logTag, message, ex)
                } finally {
                    nfcSmartCardReaderManager.disableNfcReaderMode()
                }
            }
        })
    }

    /**
     * Digital signature workflow with card over NFC
     */
    private fun getSignature() {
        checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader, exc ->
            requireActivity().runOnUiThread {
                progressBar.visibility = View.VISIBLE
                communicationTextView.text = getString(R.string.card_detected)
            }

            if ((nfcReader != null) && (exc == null)) {
                try {
                    val card = TokenWithPace.create(nfcReader)
                    card.tunnel(dataViewModel.getCan())
                    val signerCert = card.certificate(CertificateType.SIGNING)
                    debugLog(logTag, Base64.toBase64String(signerCert))
                    signature = container.prepareWebSignature(signerCert, Utils.signatureProfile)
                    val dataToSign = signature.dataToSign()
                    val pin2 = arguments?.getByteArray("pin2")
                    val signatureArray = card.calculateSignature(pin2!!, dataToSign!!, true)
                    debugLog(logTag, String.format("%s", Hex.toHexString(signatureArray)))
                    addSignature(signatureArray)
                    setReaderResult(R.drawable.success)
                } catch (ex: SmartCardReaderException) {
                    setReaderResult(R.drawable.error)
                    requireActivity().runOnUiThread {
                        exceptionToast(ex)
                    }
                    val message =  ex.message ?: "Error communicating with card"
                    errorLog(logTag, message, ex)
                } finally {
                    nfcSmartCardReaderManager.disableNfcReaderMode()
                }
                requireActivity().runOnUiThread {
                    findNavController().navigate(R.id.action_cardReaderFragment_to_containerFragment)
                }
            }
        })
    }

    private fun addSignature(signatureArray: ByteArray) {
        signature.setSignatureValue(signatureArray)
        signature.extendSignatureProfile(Utils.signatureProfile)
        container.save()
        signatureIsAdded = true
    }

    /**
     * Reading different counters and datafile from the card
     */
    private fun readCardData() {
        checkNfcStatus(nfcSmartCardReaderManager.startDiscovery(requireActivity()) { nfcReader, exc ->
            requireActivity().runOnUiThread {
                progressBar.visibility = View.VISIBLE
                communicationTextView.text = getString(R.string.card_detected)
            }
            if ((nfcReader != null) && (exc == null)) {
                try {
                    val card = TokenWithPace.create(nfcReader)
                    card.tunnel(dataViewModel.getCan())
                    val cardData = card.personalData()
                    debugLog(logTag, cardData.toString())
                    dataViewModel.setUserValues(cardData)

                    val pin1counter = card.codeRetryCounter(CodeType.PIN1)
                    dataViewModel.setPin1Counter(pin1counter)

                    val pin2counter = card.codeRetryCounter(CodeType.PIN2)
                    dataViewModel.setPin2Counter(pin2counter)

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
                    val message =  ex.message ?: "Error communicating with card"
                    errorLog(logTag, message, ex)
                } finally {
                    nfcSmartCardReaderManager.disableNfcReaderMode()
                }
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
        if (ex is CodeVerificationException) {
            Toast.makeText(requireContext(), ex.message!!, Toast.LENGTH_SHORT).show()
        } else if (ex is PaceTunnelException) {
            Toast.makeText(requireContext(), "Wrong CAN", Toast.LENGTH_SHORT).show()
        } else if (ex is ApduResponseException) {
            Toast.makeText(requireContext(), "Error communicating with card", Toast.LENGTH_SHORT).show()
        }
        else {
            if (ex.cause is TagLostException) {
                Toast.makeText(requireContext(), "Tag was lost", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), ex.message!!, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
