package com.workouthacker.revamp.pose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workouthacker.revamp.camera.WorkoutCameraController
import com.workoutpose.PoseAnalyzer
import com.workoutpose.PoseFrame
import com.workoutpose.PoseSkeletonOverlay
import com.workoutpose.WorkoutPoseConfig
import java.util.Locale

private val skeletonOptions =
        listOf(
                Color(0xFF22C55E),
                Color(0xFF00E5FF),
                Color(0xFFFFEB3B),
        )

@Composable
fun PoseScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
            ) { granted -> hasPermission = granted }

    var useLite by remember { mutableStateOf(true) }
    var useGpu by remember { mutableStateOf(true) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var oneEuro by remember { mutableStateOf(true) }
    var oneEuroMinCutoff by remember {
        mutableFloatStateOf(WorkoutPoseConfig.DEFAULT.oneEuroMinCutoff)
    }
    var oneEuroBeta by remember { mutableFloatStateOf(WorkoutPoseConfig.DEFAULT.oneEuroBeta) }
    var oneEuroDCutoff by remember { mutableFloatStateOf(WorkoutPoseConfig.DEFAULT.oneEuroDCutoff) }
    var visRecovery by remember { mutableStateOf(true) }
    var minVisibilityConfidence by remember {
        mutableFloatStateOf(WorkoutPoseConfig.DEFAULT.minVisibilityConfidence)
    }
    var skeletonColor by remember { mutableStateOf(skeletonOptions[0]) }
    var benchmarkLogging by remember { mutableStateOf(true) }

    var appliedConfig by remember {
        mutableStateOf(
                WorkoutPoseConfig(delegateSelection = WorkoutPoseConfig.DELEGATE_GPU)
        )
    }

    val analyzer = remember { PoseAnalyzer() }
    val poseFrame by analyzer.poseState.collectAsStateWithLifecycle()

    val controller = remember { WorkoutCameraController() }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    val lensFacing =
        if (useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

    LaunchedEffect(hasPermission, appliedConfig) {
        if (hasPermission) {
            controller.start(context, lifecycleOwner, previewView, analyzer, appliedConfig, lensFacing)
        }
    }

    LaunchedEffect(oneEuroMinCutoff, oneEuroBeta, oneEuroDCutoff) {
        if (hasPermission) {
            controller.updateOneEuroParameters(oneEuroMinCutoff, oneEuroBeta, oneEuroDCutoff)
        }
    }

    LaunchedEffect(minVisibilityConfidence) {
        if (hasPermission) {
            controller.updateVisibilityThreshold(minVisibilityConfidence)
        }
    }

    DisposableEffect(controller) { onDispose { controller.stop() } }

    val applyConfig = {
        appliedConfig =
                WorkoutPoseConfig(
                        modelSelection =
                                if (useLite) {
                                    WorkoutPoseConfig.MODEL_POSE_LANDMARKER_LITE
                                } else {
                                    WorkoutPoseConfig.MODEL_POSE_LANDMARKER_FULL
                                },
                        delegateSelection =
                                if (useGpu) {
                                    WorkoutPoseConfig.DELEGATE_GPU
                                } else {
                                    WorkoutPoseConfig.DELEGATE_CPU
                                },
                        enableOneEuroFilter = oneEuro,
                        oneEuroMinCutoff = oneEuroMinCutoff,
                        oneEuroBeta = oneEuroBeta,
                        oneEuroDCutoff = oneEuroDCutoff,
                        enableVisibilityRecovery = visRecovery,
                        minVisibilityConfidence = minVisibilityConfidence,
                        enableBenchmarkLogging = benchmarkLogging,
                )
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
                text = "Workout Pose",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
        )
        Text(
                text = "NATIVE KOTLIN / LIVE_STREAM",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )

        CameraPreviewCard(
                previewView = previewView,
                poseFrame = poseFrame,
                hasPermission = hasPermission,
                skeletonColor = skeletonColor,
                mirrorPreview = useFrontCamera,
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )

        Panel(title = "Controls") {
            ChipRow(
                    label = "Model",
                    options = listOf("Lite" to useLite, "Full" to !useLite),
                    onSelect = { useLite = it },
            )
            ChipRow(
                    label = "Delegate",
                    options = listOf("CPU" to !useGpu, "GPU" to useGpu),
                    onSelect = { useGpu = it },
            )
            ChipRow(
                    label = "Camera",
                    options = listOf("Front" to useFrontCamera, "Back" to !useFrontCamera),
                    onSelect = { useFrontCamera = it },
            )

            ToggleRow("One Euro filter", oneEuro) { oneEuro = it }

            if (oneEuro) {
                SliderRow(
                        label = "Min cutoff",
                        valueText = String.format(Locale.US, "%.1f Hz", oneEuroMinCutoff),
                        value = oneEuroMinCutoff,
                        range = 0.1f..10f,
                        onValueChange = { oneEuroMinCutoff = it },
                )
                SliderRow(
                        label = "Beta",
                        valueText = String.format(Locale.US, "%.4f", oneEuroBeta),
                        value = oneEuroBeta,
                        range = 0f..0.1f,
                        onValueChange = { oneEuroBeta = it },
                )
                SliderRow(
                        label = "Deriv. cutoff",
                        valueText = String.format(Locale.US, "%.1f Hz", oneEuroDCutoff),
                        value = oneEuroDCutoff,
                        range = 0.5f..5f,
                        onValueChange = { oneEuroDCutoff = it },
                )

                Text(
                        text = "Sliders apply live (no restart)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                )
            }

            ToggleRow("Visibility recovery", visRecovery) { visRecovery = it }

            ToggleRow("Benchmark logging", benchmarkLogging) { benchmarkLogging = it }

            if (visRecovery) {
                SliderRow(
                        label = "Min confidence",
                        valueText = String.format(Locale.US, "%.2f", minVisibilityConfidence),
                        value = minVisibilityConfidence,
                        range = 0f..1f,
                        onValueChange = { minVisibilityConfidence = it },
                )
            }

            Text(
                    text = "Skeleton color",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                skeletonOptions.forEach { color ->
                    Box(
                            modifier =
                                    Modifier.width(36.dp)
                                            .height(36.dp)
                                            .background(color, RoundedCornerShape(18.dp))
                                            .border(
                                                    width =
                                                            if (color == skeletonColor) 3.dp
                                                            else 2.dp,
                                                    color =
                                                            if (color == skeletonColor) {
                                                                MaterialTheme.colorScheme.onSurface
                                                            } else {
                                                                MaterialTheme.colorScheme.outline
                                                            },
                                                    shape = RoundedCornerShape(18.dp),
                                            )
                                            .clickable { skeletonColor = color },
                    )
                }
            }

            Button(
                    onClick = applyConfig,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) { Text("Apply & Restart") }
        }

        ReadoutPanel(poseFrame)

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CameraPreviewCard(
        previewView: PreviewView,
        poseFrame: PoseFrame,
        hasPermission: Boolean,
        skeletonColor: Color,
        mirrorPreview: Boolean,
        onRequestPermission: () -> Unit,
) {
    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(16.dp)
                            ),
    ) {
        AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
        )

        if (hasPermission) {
            PoseSkeletonOverlay(
                    frame = poseFrame,
                    modifier = Modifier.fillMaxSize(),
                    skeletonColor = skeletonColor,
                    mirror = mirrorPreview,
            )
        }

        Badge(
                text = if (poseFrame.poseVisible) "POSE DETECTED" else "NO POSE",
                color = if (poseFrame.poseVisible) Color(0xFF22C55E) else Color(0xFFFB7185),
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        )
        Badge(
                text = "${fmtMs(poseFrame.inferenceTimeMs)}",
                color = Color(0xFF9FB3D1),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        )

        if (!hasPermission) {
            Column(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                        text = "Camera access is required",
                        color = Color.White,
                        fontSize = 15.sp,
                )
                Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Grant camera access")
                }
            }
        }
    }
}

@Composable
private fun ReadoutPanel(poseFrame: PoseFrame) {
    Panel(title = "Readout") {
        StatRow("Pose visible", if (poseFrame.poseVisible) "YES" else "NO")
        StatRow(
                "Visible landmarks",
                "${poseFrame.visibleLandmarkCount} / ${com.workoutpose.PoseGeometry.LANDMARK_COUNT}"
        )
        StatRow("Inference", fmtMs(poseFrame.inferenceTimeMs))
        StatRow("Landmark buffer", "${poseFrame.landmarks.size} values")

        Text(
                text = "Joint angles (deg)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )
        val labels =
                listOf(
                        "L Hip",
                        "R Hip",
                        "L Knee",
                        "R Knee",
                        "L Elbow",
                        "R Elbow",
                        "L Shoulder",
                        "R Shoulder",
                )
        labels.zip(poseFrame.angles.all).forEach { (label, value) ->
            StatRow(label, fmtAngle(value))
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
            modifier =
                    Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(MaterialTheme.colorScheme.surface, shape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .padding(14.dp),
    ) {
        Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun ChipRow(
        label: String,
        options: List<Pair<String, Boolean>>,
        onSelect: (Boolean) -> Unit
) {
    Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (option, selected) ->
            OptionChip(
                    label = option,
                    selected = selected,
                    onClick = { onSelect(selected.not()) },
            )
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
            modifier =
                    Modifier.clip(RoundedCornerShape(999.dp))
                            .background(if (selected) colors.primary else colors.surfaceVariant)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
        label: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
            )
            Text(
                    text = valueText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
        )
        Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
            modifier =
                    modifier.clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
        )
    }
}

private fun fmtMs(value: Double): String =
        if (value < 0) "--" else String.format(Locale.US, "%.0f ms", value)

private fun fmtAngle(value: Float): String =
        if (value < 0) "--" else String.format(Locale.US, "%.0f°", value)
