package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.StudentStatus
import com.example.ui.components.AvatarBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronSearchBar
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
import com.example.util.LocalAppStrings
import com.example.util.QrSecurityManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentManagementScreen(
    school: School?,
    students: List<Student>,
    classes: List<SchoolClass>,
    canEdit: Boolean,
    onAddStudent: () -> Unit,
    onOpenProfile: (Student) -> Unit,
    onOpenQrCard: (Student) -> Unit,
    onImportCsv: (SchoolClass, String) -> Unit,
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var search by remember { mutableStateOf("") }
    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf<String?>(null) }
    var pageLimit by remember { mutableIntStateOf(25) }
    var showCsvDialog by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }

    val filtered = remember(students, search, selectedClassId, selectedSection) {
        students.filter { stu ->
            val qMatch = search.isBlank() ||
                stu.studentName.contains(search, ignoreCase = true) ||
                stu.studentIdCode.contains(search, ignoreCase = true) ||
                stu.rollNumber.contains(search, ignoreCase = true)
            val cMatch = selectedClassId == null || stu.classId == selectedClassId
            val sMatch = selectedSection == null || stu.section.equals(selectedSection, ignoreCase = true)
            qMatch && cMatch && sMatch
        }
    }

    val sectionsForSelected = remember(classes, selectedClassId) {
        classes.firstOrNull { it.id == selectedClassId }?.sections.orEmpty()
    }

    Scaffold(
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = onAddStudent,
                    containerColor = SaffronPrimary,
                    contentColor = PureWhite,
                    modifier = Modifier.testTag("add_student_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = strings.addStudent)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${strings.students} (${filtered.size})",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (canEdit && classes.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showCsvDialog = true },
                                border = BorderStroke(1.dp, SaffronPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("import_csv_btn"),
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = SaffronPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("CSV", color = SaffronPrimary)
                            }
                        }
                        if (filtered.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val classTitle = classes.firstOrNull { it.id == selectedClassId }?.className ?: "All Classes"
                                    val res = QrSecurityManager.exportBatchQrCardsPdfAndShare(context, school, filtered, classTitle)
                                    exportStatus = res.fold(
                                        onSuccess = { "Generated Batch QR PDF for ${filtered.size} students." },
                                        onFailure = { it.message ?: "Batch QR export failed." }
                                    )
                                },
                                border = BorderStroke(1.dp, SaffronDark),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("batch_qr_pdf_btn"),
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = SaffronDark, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Batch QR", color = SaffronDark)
                            }
                        }
                    }
                }
                if (!exportStatus.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(exportStatus!!, style = MaterialTheme.typography.bodySmall, color = SuccessGreen)
                }
                Spacer(Modifier.height(8.dp))
                SaffronSearchBar(
                    query = search,
                    onQueryChange = { search = it },
                    placeholder = "Search student by name, ID, or roll number...",
                )
                if (classes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedClassId == null,
                            onClick = { selectedClassId = null; selectedSection = null },
                            label = { Text("All Classes") },
                        )
                        classes.forEach { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = {
                                    selectedClassId = if (selectedClassId == cls.id) null else cls.id
                                    selectedSection = null
                                },
                                label = { Text(cls.className) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SaffronLight,
                                    selectedLabelColor = SaffronDark,
                                ),
                            )
                        }
                    }
                }
                if (sectionsForSelected.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sectionsForSelected.forEach { sec ->
                            FilterChip(
                                selected = selectedSection == sec,
                                onClick = { selectedSection = if (selectedSection == sec) null else sec },
                                label = { Text("Sec $sec") },
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Groups,
                        title = "No Students Registered",
                        subtitle = if (classes.isEmpty()) {
                            "Create at least one Class first, then add students or import via CSV."
                        } else {
                            "Tap + to add students with automatic signed QR codes."
                        },
                    )
                }
            } else {
                val visible = filtered.take(pageLimit)
                items(visible, key = { it.id }) { student ->
                    SaffronCard(
                        padding = PaddingValues(14.dp),
                        onClick = { onOpenProfile(student) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AvatarBadge(name = student.studentName, photoUrl = student.photoUrl, size = 48.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(student.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${student.className}-${student.section} • Roll ${student.rollNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SaffronDark,
                                )
                                Text(
                                    "ID: ${student.studentIdCode} • Parent: ${student.parentContact}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onOpenQrCard(student) },
                                modifier = Modifier.testTag("student_qr_${student.id}"),
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = "View Student QR", tint = SaffronPrimary)
                            }
                        }
                    }
                }
                if (filtered.size > pageLimit) {
                    item {
                        OutlinedButton(
                            onClick = { pageLimit += 25 },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, SaffronPrimary),
                        ) {
                            Text("Load More (${filtered.size - pageLimit} remaining)", color = SaffronPrimary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCsvDialog && classes.isNotEmpty()) {
        CsvImportDialog(
            classes = classes,
            onDismiss = { showCsvDialog = false },
            onImport = { cls, text ->
                onImportCsv(cls, text)
                showCsvDialog = false
            },
        )
    }
}

@Composable
private fun CsvImportDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onImport: (SchoolClass, String) -> Unit,
) {
    var selectedClass by remember { mutableStateOf(classes.first()) }
    var csvText by remember {
        mutableStateOf("Name,Father,Mother,DOB,Section,Roll,ParentPhone,Address\n")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Students via CSV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Target Class: ${selectedClass.className}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SaffronDark,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.take(4).forEach { cls ->
                        FilterChip(
                            selected = selectedClass.id == cls.id,
                            onClick = { selectedClass = cls },
                            label = { Text(cls.className) },
                        )
                    }
                }
                Text(
                    "Format: Name,Father,Mother,YYYY-MM-DD,Section,Roll,Phone,Address",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    minLines = 5,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(selectedClass, csvText) },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditStudentScreen(
    existingStudent: Student?,
    classes: List<SchoolClass>,
    onBack: () -> Unit,
    onSave: (
        studentName: String,
        fatherName: String,
        motherName: String,
        dob: String,
        gender: String,
        classId: String,
        className: String,
        section: String,
        rollNumber: String,
        address: String,
        parentContact: String,
        photoUrl: String,
        admissionDate: String,
        status: StudentStatus,
    ) -> Unit,
) {
    BackHandler(onBack = onBack)
    val strings = LocalAppStrings.current

    var studentName by remember(existingStudent) { mutableStateOf(existingStudent?.studentName.orEmpty()) }
    var fatherName by remember(existingStudent) { mutableStateOf(existingStudent?.fatherName.orEmpty()) }
    var motherName by remember(existingStudent) { mutableStateOf(existingStudent?.motherName.orEmpty()) }
    var dob by remember(existingStudent) { mutableStateOf(existingStudent?.dateOfBirth ?: "2012-01-01") }
    var gender by remember(existingStudent) { mutableStateOf(existingStudent?.gender.orEmpty()) }
    var selectedClass by remember(existingStudent, classes) {
        mutableStateOf(classes.firstOrNull { it.id == existingStudent?.classId } ?: classes.firstOrNull())
    }
    var section by remember(existingStudent, selectedClass) {
        mutableStateOf(existingStudent?.section ?: selectedClass?.sections?.firstOrNull() ?: "A")
    }
    var rollNumber by remember(existingStudent) { mutableStateOf(existingStudent?.rollNumber.orEmpty()) }
    var address by remember(existingStudent) { mutableStateOf(existingStudent?.address.orEmpty()) }
    var parentContact by remember(existingStudent) { mutableStateOf(existingStudent?.parentContact.orEmpty()) }
    var photoUrl by remember(existingStudent) { mutableStateOf(existingStudent?.photoUrl.orEmpty()) }
    var admissionDate by remember(existingStudent) { mutableStateOf(existingStudent?.admissionDate.orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Play Policy compliant zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUrl = uri.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SaffronSubScreenBar(
            title = if (existingStudent == null) strings.addStudent else strings.editStudent,
            subtitle = "Auto-generates signed opaque QR code",
            onBack = onBack,
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarBadge(name = studentName.ifBlank { "Student" }, photoUrl = photoUrl, size = 68.dp)
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    border = BorderStroke(1.dp, SaffronPrimary),
                    modifier = Modifier.testTag("pick_student_photo_btn"),
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SaffronPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Student Photo", color = SaffronPrimary)
                }
            }

            if (classes.isEmpty()) {
                SaffronCard {
                    Text(
                        "Please create a Class in Class Management before adding students.",
                        color = ErrorRed,
                    )
                }
            } else {
                Text("Select Class *", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { cls ->
                        FilterChip(
                            selected = selectedClass?.id == cls.id,
                            onClick = {
                                selectedClass = cls
                                section = cls.sections.firstOrNull() ?: "A"
                            },
                            label = { Text(cls.className) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SaffronLight,
                                selectedLabelColor = SaffronDark,
                            ),
                        )
                    }
                }
                val secs = selectedClass?.sections.orEmpty()
                if (secs.isNotEmpty()) {
                    Text("Section *", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        secs.forEach { sec ->
                            FilterChip(
                                selected = section == sec,
                                onClick = { section = sec },
                                label = { Text("Section $sec") },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(studentName, { studentName = it; errorText = null }, label = { Text("Student Name *") }, modifier = Modifier.fillMaxWidth().testTag("student_name_input"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rollNumber, { rollNumber = it; errorText = null }, label = { Text("Roll Number *") }, modifier = Modifier.weight(1f).testTag("student_roll_input"))
                OutlinedTextField(gender, { gender = it }, label = { Text("Gender (Optional)") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(fatherName, { fatherName = it }, label = { Text("Father's Name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(motherName, { motherName = it }, label = { Text("Mother's Name *") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dob, { dob = it }, label = { Text("Date of Birth (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(admissionDate, { admissionDate = it }, label = { Text("Admission Date") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(parentContact, { parentContact = it }, label = { Text("Parent Contact Number *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(address, { address = it }, label = { Text("Residential Address") }, modifier = Modifier.fillMaxWidth())

            if (!errorText.isNullOrBlank()) {
                Text(errorText!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val cls = selectedClass
                    if (studentName.isBlank() || rollNumber.isBlank() || cls == null) {
                        errorText = "Student Name, Class, and Roll Number are required."
                        return@Button
                    }
                    onSave(
                        studentName, fatherName, motherName, dob, gender,
                        cls.id, cls.className, section, rollNumber, address,
                        parentContact, photoUrl, admissionDate,
                        existingStudent?.statusEnum ?: StudentStatus.ACTIVE,
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_student_btn"),
            ) {
                Text(if (existingStudent == null) "Save Student & Generate QR" else "Update Student")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun StudentProfileScreen(
    student: Student,
    attendanceRecords: List<AttendanceRecord>,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onViewQrCard: () -> Unit,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val studentHistory = remember(attendanceRecords, student.id) {
        attendanceRecords.filter { it.studentId == student.id }
    }
    val presentCount = studentHistory.count { it.statusEnum == AttendanceStatus.PRESENT || it.statusEnum == AttendanceStatus.LATE }
    val pct = if (studentHistory.isEmpty()) 100 else (presentCount * 100 / studentHistory.size)
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SaffronSubScreenBar(
            title = student.studentName,
            subtitle = "ID: ${student.studentIdCode}",
            onBack = onBack,
            trailing = {
                if (canEdit) {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_student_btn")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Student", tint = SaffronPrimary)
                    }
                }
            },
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SaffronCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AvatarBadge(name = student.studentName, photoUrl = student.photoUrl, size = 68.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(student.studentName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${student.className} • Section ${student.section} • Roll ${student.rollNumber}", color = SaffronDark)
                        Spacer(Modifier.height(4.dp))
                        StatusPill(
                            text = student.status,
                            backgroundColor = if (student.statusEnum == StudentStatus.ACTIVE) SuccessLight else ErrorLight,
                            contentColor = if (student.statusEnum == StudentStatus.ACTIVE) SuccessGreen else ErrorRed,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onViewQrCard,
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("open_qr_card_btn"),
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View / Download Printable QR Card")
                }
            }

            SaffronCard {
                Text("Student Particulars", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                DetailRow("Father's Name", student.fatherName)
                DetailRow("Mother's Name", student.motherName)
                DetailRow("Date of Birth", student.dateOfBirth)
                if (student.gender.isNotBlank()) DetailRow("Gender", student.gender)
                DetailRow("Parent Contact", student.parentContact)
                DetailRow("Address", student.address)
                DetailRow("Admission Date", student.admissionDate)
                DetailRow("QR Token Version", "v${student.qrVersion} (${student.qrTokenId})")
            }

            SaffronCard {
                Text("Attendance Summary ($pct%)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Total Recorded Days: ${studentHistory.size} • Present/Late: $presentCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onToggleActive,
                        border = BorderStroke(1.dp, SaffronDark),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (student.statusEnum == StudentStatus.ACTIVE) "Deactivate" else "Activate",
                            color = SaffronDark,
                        )
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete", color = ErrorRed)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Student?") },
            text = { Text("Remove ${student.studentName} permanently from the school roster?") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StudentQrCardScreen(
    school: School?,
    student: Student,
    canManageQr: Boolean,
    onBack: () -> Unit,
    onRegenerateQr: () -> Unit,
    onRevokeQr: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var shareMessage by remember { mutableStateOf<String?>(null) }

    val cardBitmap: Bitmap = remember(school?.schoolName, student.id, student.qrTokenId, student.qrVersion) {
        QrSecurityManager.renderStudentQrCardBitmap(school, student)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SaffronSubScreenBar(
            title = "Student QR Identity Card",
            subtitle = "${student.studentName} • Token v${student.qrVersion}",
            onBack = onBack,
        )

        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SaffronCard(padding = PaddingValues(12.dp)) {
                Image(
                    bitmap = cardBitmap.asImageBitmap(),
                    contentDescription = "Printable QR Card for ${student.studentName}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = "Privacy Protected: QR payload contains only an HMAC-signed opaque token linked to School ID and Student ID — zero PII exposed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!shareMessage.isNullOrBlank()) {
                Text(shareMessage!!, color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val res = QrSecurityManager.exportQrCardPngAndShare(context, school, student)
                    shareMessage = res.fold(
                        onSuccess = { "QR card PNG exported and ready to print/share." },
                        onFailure = { it.message ?: "Export failed." }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("download_qr_card_btn"),
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.downloadQrCard)
            }

            if (canManageQr) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRegenerateQr,
                        border = BorderStroke(1.dp, SaffronPrimary),
                        modifier = Modifier.weight(1f).testTag("regenerate_qr_btn"),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = SaffronPrimary)
                        Spacer(Modifier.width(6.dp))
                        Text(strings.regenerateQr, color = SaffronPrimary)
                    }
                    OutlinedButton(
                        onClick = onRevokeQr,
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier.weight(1f).testTag("revoke_qr_btn"),
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = ErrorRed)
                        Spacer(Modifier.width(6.dp))
                        Text(strings.revokeQr, color = ErrorRed)
                    }
                }
            }
        }
    }
}
