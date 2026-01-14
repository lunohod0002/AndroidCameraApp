package com.example.vkrandroid


import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.MediaController
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.vkrandroid.databinding.FragmentViewerBinding
import androidx.core.net.toUri

class ViewerFragment : Fragment() {
    private var _binding: FragmentViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            findNavController().popBackStack(R.id.galleryFragment,false)
        }
        val path = arguments?.getString("filePath") ?: return
        val isVideo = arguments?.getBoolean("isVideo") ?: false
        val uri = path.toUri()

        if (isVideo) {
            binding.videoView.visibility = View.VISIBLE
            binding.videoView.setVideoURI(uri)
            val mediaController = MediaController(context)
            mediaController.setAnchorView(binding.videoView)
            binding.videoView.setMediaController(mediaController)
            binding.videoView.start()
        } else {
            binding.imageView.visibility = View.VISIBLE
            Glide.with(this).load(uri).into(binding.imageView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}