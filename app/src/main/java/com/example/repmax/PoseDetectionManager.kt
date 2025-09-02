package com.example.repmax

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

class PoseDetectionManager {
    // Private detector instance - only this class manages it
    private var poseDetector: PoseDetector? = null

    // Track initialization state
    private var isInitialized = false

    /**
     * Initialize the pose detector with options optimized for RepMax
     * Call this once when your app starts
     */
    fun initialize() {
        if (isInitialized) return // Prevent multiple initializations

        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE) // For live camera
            .build()

        poseDetector = PoseDetection.getClient(options)
        isInitialized = true
    }

    /**
     * Main function to detect pose from camera frame
     * @param image: InputImage from camera
     * @param onSuccess: Callback when pose is detected successfully
     * @param onFailure: Callback when detection fails
     */
    fun detectPose(
        image: InputImage,
        onSuccess: (Pose) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (!isInitialized || poseDetector == null) {
            onFailure(IllegalStateException("PoseDetector not initialized"))
            return
        }

        poseDetector?.process(image)
            ?.addOnSuccessListener { pose ->
                onSuccess(pose)
            }
            ?.addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Alternative method with single callback for RepMax-specific logic
     */
    fun detectPoseForRepCounting(
        image: InputImage,
        callback: (Pose?, String?) -> Unit // Pose or error message
    ) {
        detectPose(
            image = image,
            onSuccess = { pose -> callback(pose, null) },
            onFailure = { exception -> callback(null, exception.message) }
        )
    }

    /**
     * Check if detector is ready to use
     */
    fun isReady(): Boolean = isInitialized && poseDetector != null

    /**
     * Clean up resources - MUST call this in onDestroy
     */
    fun cleanup() {
        poseDetector?.close()
        poseDetector = null
        isInitialized = false
    }
}

// Extension functions for RepMax-specific pose analysis (OUTSIDE the class)
/**
 * Helper extension to extract key landmarks for exercise tracking
 */
fun Pose.getExerciseLandmarks(minConfidence: Float = 0.6f): ExerciseLandmarks? {
    val landmarks = this.allPoseLandmarks
    if (landmarks.isEmpty()) return null

    fun safeGet(type: Int): PoseLandmark? {
        val lm = landmarks.firstOrNull { it.landmarkType == type }
        return if (lm != null && lm.inFrameLikelihood >= minConfidence) lm else null
    }

    return ExerciseLandmarks(
        leftShoulder = safeGet(PoseLandmark.LEFT_SHOULDER),
        rightShoulder = safeGet(PoseLandmark.RIGHT_SHOULDER),
        leftElbow = safeGet(PoseLandmark.LEFT_ELBOW),
        rightElbow = safeGet(PoseLandmark.RIGHT_ELBOW),
        leftWrist = safeGet(PoseLandmark.LEFT_WRIST),
        rightWrist = safeGet(PoseLandmark.RIGHT_WRIST),
        leftHip = safeGet(PoseLandmark.LEFT_HIP),
        rightHip = safeGet(PoseLandmark.RIGHT_HIP),
        leftKnee = safeGet(PoseLandmark.LEFT_KNEE),
        rightKnee = safeGet(PoseLandmark.RIGHT_KNEE),
        leftAnkle = safeGet(PoseLandmark.LEFT_ANKLE),
        rightAnkle = safeGet(PoseLandmark.RIGHT_ANKLE)
    )
}



