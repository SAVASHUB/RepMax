package com.example.repmax

enum class ExerciseType ( val displayname : String){
    SQUAT("Squats"),
    PUSHUP("Push-ups");

    companion object {
        fun fromString(name: String): ExerciseType {
            return values().find { it.name == name } ?: SQUAT
        }
    }
}