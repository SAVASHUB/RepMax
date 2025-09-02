package com.example.repmax

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var landmarks: ExerciseLandmarks? = null
    private var isPositioned = false
    private var imageWidth = 0
    private var imageHeight = 0
    private var transformationMatrix: Matrix? = null

    // Paints for different joint states
    private val jointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val jointStrokePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val positionedJointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val positionedStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // Connection lines between joints
    private val connectionPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        alpha = 150 // Semi-transparent
    }

    fun updatePose(landmarks: ExerciseLandmarks?, positioned: Boolean = false, imageWidth: Int = 0, imageHeight: Int = 0) {
        this.landmarks = landmarks
        this.isPositioned = positioned
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        // Calculate transformation matrix when we have valid dimensions
        if (imageWidth > 0 && imageHeight > 0 && width > 0 && height > 0) {
            calculateTransformationMatrix()
        }

        invalidate() // Trigger redraw
    }

    private fun calculateTransformationMatrix() {
        transformationMatrix = Matrix()

        // Calculate scale factors
        val scaleX = width.toFloat() / imageWidth.toFloat()
        val scaleY = height.toFloat() / imageHeight.toFloat()

        // For front camera, we need to flip horizontally and adjust scaling
        transformationMatrix?.apply {
            // Flip horizontally for front camera (mirror effect)
            postScale(-scaleX, scaleY)
            // Translate to correct position after flip
            postTranslate(width.toFloat(), 0f)
        }
    }

    private fun transformPoint(x: Float, y: Float): PointF {
        val transformedPoint = floatArrayOf(x, y)
        transformationMatrix?.mapPoints(transformedPoint)
        return PointF(transformedPoint[0], transformedPoint[1])
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas

        val landmarks = this.landmarks ?: return

        // Only draw if we have a valid transformation matrix
        if (transformationMatrix == null) return

        // Draw connections first (so they appear behind the joints)
        drawConnections(canvas, landmarks)

        // Draw joint landmarks
        drawJoint(canvas, landmarks.leftHip, "L Hip")
        drawJoint(canvas, landmarks.rightHip, "R Hip")
        drawJoint(canvas, landmarks.leftKnee, "L Knee")
        drawJoint(canvas, landmarks.rightKnee, "R Knee")
    }

    private fun drawConnections(canvas: Canvas, landmarks: ExerciseLandmarks) {
        // Draw hip-to-knee connections
        landmarks.leftHip?.let { leftHip ->
            landmarks.leftKnee?.let { leftKnee ->
                val hipPoint = transformPoint(leftHip.position.x, leftHip.position.y)
                val kneePoint = transformPoint(leftKnee.position.x, leftKnee.position.y)
                canvas.drawLine(
                    hipPoint.x, hipPoint.y,
                    kneePoint.x, kneePoint.y,
                    connectionPaint
                )
            }
        }

        landmarks.rightHip?.let { rightHip ->
            landmarks.rightKnee?.let { rightKnee ->
                val hipPoint = transformPoint(rightHip.position.x, rightHip.position.y)
                val kneePoint = transformPoint(rightKnee.position.x, rightKnee.position.y)
                canvas.drawLine(
                    hipPoint.x, hipPoint.y,
                    kneePoint.x, kneePoint.y,
                    connectionPaint
                )
            }
        }

        // Draw hip-to-hip connection
        landmarks.leftHip?.let { leftHip ->
            landmarks.rightHip?.let { rightHip ->
                val leftPoint = transformPoint(leftHip.position.x, leftHip.position.y)
                val rightPoint = transformPoint(rightHip.position.x, rightHip.position.y)
                canvas.drawLine(
                    leftPoint.x, leftPoint.y,
                    rightPoint.x, rightPoint.y,
                    connectionPaint
                )
            }
        }
    }

    private fun drawJoint(canvas: Canvas, landmark: PoseLandmark?, label: String) {
        landmark ?: return

        // Transform the landmark coordinates to screen coordinates
        val point = transformPoint(landmark.position.x, landmark.position.y)
        val x = point.x
        val y = point.y

        // Choose paint based on positioning state
        val fillPaint = if (isPositioned) positionedJointPaint else jointPaint
        val strokePaint = if (isPositioned) positionedStrokePaint else jointStrokePaint

        // Draw outer circle (stroke)
        canvas.drawCircle(x, y, 20f, strokePaint)

        // Draw inner circle (fill)
        canvas.drawCircle(x, y, 16f, fillPaint)

        // Draw label
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.BLACK) // Add shadow for better visibility
        }

        // Position label slightly above the joint
        canvas.drawText(label, x, y - 35f, textPaint)
    }

    // Helper method to clear the overlay
    fun clearPose() {
        landmarks = null
        invalidate()
    }
}