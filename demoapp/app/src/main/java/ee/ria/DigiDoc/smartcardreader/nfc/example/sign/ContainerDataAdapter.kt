package ee.ria.DigiDoc.smartcardreader.nfc.example.sign

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.FileData
import ee.ria.DigiDoc.smartcardreader.nfc.example.R
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils
import ee.ria.DigiDoc.smartcardreader.nfc.example.util.Utils.signatureIsAdded

class ContainerDataAdapter(
    private val files: MutableList<FileData>,
    private val context: Context,
    private val containerLayout: LinearLayout,
    private val signerCertButton: Button
) :
    RecyclerView.Adapter<ContainerDataAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileNameTextView: TextView
        val deleteButton: ImageButton

        init {
            fileNameTextView = view.findViewById(R.id.fileName_textView)
            deleteButton = view.findViewById(R.id.deleteFile_button)
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.file, viewGroup, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.fileNameTextView.text = files[position].getFileName()
        if (signatureIsAdded) {
            viewHolder.deleteButton.visibility = View.GONE
        }
        viewHolder.deleteButton.setOnClickListener {
            files.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, files.size)
            if (Utils.getContainerFilesList().isEmpty()) {
                containerLayout.visibility = View.INVISIBLE
                signerCertButton.visibility = View.GONE
            }
        }

        viewHolder.fileNameTextView.setOnClickListener {
            val viewFileIntent = Intent()
            viewFileIntent.action = Intent.ACTION_VIEW
            viewFileIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            viewFileIntent.data = files[position].getFileIntent().data
            context.startActivity(viewFileIntent)
        }
    }

    override fun getItemCount() = files.size
}