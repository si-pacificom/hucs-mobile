package org.linphone.ui.file_viewer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.annotation.UiThread
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.FileViewerActivityBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.file_viewer.adapter.PdfPagesListAdapter
import org.linphone.ui.file_viewer.viewmodel.FileViewModel
import org.linphone.utils.FileUtils

@UiThread
class FileViewerActivity : GenericActivity() {
    companion object {
        private const val TAG = "[File Viewer Activity]"

        private const val EXPORT_FILE_AS_DOCUMENT = 10
    }

    private lateinit var binding: FileViewerActivityBinding

    private lateinit var viewModel: FileViewModel

    private lateinit var adapter: PdfPagesListAdapter

    private val pageChangedListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            viewModel.pdfCurrentPage.value = (position + 1).toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.navigationBarColor = getColor(R.color.gray_900)

        binding = DataBindingUtil.setContentView(this, R.layout.file_viewer_activity)
        binding.lifecycleOwner = this
        setUpToastsArea(binding.toastsArea)

        viewModel = ViewModelProvider(this)[FileViewModel::class.java]
        binding.viewModel = viewModel

        val args = intent.extras
        if (args == null) {
            finish()
            return
        }

        val path = args.getString("path")
        if (path.isNullOrEmpty()) {
            finish()
            return
        }

        val timestamp = args.getLong("timestamp", -1)
        val preLoadedContent = args.getString("content")
        Log.i(
            "$TAG Path argument is [$path], pre loaded text content is ${if (preLoadedContent.isNullOrEmpty()) "not available" else "available, using it"}"
        )
        viewModel.loadFile(path, timestamp, preLoadedContent)

        binding.setBackClickListener {
            finish()
        }

        viewModel.fileReadyEvent.observe(this) {
            it.consume { done ->
                if (!done) {
                    finish()
                    Log.e("$TAG Failed to open file, going back")
                }
            }
        }

        binding.setShareClickListener {
            shareFile()
        }

        viewModel.pdfRendererReadyEvent.observe(this) {
            it.consume {
                Log.i("$TAG PDF renderer is ready, attaching adapter to ViewPager")
                if (viewModel.screenWidth == 0 || viewModel.screenHeight == 0) {
                    updateScreenSize()
                }

                adapter = PdfPagesListAdapter(viewModel)
                binding.pdfViewPager.adapter = adapter
            }
        }

        viewModel.exportPlainTextFileEvent.observe(this) {
            it.consume { name ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
            }
        }

        viewModel.exportPdfEvent.observe(this) {
            it.consume { name ->
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_TITLE, name)
                }
                startActivityForResult(intent, EXPORT_FILE_AS_DOCUMENT)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        updateScreenSize()
        binding.pdfViewPager.registerOnPageChangeCallback(pageChangedListener)
    }

    override fun onPause() {
        binding.pdfViewPager.unregisterOnPageChangeCallback(pageChangedListener)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == EXPORT_FILE_AS_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.data?.also { documentUri ->
                Log.i("$TAG Exported file should be stored in URI [$documentUri]")
                viewModel.copyFileToUri(documentUri)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateScreenSize() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        viewModel.screenHeight = displayMetrics.heightPixels
        viewModel.screenWidth = displayMetrics.widthPixels
        Log.i(
            "$TAG Setting screen size ${viewModel.screenWidth}/${viewModel.screenHeight} for PDF renderer"
        )
    }

    private fun shareFile() {
        lifecycleScope.launch {
            val filePath = FileUtils.getProperFilePath(viewModel.getFilePath())
            val copy = FileUtils.getFilePath(
                baseContext,
                Uri.parse(filePath),
                overrideExisting = true,
                copyToCache = true
            )
            if (!copy.isNullOrEmpty()) {
                val publicUri = FileProvider.getUriForFile(
                    baseContext,
                    getString(R.string.file_provider),
                    File(copy)
                )
                Log.i("$TAG Public URI for file is [$publicUri], starting intent chooser")

                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, publicUri)
                    putExtra(Intent.EXTRA_SUBJECT, viewModel.fileName.value.orEmpty())
                    type = viewModel.mimeType.value.orEmpty()
                }

                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            } else {
                Log.e("$TAG Failed to copy file [$filePath] to share!")
            }
        }
    }
}
