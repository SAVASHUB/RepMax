package com.example.repmax
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.core.ImageProxy

enum class ExerciseState {
    READY,
    DOWN_POSITION,
    UP_POSITION,
    TRANSITION,
    COMPLETED
}

class ExerciseActivity : AppCompatActivity() {

    // Pose Detection Manager
    private val poseDetectionManager = PoseDetectionManager()

    // Exercise Info
    private lateinit var currentExercise: ExerciseType
    private var repCount = 0
    private var exerciseState = ExerciseState.READY

    // Camera Components
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // UI Elements
    private lateinit var previewView: PreviewView
    private lateinit var exerciseTitleText: TextView
    private lateinit var repCountText: TextView
    private lateinit var instructionText: TextView

    // Camera Permission Launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for exercise tracking", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        // Get exercise type from intent
        val exerciseTypeName = intent.getStringExtra("EXERCISE_TYPE") ?: ExerciseType.SQUAT.name
        currentExercise = ExerciseType.fromString(exerciseTypeName)

        initializeViews()
        initializePoseDetection()

        // Check camera permission
        if (allPermissionsGranted()) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.preview_view)
        exerciseTitleText = findViewById(R.id.tv_exercise_title)
        repCountText = findViewById(R.id.tv_rep_count)
        instructionText = findViewById(R.id.tv_instruction)

        // Set initial UI
        exerciseTitleText.text = currentExercise.toString()
        updateRepCount(0)
        updateInstruction("Get ready! Position yourself in front of the camera")
    }

    private fun initializePoseDetection() {
        try {
            poseDetectionManager.initialize()
            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d("ExerciseActivity", "Pose detection initialized successfully")
        } catch (e: Exception) {
            Log.e("ExerciseActivity", "Failed to initialize pose detection", e)
            Toast.makeText(this, "Failed to initialize exercise tracking", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (exc: Exception) {
                Log.e("ExerciseActivity", "Use case binding failed", exc)
                Toast.makeText(this, "Camera setup failed", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return

        // Camera preview
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // Image analysis for pose detection
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageForPoseDetection(imageProxy)
                }
            }

        // Select front camera for better user experience
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            updateInstruction("Start your ${currentExercise.toString().lowercase()}!")

        } catch (exc: Exception) {
            Log.e("ExerciseActivity", "Use case binding failed", exc)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageForPoseDetection(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            // FIXED: Use detectPoseForRepCounting instead of detectPose
            poseDetectionManager.detectPoseForRepCounting(image) { pose, error ->
                if (error != null) {
                    Log.e("ExerciseActivity", "Pose detection error: $error")
                } else if (pose != null) {
                    analyzeExerciseMovement(pose)
                }

                // Always close the image proxy
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeExerciseMovement(pose: Pose) {
        // FIXED: Use the extension function to get landmarks
        val landmarks = pose.getExerciseLandmarks()

        if (landmarks != null) {
            // Exercise-specific detection logic
            val newState = when (currentExercise) {
                ExerciseType.SQUAT -> detectSquatMovement(landmarks)
                ExerciseType.PUSH_UP -> detectPushUpMovement(landmarks)
                ExerciseType.PULL_UP -> detectPullUpMovement(landmarks)
            }

            // Check for rep completion
            if (exerciseState != newState) {
                exerciseState = newState

                when (exerciseState) {
                    ExerciseState.DOWN_POSITION -> {
                        updateInstruction("Good! Now go back up")
                    }
                    ExerciseState.UP_POSITION -> {
                        // FIXED: Simplified logic for rep counting
                        repCount++
                        updateRepCount(repCount)
                        updateInstruction("Great rep! Keep going")
                        Log.d("ExerciseActivity", "Rep completed! Total: $repCount")
                    }
                    ExerciseState.TRANSITION -> {
                        updateInstruction("Keep moving...")
                    }
                    else -> {}
                }
            }
        } else {
            updateInstruction("Position yourself fully in the camera view")
        }
    }

    private fun detectSquatMovement(landmarks: ExerciseLandmarks): ExerciseState {
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee
        val leftAnkle = landmarks.leftAnkle
        val rightAnkle = landmarks.rightAnkle

        if (leftHip != null && rightHip != null &&
            leftKnee != null && rightKnee != null &&
            leftAnkle != null && rightAnkle != null) {

            // Calculate average positions
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2
            val avgAnkleY = (leftAnkle.position.y + rightAnkle.position.y) / 2

            // Squat detection logic
            // When squatting down, knees should be closer to ankles
            val kneeAnkleDistance = avgKneeY - avgAnkleY
            val hipKneeDistance = avgHipY - avgKneeY

            return when {
                kneeAnkleDistance < 100 && hipKneeDistance < 80 -> ExerciseState.DOWN_POSITION
                kneeAnkleDistance > 150 && hipKneeDistance > 120 -> ExerciseState.UP_POSITION
                else -> ExerciseState.TRANSITION
            }
        }

        return ExerciseState.READY
    }

    private fun detectPushUpMovement(landmarks: ExerciseLandmarks): ExerciseState {
        // Placeholder for push-up detection
        // You can implement this later
        return ExerciseState.READY
    }

    private fun detectPullUpMovement(landmarks: ExerciseLandmarks): ExerciseState {
        // Placeholder for pull-up detection
        // You can implement this later
        return ExerciseState.READY
    }

    private fun updateRepCount(count: Int) {
        runOnUiThread {
            repCountText.text = "Reps: $count"
        }
    }

    private fun updateInstruction(instruction: String) {
        runOnUiThread {
            instructionText.text = instruction
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        poseDetectionManager.cleanup()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()

        Log.d("ExerciseActivity", "Activity destroyed, resources cleaned up")
    }

    override fun onPause() {
        super.onPause()
        // You can pause detection here if needed
    }

    override fun onResume() {
        super.onResume()
        // Resume detection if paused
    }
}