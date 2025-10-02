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

package ee.ria.DigiDoc.smartcardreader.nfc.example.sign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.databinding.FragmentContainerBinding
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.FileData
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.addFileContent
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.checkFreeSpace
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.container
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.filesToCache
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.getContainerFilesList
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.signatureIsAdded
import ee.ria.DigiDoc.smartcardreader.nfc.example.viewmodel.DataViewModel
import ee.ria.libdigidocpp.Container
import java.io.File
import java.util.Locale

class ContainerFragment : Fragment() {

    private val dataViewModel: DataViewModel by activityViewModels()
    private lateinit var binding: FragmentContainerBinding
    private lateinit var backButton: Button
    private lateinit var addFileButton: Button
    private lateinit var addSignatureButton: Button
    private lateinit var shareButton: Button
    private lateinit var containerNameEditText: EditText
    private lateinit var containerFilesRecyclerView: RecyclerView
    private lateinit var containerLayout: LinearLayout
    private lateinit var containerDataAdapter: ContainerDataAdapter

    private var containerFiles: MutableList<FileData> = mutableListOf()
    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult

            data.action = Intent.ACTION_OPEN_DOCUMENT
            val uri = data.data ?: return@registerForActivityResult
            val nameSize = Utils.getFileNameAndSize(uri, requireContext())
            val fileName = nameSize.first

            if (containerFiles.any { it.getFileName() == fileName }) return@registerForActivityResult

            addFileContent(fileName, data, requireActivity().contentResolver)

            if (containerNameEditText.text.toString() == "") {
                val name = String.format(
                    Locale.US, "%s.%s",
                    Utils.removeFileExtension(fileName), Utils.SIGNATURE_CONTAINER_EXTENSION
                )
                dataViewModel.setContainerName(name)
            }
            containerDataAdapter.notifyItemInserted(containerFiles.size - 1)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backButton = binding.buttonBack
        addFileButton = binding.buttonAddFile
        addSignatureButton = binding.buttonGetSignCert
        shareButton = binding.buttonShareContainer
        containerNameEditText = binding.editTextContainerName
        containerFilesRecyclerView = binding.recyclerViewContainerFiles
        containerLayout = binding.linearLayoutContainer
        containerDataAdapter = ContainerDataAdapter(
            getContainerFilesList(),
            requireContext(),
            containerLayout,
            addSignatureButton
        )

        backButton.setOnClickListener {
            clearData()
            findNavController().popBackStack(R.id.homeFragment, false)
        }
        addFileButton.setOnClickListener { addFile() }
        addSignatureButton.setOnClickListener {
            filesToCache(requireContext().cacheDir.absolutePath)
            createContainer()
            dataViewModel.setContainerName(containerNameEditText.text.toString())
            val bundle = Bundle()
            bundle.putString("get", "signature")
            findNavController().navigate(R.id.action_containerFragment_to_canFragment, bundle)
        }
        shareButton.setOnClickListener {
            shareContainer()
        }
        setRecyclerView()
        handleOnBackPressed()
    }

    private fun handleOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                clearData()
                findNavController().popBackStack(R.id.homeFragment, false)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (dataViewModel.getCan().isNotEmpty()) containerNameEditText.isEnabled = false
        containerFiles = getContainerFilesList()
        containerNameEditText.text =
            Editable.Factory.getInstance().newEditable(dataViewModel.getContainerName())

        if (getContainerFilesList().isEmpty()) {
            addSignatureButton.visibility = View.GONE
            containerLayout.visibility = View.INVISIBLE
        } else {
            addSignatureButton.visibility = View.VISIBLE
            containerLayout.visibility = View.VISIBLE
        }
        if (signatureIsAdded) {
            addSignatureButton.visibility = View.GONE
            addFileButton.visibility = View.GONE
        } else {
            shareButton.visibility = View.GONE
        }
        containerDataAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearData()
    }

    private fun clearData() {
        Utils.clearContainerFilesList()
        Utils.deleteCachedDataFiles(requireActivity())
        signatureIsAdded = false
        dataViewModel.clearCan()
    }

    private fun addFile() {
        val chooseFileIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
        pickFileLauncher.launch(Intent.createChooser(chooseFileIntent, "Choose a file"))
    }

    private fun createContainer() {
        try {
            // Create container file path
            val containerFile: File = Utils.signatureContainerFile(
                containerNameEditText.text.toString(),
                requireContext().filesDir
            )
            // Create container file with existing path
            container = Container.create(containerFile.absolutePath)
            // Add all files to container
            val dataFiles = Utils.getFilesFromCache(requireContext().cacheDir.absolutePath)
            for (file in dataFiles) {
                val index = dataFiles.indexOf(file)
                val mime = Utils.getMimeType(index)
                container.addDataFile(file.absolutePath, mime)
            }

            // Save container
            if (checkFreeSpace(container, requireActivity())) {
                container.save()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Unable to save container, not enough free space.",
                    Toast.LENGTH_SHORT
                ).show()
                throw Exception("Not enough free space.")
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun setRecyclerView() {
        val linearLayoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        containerFilesRecyclerView.layoutManager = linearLayoutManager
        containerFilesRecyclerView.adapter = containerDataAdapter
    }

    private fun shareContainer() {
        val containerFile = File(
            requireContext().filesDir.toString() +
                    File.separator + "containers" + File.separator + containerNameEditText.text.toString()
        )
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "ee.ria.DigiDoc.smartcardreader.nfc.example.provider",
            containerFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(shareIntent)
    }

}
