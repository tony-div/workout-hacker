package com.workoutpose.exercise

/** Single pose landmark in normalized MediaPipe space (x right, y down). */
data class Landmark(val x: Double, val y: Double, val z: Double, val visibility: Double)
