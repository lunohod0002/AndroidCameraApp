package com.example.vkrandroid

import android.app.AlertDialog
import android.content.ContentUris
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.vkrandroid.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private var adapter: GalleryAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().popBackStack(R.id.cameraFragment,false)
        }
        binding.recyclerView.layoutManager = GridLayoutManager(context, 3)
        loadMedia()
    }

    private fun loadMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaFile>()
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DATE_ADDED)
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
            val selectionArgs = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            val queryUri = MediaStore.Files.getContentUri("external")

            requireContext().contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val type = cursor.getInt(typeColumn)
                    val date = cursor.getLong(dateColumn)
                    val contentUri = ContentUris.withAppendedId(queryUri, id)
                    mediaList.add(
                        MediaFile(contentUri, type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, date)
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (mediaList.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                } else {
                    binding.emptyView.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE

                    adapter = GalleryAdapter(
                        mediaList,
                        onClick = { file ->
                            val bundle = Bundle().apply {
                                putString("filePath", file.uri.toString())
                                putBoolean("isVideo", file.isVideo)
                            }
                            findNavController().navigate(R.id.action_gallery_to_viewer, bundle)
                        },
                        onDelete = { file ->
                            deleteFile(file)
                        }
                    )
                    binding.recyclerView.adapter = adapter
                }
            }
        }
    }

    private fun deleteFile(file: MediaFile) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rowsDeleted = requireContext().contentResolver.delete(file.uri, null, null)
                withContext(Dispatchers.Main) {
                    if (rowsDeleted > 0) {
                        adapter?.removeItem(file)
                        Toast.makeText(context, "Файл удален", Toast.LENGTH_SHORT).show()

                        if (adapter?.itemCount == 0) {
                            binding.emptyView.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(context, "Не удалось удалить файл", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Ошибка при удалении: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}