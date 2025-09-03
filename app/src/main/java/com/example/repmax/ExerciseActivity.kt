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
import android.content.Intent
import android.view.View
import android.widget.Button


enum class ExerciseState {
    READY,
    DOWN_POSITION,
    UP_POSITION,
    TRANSITION
}

class ExerciseActivity : AppCompatActivity() {

    // Pose Detection Manager
    private val poseDetectionManager = PoseDetectionManager()

    // Exercise Info
    private lateinit var currentExercise: ExerciseType
    private var repCount = 0

    // Simple Positioning System
    private var isUserPositioned = false
    private var positioningCheckCount = 0
    private val requiredPositionFrames = 60 // User must be in position for 60 frames (~2 seconds)

    // Camera Components
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // UI Elements
    private lateinit var previewView: PreviewView
    private lateinit var exerciseTitleText: TextView
    private lateinit var repCountText: TextView
    private lateinit var instructionText: TextView
    private lateinit var positioningFrame: android.view.View

    // Baseline measurements for adaptive squat detection
    private var baselineHipKneeDistance: Float = 0f
    private var baselineRecorded = false

    private var wasDown = false

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
        positioningFrame = findViewById(R.id.positioning_frame)

        val finishButton = findViewById<Button>(R.id.btn_finish)
        finishButton.setOnClickListener {
            endExercise()
        }

        // Set initial UI
        exerciseTitleText.text = currentExercise.displayName
        updateRepCount(0)
        showPositioningPhase()
    }


    private fun showPositioningPhase() {
        positioningFrame.visibility = View.VISIBLE
        updateInstruction("Position yourself inside the frame")
        isUserPositioned = false
        positioningCheckCount = 0
        baselineRecorded = false
    }

    private fun hidePositioningFrame() {
        positioningFrame.visibility = android.view.View.GONE
        updateInstruction("Great! Start your ${currentExercise.displayName.lowercase()}!")
        isUserPositioned = true
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
                it.surfaceProvider = previewView.surfaceProvider
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

            poseDetectionManager.detectPoseForRepCounting(image) { pose, error ->
                if (error != null) {
                    Log.e("ExerciseActivity", "Pose detection error: $error")
                } else if (pose != null) {
                    analyzeExerciseMovement(pose)
                }
                imageProxy.close()
            }
        }
    }

    private fun analyzeExerciseMovement(pose: Pose) {
        val landmarks = pose.getExerciseLandmarks()

        if (landmarks != null) {
            if (!isUserPositioned) {
                checkUserPositioning(pose)
                return
            }

            if (!baselineRecorded) return

            val newState = when (currentExercise) {
                ExerciseType.SQUAT -> detectSquatMovement(landmarks)
                else -> ExerciseState.READY
            }

            when (newState) {
                ExerciseState.DOWN_POSITION -> {
                    wasDown = true
                    updateInstruction("Good! Now go back up")
                }
                ExerciseState.UP_POSITION -> {
                    if (wasDown) {
                        repCount++
                        updateRepCount(repCount)
                        updateInstruction("Great rep! Total: $repCount")
                        wasDown = false
                    } else {
                        updateInstruction("Ready for next squat")
                    }
                }
                ExerciseState.TRANSITION -> {
                    updateInstruction("Keep moving...")
                }
                else -> {}
            }
        }
    }


    private fun checkUserPositioning(pose: Pose) {
        val landmarks = pose.getExerciseLandmarks()

        if (landmarks != null) {
            val isInFrame = when (currentExercise) {
                ExerciseType.SQUAT -> checkSquatPositioning(landmarks)
                ExerciseType.PUSH_UP -> checkPushUpPositioning(landmarks)
                ExerciseType.PULL_UP -> checkPullUpPositioning(landmarks)
            }

            if (isInFrame) {
                positioningCheckCount++
                updateInstruction("Hold position... ${positioningCheckCount}/${requiredPositionFrames}")

                if (positioningCheckCount >= requiredPositionFrames) {
                    // Record baseline measurements and hide frame
                    recordBaselineMeasurements(landmarks)
                    hidePositioningFrame()
                }
            } else {
                positioningCheckCount = 0
                updateInstruction("Position yourself inside the frame")
            }
        } else {
            positioningCheckCount = 0
            updateInstruction("Step back so your full body is visible")
        }
    }

    private fun checkSquatPositioning(landmarks: ExerciseLandmarks): Boolean {
        // Check if user is properly positioned for squat detection
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee
        val leftAnkle = landmarks.leftAnkle
        val rightAnkle = landmarks.rightAnkle
8
        // All key points must be visible
        if (leftHip == null || rightHip == null ||
            leftKnee == null || rightKnee == null ||
            leftAnkle == null || rightAnkle == null) {
            return false
        }

        // Get screen dimensions
        val screenWidth = previewView.width.toFloat()
        val screenHeight = previewView.height.toFloat()

        // Define frame boundaries (middle 70% of screen width, middle 80% of height) // relax
        val frameLeft = screenWidth * 0.1f
        val frameRight = screenWidth * 0.9f
        val frameBottom = screenHeight * 0.9f

        // Check if key body points are within the frame
        val avgHipX = (leftHip.position.x + rightHip.position.x) / 2
        val avgAnkleX = (leftAnkle.position.x + rightAnkle.position.x) / 2

        val feetY = maxOf(leftAnkle.position.y, rightAnkle.position.y)

        return  avgHipX in frameLeft..frameRight &&
                avgAnkleX in frameLeft..frameRight &&
                feetY <= frameBottom
    }

    private fun recordBaselineMeasurements(landmarks: ExerciseLandmarks) {
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee

        if (leftHip != null && rightHip != null && leftKnee != null && rightKnee != null) {
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2
            baselineHipKneeDistance = avgKneeY - avgHipY
            baselineRecorded = true

            Log.d("ExerciseActivity", "Baseline recorded: $baselineHipKneeDistance pixels")
        }
    }

    private fun detectSquatMovement(landmarks: ExerciseLandmarks): ExerciseState {
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee

        if (leftHip != null && rightHip != null &&
            leftKnee != null && rightKnee != null && baselineRecorded) {

            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2
            val currentDistance = avgKneeY - avgHipY

            // Use percentage of baseline for thresholds (adaptive to person's size)
            val squatThreshold = baselineHipKneeDistance * 0.65f  // 65% closer = squat
            val standThreshold = baselineHipKneeDistance * 0.95f // 95% of baseline = standing

            Log.d("SquatDetection", "Current: $currentDistance, Baseline: $baselineHipKneeDistance")
            Log.d("SquatDetection", "Squat threshold: $squatThreshold, Stand threshold: $standThreshold")

            return when {
                currentDistance <= squatThreshold -> {
                    Log.d("SquatDetection", "DOWN - distance: $currentDistance")
                    ExerciseState.DOWN_POSITION
                }
                currentDistance >= standThreshold -> {
                    Log.d("SquatDetection", "UP - distance: $currentDistance")
                    ExerciseState.UP_POSITION
                }
                else -> {
                    Log.d("SquatDetection", "TRANSITION - distance: $currentDistance")
                    ExerciseState.TRANSITION
                }
            }
        }

        return ExerciseState.READY
    }

    // Keep your existing placeholder methods
    private fun detectPushUpMovement(landmarks: ExerciseLandmarks): ExerciseState {
        return ExerciseState.READY
    }

    private fun detectPullUpMovement(landmarks: ExerciseLandmarks): ExerciseState {
        return ExerciseState.READY
    }

    private fun checkPushUpPositioning(landmarks: ExerciseLandmarks): Boolean {
        return false
    }

    private fun checkPullUpPositioning(landmarks: ExerciseLandmarks): Boolean {
        return false
    }

    // Keep your existing UI update methods
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

    private fun endExercise() {
        Toast.makeText(this, "Exercise completed! Reps: $repCount", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up resources
        poseDetectionManager.cleanup()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()

        Log.d("ExerciseActivity", "Activity destroyed, resources cleaned up")
    }

}