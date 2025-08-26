package com.example.repmax

enum class ExerciseType(val displayName: String) {
    SQUAT("Squats"),
    PUSH_UP("Push-ups"),
    PULL_UP("Pull-ups");

    companion object {
        fun fromString(name: String): ExerciseType {
            return values().find { it.name == name } ?: SQUAT
        }
    }
}