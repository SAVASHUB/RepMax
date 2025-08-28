package com.example.repmax

import com.google.mlkit.vision.pose.PoseLandmark

data class ExerciseLandmarks(
    val leftShoulder: PoseLandmark?,
    val rightShoulder: PoseLandmark?,
    val leftElbow: PoseLandmark?,
    val rightElbow: PoseLandmark?,
    val leftWrist: PoseLandmark?,
    val rightWrist: PoseLandmark?,
    val leftHip: PoseLandmark?,
    val rightHip: PoseLandmark?,
    val leftKnee: PoseLandmark?,
    val rightKnee: PoseLandmark?,
    val leftAnkle: PoseLandmark?,
    val rightAnkle: PoseLandmark?
)
