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
import android.widget.Button

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

    // Positioning System
    private var isUserPositioned = false
    private var positioningCheckCount = 0
    private val requiredPositionFrames = 60 // User must be in position for 30 frames (~1 second)


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

    private fun endExercise() {
        // Optional: Save exercise results or show summary
        Toast.makeText(this, "Exercise completed! Reps: $repCount", Toast.LENGTH_SHORT).show()

        // Go back to MainActivity (home page)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun showPositioningPhase() {
        // Show the positioning frame
        positioningFrame.visibility = android.view.View.VISIBLE
        updateInstruction("Position yourself inside the frame")
        isUserPositioned = false
        positioningCheckCount = 0
    }

    private fun PositioningFrame() {
        // Keep the positioning frame visible but change its purpose
        // positioningFrame.visibility = android.view.View.GONE
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
                    PositioningFrame()
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

    private fun checkPullUpPositioning(landmarks: ExerciseLandmarks): Boolean { return false}

    private fun checkPushUpPositioning(landmarks: ExerciseLandmarks): Boolean { return false}

    private fun checkSquatPositioning(landmarks: ExerciseLandmarks): Boolean {
        // Check if user is properly positioned for squat detection
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee
        val leftAnkle = landmarks.leftAnkle
        val rightAnkle = landmarks.rightAnkle
        val leftShoulder = landmarks.leftShoulder
        val rightShoulder = landmarks.rightShoulder

        // All key points must be visible
        if (leftHip == null || rightHip == null ||
            leftKnee == null || rightKnee == null ||
            leftAnkle == null || rightAnkle == null ||
            leftShoulder == null || rightShoulder == null) {
            return false
        }

        // Get screen dimensions
        val screenWidth = previewView.width.toFloat()
        val screenHeight = previewView.height.toFloat()

        // Define frame boundaries (middle 70% of screen width, middle 80% of height)
        val frameLeft = screenWidth * 0.15f
        val frameRight = screenWidth * 0.85f
        val frameTop = screenHeight * 0.1f
        val frameBottom = screenHeight * 0.9f

        // Check if key body points are within the frame
        val avgShoulderX = (leftShoulder.position.x + rightShoulder.position.x) / 2
        val avgHipX = (leftHip.position.x + rightHip.position.x) / 2
        val avgAnkleX = (leftAnkle.position.x + rightAnkle.position.x) / 2

        val headY = minOf(leftShoulder.position.y, rightShoulder.position.y) - 50 // Estimate head position
        val feetY = maxOf(leftAnkle.position.y, rightAnkle.position.y)

        return avgShoulderX in frameLeft..frameRight &&
                avgHipX in frameLeft..frameRight &&
                avgAnkleX in frameLeft..frameRight &&
                headY >= frameTop &&
                feetY <= frameBottom
    }


    private fun analyzeExerciseMovement(pose: Pose) {
        val landmarks = pose.getExerciseLandmarks()

        if (landmarks != null) {
            // First check if user is positioned (if positioning frame is still visible)
            if (!isUserPositioned) {
                checkUserPositioning(pose)
                return // Don't count reps until positioned
            }

            // Only start counting reps after user is properly positioned
            val newState = when (currentExercise) {
                ExerciseType.SQUAT -> detectSquatMovement(landmarks)
                ExerciseType.PUSH_UP -> detectPushUpMovement(landmarks)
                ExerciseType.PULL_UP -> detectPullUpMovement(landmarks)
            }

            // Check for rep completion - only count when transitioning from DOWN to UP
            if (exerciseState != newState) {
                val oldState = exerciseState
                exerciseState = newState

                Log.d("ExerciseActivity", "State change: $oldState -> $newState")

                when {
                    oldState == ExerciseState.DOWN_POSITION && newState == ExerciseState.UP_POSITION -> {
                        repCount++
                        updateRepCount(repCount)
                        updateInstruction("Great rep! Keep going - Reps: $repCount")
                        Log.d("ExerciseActivity", "Rep completed! Total: $repCount")
                    }
                    newState == ExerciseState.DOWN_POSITION -> {
                        updateInstruction("Good! Now go back up")
                        Log.d("ExerciseActivity", "Down position detected")
                    }
                    newState == ExerciseState.UP_POSITION -> {
                        updateInstruction("Ready for next squat")
                        Log.d("ExerciseActivity", "Up position detected")
                    }
                    newState == ExerciseState.TRANSITION -> {
                        updateInstruction("Keep moving...")
                    }
                }
            }
        }
    }

    private fun detectSquatMovement(landmarks: ExerciseLandmarks): ExerciseState {
        val leftHip = landmarks.leftHip
        val rightHip = landmarks.rightHip
        val leftKnee = landmarks.leftKnee
        val rightKnee = landmarks.rightKnee

        if (leftHip != null && rightHip != null &&
            leftKnee != null && rightKnee != null) {

            // Calculate average positions
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2

            // FIXED: Use absolute distance since knees are below hips
            val hipKneeDistance = avgKneeY - avgHipY  // Now this will be positive

            // Debug logging
            Log.d("SquatDetection", "Hip Y: $avgHipY, Knee Y: $avgKneeY, Distance: $hipKneeDistance")

            return when {
                hipKneeDistance < 300 -> {  // Squatting - hips closer to knees
                    Log.d("SquatDetection", "DOWN position - distance: $hipKneeDistance")
                    ExerciseState.DOWN_POSITION
                }
                hipKneeDistance > 400 -> {  // Standing - hips far from knees
                    Log.d("SquatDetection", "UP position - distance: $hipKneeDistance")
                    ExerciseState.UP_POSITION
                }
                else -> {
                    Log.d("SquatDetection", "TRANSITION - distance: $hipKneeDistance")
                    ExerciseState.TRANSITION
                }
            }
        }

        Log.d("SquatDetection", "READY - missing landmarks")
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

}