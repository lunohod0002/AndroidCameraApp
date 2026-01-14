package com.example.vkrandroid

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vkrandroid.databinding.FragmentCameraBinding
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var camera: Camera? = null
    private var isVideoMode = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { ContextCompat.checkSelfPermission(requireContext(), it.key) == PackageManager.PERMISSION_GRANTED }) {
            startCamera()
        } else {
            Toast.makeText(context, "Permissions not granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= 28) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupListeners()
        checkPermissionsAndStart()
    }

    private fun setupListeners() {
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isVideoMode = (checkedId == R.id.btnVideo)
                startCamera()
            }
           }

        binding.captureBtn.setOnClickListener {
            if (isVideoMode) captureVideo() else takePhoto()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                animateFlash()
            }
        }

        binding.switchCameraBtn.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }

        binding.galleryBtn.setOnClickListener {
            findNavController().navigate(R.id.action_camera_to_gallery)
        }

        setupGestures()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                camera?.cameraControl?.setZoomRatio(currentZoom * detector.scaleFactor)
                return true
            }
        })

        binding.viewFinder.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                binding.focusSquare.visibility = View.VISIBLE
                binding.focusSquare.scaleX = 1f
                binding.focusSquare.scaleY = 1f
                binding.focusSquare.alpha = 1f
                binding.focusSquare.x = event.x - 40f
                binding.focusSquare.y = event.y - 40f

                binding.focusSquare.animate()
                    .scaleX(1.3f)
                    .scaleY(1.3f)
                    .alpha(0f)
                    .setDuration(1500)
                    .withEndAction {
                        binding.focusSquare.visibility = View.GONE
                        binding.focusSquare.scaleX = 1f
                        binding.focusSquare.scaleY = 1f
                        binding.focusSquare.alpha = 1f
                    }
                    .start()

                val factory = binding.viewFinder.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }
            true
        }
    }




    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            val useCases = mutableListOf<UseCase>(preview)

            if (isVideoMode) {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                useCases.add(videoCapture!!)
            } else {
                imageCapture = ImageCapture.Builder().build()
                useCases.add(imageCapture!!)
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, *useCases.toTypedArray())
            } catch (e: Exception) {
                Log.e("CameraFragment", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.root.foreground = ColorDrawable(Color.WHITE)
        binding.root.postDelayed({ binding.root.foreground = null }, 50)

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > 28) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DemoCamera")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraFragment", "Photo failed: ${exc.message}", exc)
                }
            })
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > 28) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DemoCamera")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            requireContext().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(requireContext(), mediaStoreOutput)
            .apply {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        binding.captureBtn.backgroundTintList =
                            ContextCompat.getColorStateList(requireContext(), R.color.red)

                        binding.videoTimer.visibility = View.VISIBLE
                        binding.videoTimer.base = SystemClock.elapsedRealtime()
                        binding.videoTimer.start()
                    }
                    is VideoRecordEvent.Finalize -> {
                        binding.captureBtn.backgroundTintList =
                            ContextCompat.getColorStateList(requireContext(), R.color.black)
                        binding.videoTimer.stop()
                        binding.videoTimer.visibility = View.GONE
                        if (!recordEvent.hasError()) {
                            Toast.makeText(context, "Video saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }
    @RequiresApi(Build.VERSION_CODES.M)
    private fun animateFlash() {
        binding.root.postDelayed({
            binding.root.foreground = ColorDrawable(Color.WHITE)
            binding.root.postDelayed({
                binding.root.foreground = null
            }, 50)
        }, 100)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}