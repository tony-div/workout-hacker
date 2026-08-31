package com.workouthacker.revamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.workouthacker.revamp.pose.PoseScreen
import com.workouthacker.revamp.ui.theme.WorkoutHackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutHackerTheme {
                PoseScreen()
            }
        }
    }
}