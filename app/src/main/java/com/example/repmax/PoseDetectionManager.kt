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
fun Pose.getExerciseLandmarks(): ExerciseLandmarks? {
    val landmarks = this.allPoseLandmarks
    if (landmarks.isEmpty()) return null

    return ExerciseLandmarks(
        leftShoulder = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_SHOULDER },
        rightShoulder = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_SHOULDER },
        leftElbow = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_ELBOW },
        rightElbow = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_ELBOW },
        leftWrist = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_WRIST },
        rightWrist = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_WRIST },
        leftHip = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_HIP },
        rightHip = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_HIP },
        leftKnee = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_KNEE },
        rightKnee = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_KNEE },
        leftAnkle = landmarks.firstOrNull { it.landmarkType == PoseLandmark.LEFT_ANKLE },
        rightAnkle = landmarks.firstOrNull { it.landmarkType == PoseLandmark.RIGHT_ANKLE }
    )
}


