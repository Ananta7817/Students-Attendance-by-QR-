package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.QrScanOutcome
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.AttendanceStatusBadge
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronSubScreenBar
import com.example.ui.components.StatusPill
import com.example.ui.theme.ErrorLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessLight
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningLight
import com.example.util.LocalAppStrings
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private fun checkNetworkOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Suppress("DEPRECATION")
private fun triggerHapticPulse(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v?.vibrate(70)
            }
        }
    } catch (_: Exception) {
    }
}

@Composable
fun QrScannerAndManualAttendanceScreen(
    school: School?,
    actor: UserProfile,
    classes: List<SchoolClass>,
    students: List<Student>,
    todayAttendance: List<AttendanceRecord>,
    initialClassId: String?,
    scanOutcome: QrScanOutcome,
    vibrateOnScan: Boolean,
    onBack: (() -> Unit)?,
    onQrDetected: (rawContent: String, selectedClassId: String?, isOnline: Boolean) -> Unit,
    onResetOutcome: () -> Unit,
    onManualMark: (Student, AttendanceStatus) -> Unit,
) {
    if (onBack != null) BackHandler(onBack = onBack)
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val permittedClasses = remember(classes, actor) {
        if (actor.roleEnum == UserRole.TEACHER) {
            classes.filter { it.id in actor.assignedClassIds }
        } else {
            classes
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = QR Scanner, 1 = Manual Attendance
    var selectedClassId by remember(initialClassId, permittedClasses) {
        mutableStateOf(
            initialClassId?.takeIf { id -> permittedClasses.any { it.id == id } }
                ?: permittedClasses.firstOrNull()?.id
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var lastScanTime by remember { mutableStateOf(0L) }
    val isOnline = remember(scanOutcome, selectedTab) { checkNetworkOnline(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(scanOutcome) {
        if (vibrateOnScan && (scanOutcome is QrScanOutcome.Recorded || scanOutcome is QrScanOutcome.AlreadyPresent)) {
            triggerHapticPulse(context)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBack != null) {
            SaffronSubScreenBar(
                title = strings.scanQrAttendance,
                subtitle = school?.schoolName,
                onBack = onBack,
                trailing = {
                    StatusPill(
                        text = if (isOnline) "Online" else "Offline",
                        backgroundColor = if (isOnline) SuccessLight else WarningLight,
                        contentColor = if (isOnline) SuccessGreen else WarningAmber,
                    )
                },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(strings.scanQrAttendance, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (isOnline) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        if (isOnline) "Online Sync" else "Offline Queue",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOnline) SuccessGreen else WarningAmber,
                    )
                }
            }
        }

        // Class selector row
        if (permittedClasses.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (actor.roleEnum != UserRole.TEACHER) {
                    item {
                        FilterChip(
                            selected = selectedClassId == null,
                            onClick = { selectedClassId = null },
                            label = { Text("Any Class") },
                        )
                    }
                }
                items(permittedClasses, key = { it.id }) { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { selectedClassId = cls.id },
                        label = { Text(cls.className) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronLight,
                            selectedLabelColor = SaffronDark,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = SaffronPrimary,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("QR Camera Scanner") },
                modifier = Modifier.testTag("tab_qr_camera"),
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(strings.manualAttendance) },
                modifier = Modifier.testTag("tab_manual_attendance"),
            )
        }

        if (selectedTab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasCameraPermission) {
                    SaffronCard(padding = PaddingValues(24.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SaffronPrimary, modifier = Modifier.size(48.dp))
                            Text("Camera Permission Required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Allow camera access to scan Student QR Identity Cards using CameraX and Google ML Kit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                                modifier = Modifier.testTag("grant_camera_permission_btn"),
                            ) {
                                Text("Enable Camera Scanner")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.Black),
                    ) {
                        CameraXQrPreview(
                            torchEnabled = torchEnabled,
                            isScanningPaused = scanOutcome !is QrScanOutcome.Idle,
                            onQrCodeScanned = { raw ->
                                val now = System.currentTimeMillis()
                                if (now - lastScanTime > 1800L && scanOutcome is QrScanOutcome.Idle) {
                                    lastScanTime = now
                                    onQrDetected(raw, selectedClassId, checkNetworkOnline(context))
                                }
                            },
                        )

                        // Saffron corner bracket viewfinder overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxSize = size.minDimension * 0.62f
                            val left = (size.width - boxSize) / 2f
                            val top = (size.height - boxSize) / 2f
                            val corner = 38.dp.toPx()
                            val stroke = 5.dp.toPx()
                            val color = SaffronPrimary

                            // Top-left
                            drawLine(color, Offset(left, top), Offset(left + corner, top), stroke, StrokeCap.Round)
                            drawLine(color, Offset(left, top), Offset(left, top + corner), stroke, StrokeCap.Round)
                            // Top-right
                            drawLine(color, Offset(left + boxSize, top), Offset(left + boxSize - corner, top), stroke, StrokeCap.Round)
                            drawLine(color, Offset(left + boxSize, top), Offset(left + boxSize, top + corner), stroke, StrokeCap.Round)
                            // Bottom-left
                            drawLine(color, Offset(left, top + boxSize), Offset(left + corner, top + boxSize), stroke, StrokeCap.Round)
                            drawLine(color, Offset(left, top + boxSize), Offset(left, top + boxSize - corner), stroke, StrokeCap.Round)
                            // Bottom-right
                            drawLine(color, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - corner, top + boxSize), stroke, StrokeCap.Round)
                            drawLine(color, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - corner), stroke, StrokeCap.Round)
                        }

                        // Torch toggle button
                        IconButton(
                            onClick = { torchEnabled = !torchEnabled },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .testTag("torch_toggle_btn"),
                        ) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Toggle Flash",
                                tint = PureWhite,
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                        ) {
                            Text(
                                text = "Align Student QR Card inside the Saffron Frame",
                                color = PureWhite,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // Scan Outcome Card
                ScanOutcomePanel(
                    outcome = scanOutcome,
                    onScanNext = onResetOutcome,
                )
            }
        } else {
            // Manual Attendance List for Authorized Teachers / Admins
            val classStudents = remember(students, selectedClassId, actor) {
                students.filter { stu ->
                    stu.statusEnum == StudentStatus.ACTIVE &&
                        (selectedClassId == null || stu.classId == selectedClassId) &&
                        (actor.roleEnum != UserRole.TEACHER || stu.classId in actor.assignedClassIds)
                }
            }
            val attendanceByStudent = remember(todayAttendance) {
                todayAttendance.associateBy { it.studentId }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (classStudents.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.QrCodeScanner,
                            title = "No Eligible Students",
                            subtitle = "Select an assigned class with active students to record manual attendance.",
                        )
                    }
                } else {
                    items(classStudents, key = { it.id }) { stu ->
                        val existing = attendanceByStudent[stu.id]
                        SaffronCard(padding = PaddingValues(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AvatarBadge(name = stu.studentName, photoUrl = stu.photoUrl, size = 44.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stu.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("${stu.className}-${stu.section} • Roll ${stu.rollNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (existing != null) {
                                    AttendanceStatusBadge(existing.statusEnum)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    AttendanceStatus.PRESENT to "P",
                                    AttendanceStatus.ABSENT to "A",
                                    AttendanceStatus.LATE to "L",
                                    AttendanceStatus.EXCUSED to "E",
                                ).forEach { (st, code) ->
                                    val isChosen = existing?.statusEnum == st
                                    if (isChosen) {
                                        Button(
                                            onClick = { onManualMark(stu, st) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                                            contentPadding = PaddingValues(vertical = 4.dp),
                                            modifier = Modifier.weight(1f),
                                        ) { Text(code) }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onManualMark(stu, st) },
                                            border = BorderStroke(1.dp, SaffronPrimary.copy(alpha = 0.5f)),
                                            contentPadding = PaddingValues(vertical = 4.dp),
                                            modifier = Modifier.weight(1f),
                                        ) { Text(code, color = SaffronDark) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanOutcomePanel(
    outcome: QrScanOutcome,
    onScanNext: () -> Unit,
) {
    val strings = LocalAppStrings.current
    when (outcome) {
        QrScanOutcome.Idle -> {
            SaffronCard(padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = SaffronPrimary)
                    Text(
                        "Ready to scan. Point camera at a valid school student QR ID card.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        QrScanOutcome.Processing -> {
            SaffronCard(padding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = SaffronPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    Text("Validating cryptographic QR token & recording attendance...")
                }
            }
        }
        is QrScanOutcome.Recorded -> {
            SaffronCard(padding = PaddingValues(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AvatarBadge(name = outcome.student.studentName, photoUrl = outcome.student.photoUrl, size = 56.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Text(
                                if (outcome.offlineQueued) strings.offlineQueued else strings.attendanceRecorded,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Text(outcome.student.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "ID: ${outcome.student.studentIdCode} • ${outcome.student.className}-${outcome.student.section} • Roll ${outcome.student.rollNumber}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text("Time: ${outcome.time} • Status: ${outcome.status.name}", style = MaterialTheme.typography.labelSmall, color = SaffronDark)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onScanNext,
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("scan_next_student_btn"),
                ) { Text("Scan Next Student") }
            }
        }
        is QrScanOutcome.AlreadyPresent -> {
            Surface(
                color = WarningLight,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, WarningAmber),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarBadge(name = outcome.student.studentName, photoUrl = outcome.student.photoUrl, size = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                Text(strings.alreadyPresent, color = SaffronDark, fontWeight = FontWeight.Bold)
                            }
                            Text(outcome.student.studentName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${outcome.student.className}-${outcome.student.section} • Marked ${outcome.status.name} at ${outcome.existingTime}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Button(
                        onClick = onScanNext,
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Scan Next Student") }
                }
            }
        }
        is QrScanOutcome.Rejected -> {
            Surface(
                color = ErrorLight,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorRed),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                        Text(outcome.reason, color = ErrorRed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    OutlinedButton(
                        onClick = onScanNext,
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Try Again", color = ErrorRed) }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraXQrPreview(
    torchEnabled: Boolean,
    isScanningPaused: Boolean,
    onQrCodeScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(torchEnabled, cameraRef) {
        cameraRef?.cameraControl?.enableTorch(torchEnabled)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !isScanningPaused) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val rawValue = barcodes.firstOrNull()?.rawValue
                                            if (!rawValue.isNullOrBlank()) {
                                                onQrCodeScanned(rawValue)
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }
                    cameraProvider.unbindAll()
                    cameraRef = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer,
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "CameraX bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}
