package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.onGoogleSignInClicked
import com.example.data.model.School
import com.example.data.model.SchoolStatus
import com.example.data.model.TeacherInvite
import com.example.ui.components.BrandLogoImage
import com.example.ui.components.LanguagePillSwitcher
import com.example.ui.components.SaffronCard
import com.example.ui.components.SaffronSubScreenBar
import com.example.ui.components.SchoolStatusBadge
import com.example.ui.theme.ErrorLight
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmSurface
import com.example.util.AppLanguage
import com.example.util.LocalAppStrings
import kotlinx.coroutines.delay

enum class UnauthRoute {
    SPLASH,
    WELCOME,
    LOGIN,
    FORGOT_PASSWORD_HELP,
}

@Composable
fun UnauthenticatedFlow(
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSignedIn: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var route by remember { mutableStateOf(UnauthRoute.SPLASH) }
    var selectedPortal by remember { mutableStateOf("SCHOOL_ADMIN") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800L)
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onSignedIn,
            onUnauthenticated = { route = UnauthRoute.WELCOME },
            scope = scope,
        )
    }

    when (route) {
        UnauthRoute.SPLASH -> SplashScreen()
        UnauthRoute.WELCOME -> WelcomeScreen(
            language = language,
            onSelectLanguage = onSelectLanguage,
            onContinueToLogin = { route = UnauthRoute.LOGIN },
            onRegisterSchoolIntent = {
                selectedPortal = "SCHOOL_ADMIN"
                route = UnauthRoute.LOGIN
            },
        )
        UnauthRoute.LOGIN -> {
            BackHandler { route = UnauthRoute.WELCOME }
            LoginScreen(
                selectedPortal = selectedPortal,
                onSelectPortal = { selectedPortal = it },
                isLoading = isSigningIn,
                errorMessage = errorMessage,
                onBack = { route = UnauthRoute.WELCOME },
                onHelpClick = { route = UnauthRoute.FORGOT_PASSWORD_HELP },
                onGoogleSignIn = {
                    errorMessage = null
                    isSigningIn = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isSigningIn = false
                            onSignedIn()
                        },
                        onAuthError = { msg ->
                            isSigningIn = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = { isSigningIn = false },
                    )
                },
            )
        }
        UnauthRoute.FORGOT_PASSWORD_HELP -> {
            BackHandler { route = UnauthRoute.LOGIN }
            AccountRecoveryScreen(onBack = { route = UnauthRoute.LOGIN })
        }
    }
}

@Composable
fun SplashScreen() {
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SaffronPrimary, SaffronDark))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            BrandLogoImage(size = 132.dp)
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = strings.appTagline,
                style = MaterialTheme.typography.bodyMedium,
                color = SaffronLight,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator(
                color = PureWhite,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun WelcomeScreen(
    language: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onContinueToLogin: () -> Unit,
    onRegisterSchoolIntent: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(SaffronPrimary, SaffronDark)))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SAFFRON EDTECH",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaffronLight,
                    )
                    LanguagePillSwitcher(current = language, onSelect = onSelectLanguage)
                }
                Spacer(Modifier.height(22.dp))
                BrandLogoImage(size = 116.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = strings.appTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.welcomeHeadline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SaffronLight,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = strings.welcomeSubheadline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            FeatureHighlightRow(
                icon = Icons.Default.QrCodeScanner,
                title = "CameraX + ML Kit QR Scanning",
                subtitle = "Instant attendance with duplicate protection & offline sync",
            )
            FeatureHighlightRow(
                icon = Icons.Default.Security,
                title = "Multi-School Zero-Trust Isolation",
                subtitle = "Strict Firestore isolation for Super Admin, Schools & Teachers",
            )
            FeatureHighlightRow(
                icon = Icons.Default.School,
                title = "Printable QR ID Cards & Reports",
                subtitle = "Batch class QR PDFs and CSV / Excel / PDF attendance exports",
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onContinueToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.continueToLogin, style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = onRegisterSchoolIntent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("welcome_register_school_button"),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SaffronPrimary),
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = SaffronPrimary)
                Spacer(Modifier.width(8.dp))
                Text(strings.registerSchool, color = SaffronPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    SaffronCard(padding = PaddingValues(14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SaffronLight,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = SaffronPrimary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LoginScreen(
    selectedPortal: String,
    onSelectPortal: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onHelpClick: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaffronSubScreenBar(title = strings.continueToLogin, onBack = onBack)

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrandLogoImage(size = 92.dp)
            Text(
                text = strings.appTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Select your portal and authenticate securely with Google Sign-In. Role permissions are enforced server-side.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "SUPER_ADMIN" to strings.superAdminPortal,
                    "SCHOOL_ADMIN" to strings.schoolAdminPortal,
                    "TEACHER" to strings.teacherPortal,
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedPortal == key,
                        onClick = { onSelectPortal(key) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("portal_${key.lowercase()}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SaffronLight,
                            selectedLabelColor = SaffronDark,
                        ),
                    )
                }
            }

            SaffronCard {
                val portalNote = when (selectedPortal) {
                    "SUPER_ADMIN" -> "Super Admin Portal: Verifies permanent Firebase UID & verified administrator identity (Ananta Barman)."
                    "TEACHER" -> "Teacher Portal: Sign in with the Google email added by your School Admin to unlock assigned class QR scanning."
                    else -> "School Admin Portal: Manage students, teachers, classes, QR cards, and reports — or register a new school."
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when (selectedPortal) {
                            "SUPER_ADMIN" -> Icons.Default.AdminPanelSettings
                            "TEACHER" -> Icons.Default.QrCodeScanner
                            else -> Icons.Default.School
                        },
                        contentDescription = null,
                        tint = SaffronPrimary,
                    )
                    Text(
                        text = portalNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = ErrorLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = errorMessage,
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Button(
                onClick = onGoogleSignIn,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = PureWhite,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(strings.signInWithGoogle, style = MaterialTheme.typography.labelLarge)
                }
            }

            TextButton(
                onClick = onHelpClick,
                modifier = Modifier.testTag("account_recovery_button"),
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = SaffronDark)
                Spacer(Modifier.width(6.dp))
                Text(strings.accountRecoveryHelp, color = SaffronDark)
            }
        }
    }
}

@Composable
fun AccountRecoveryScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        SaffronSubScreenBar(title = "Account Recovery & Help", onBack = onBack)
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SaffronCard {
                Text("Google Account Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This app uses secure Google Sign-In via Android Credential Manager. No separate app password is stored or exposed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SaffronCard {
                Text("Locked Out or Changing School Admin?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• Recover your Google Account at accounts.google.com/signin/recovery.\n" +
                        "• Teachers must sign in using the exact Gmail/Google email registered by their School Admin.\n" +
                        "• School Admins needing access reset or school reactivation can contact the Platform Super Admin (Ananta Barman).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
            ) {
                Text("Return to Sign In")
            }
        }
    }
}

/**
 * Shown to newly signed-in users who are not yet linked to a school or Super Admin role.
 * Supports: (1) Accepting a pending verified Teacher Invite, or (2) Registering a New School.
 */
@Composable
fun OnboardingAndSchoolRegistrationScreen(
    userEmail: String,
    userName: String,
    pendingInvites: List<TeacherInvite>,
    busy: Boolean,
    feedbackMessage: String?,
    onAcceptInvite: (TeacherInvite) -> Unit,
    onRegisterSchool: (
        schoolName: String,
        schoolCode: String,
        headmasterName: String,
        email: String,
        phone: String,
        address: String,
        villageOrTown: String,
        district: String,
        state: String,
        pinCode: String,
    ) -> Unit,
    onSignOut: () -> Unit,
) {
    val strings = LocalAppStrings.current
    var showRegForm by remember { mutableStateOf(pendingInvites.isEmpty()) }

    var schoolName by remember { mutableStateOf("") }
    var schoolCode by remember { mutableStateOf("") }
    var headmasterName by remember { mutableStateOf(userName) }
    var schoolEmail by remember { mutableStateOf(userEmail) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var villageOrTown by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("West Bengal") }
    var pinCode by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandLogoImage(size = 44.dp)
                    Column {
                        Text("Account Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                TextButton(onClick = onSignOut, modifier = Modifier.testTag("onboarding_sign_out")) {
                    Text(strings.signOut, color = ErrorRed)
                }
            }

            if (pendingInvites.isNotEmpty()) {
                SaffronCard {
                    Text(
                        "Teacher Invitation Found!",
                        style = MaterialTheme.typography.titleMedium,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    pendingInvites.forEach { invite ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("School: ${invite.schoolName}", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Subjects: ${invite.assignedSubjects.joinToString().ifBlank { "General" }} • Classes: ${invite.assignedClassIds.size}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { onAcceptInvite(invite) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("accept_teacher_invite_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Join ${invite.schoolName} as Teacher")
                            }
                        }
                    }
                }
            }

            if (!showRegForm) {
                OutlinedButton(
                    onClick = { showRegForm = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, SaffronPrimary),
                ) {
                    Text("Or Register a New School Instead", color = SaffronPrimary)
                }
            } else {
                SaffronCard {
                    Text(
                        text = strings.registerSchool,
                        style = MaterialTheme.typography.titleLarge,
                        color = SaffronDark,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "New registrations remain PENDING until approved by the Super Admin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    val fields = listOf(
                        Triple("School Name *", schoolName) { v: String -> schoolName = v },
                        Triple("School Code / UDISE Code *", schoolCode) { v: String -> schoolCode = v },
                        Triple("Headmaster / Principal Name *", headmasterName) { v: String -> headmasterName = v },
                        Triple("Official School Email *", schoolEmail) { v: String -> schoolEmail = v },
                        Triple("Contact Number *", phone) { v: String -> phone = v },
                        Triple("School Address *", address) { v: String -> address = v },
                        Triple("Village / Town *", villageOrTown) { v: String -> villageOrTown = v },
                        Triple("District *", district) { v: String -> district = v },
                        Triple("State *", state) { v: String -> state = v },
                        Triple("PIN Code *", pinCode) { v: String -> pinCode = v },
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        fields.forEach { (label, value, setter) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { setter(it); validationError = null },
                                label = { Text(label) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    val err = validationError ?: feedbackMessage
                    if (!err.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (schoolName.isBlank() || schoolCode.isBlank() || headmasterName.isBlank() ||
                                schoolEmail.isBlank() || phone.isBlank() || address.isBlank() ||
                                district.isBlank() || state.isBlank() || pinCode.isBlank()
                            ) {
                                validationError = "Please fill in all required school registration fields."
                                return@Button
                            }
                            onRegisterSchool(
                                schoolName, schoolCode, headmasterName, schoolEmail, phone,
                                address, villageOrTown, district, state, pinCode
                            )
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_school_registration_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = PureWhite, modifier = Modifier.size(22.dp))
                        } else {
                            Text("Submit School Application")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SchoolApprovalGateScreen(
    school: School?,
    onSignOut: () -> Unit,
) {
    val strings = LocalAppStrings.current
    val status = school?.statusEnum ?: SchoolStatus.PENDING
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        SaffronCard(
            modifier = Modifier.widthIn(max = 520.dp),
            padding = PaddingValues(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandLogoImage(size = 84.dp)
                Icon(
                    imageVector = Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(36.dp),
                )
                SchoolStatusBadge(status = status)
                Text(
                    text = when (status) {
                        SchoolStatus.PENDING -> strings.pendingApprovalTitle
                        SchoolStatus.REJECTED -> "Application Rejected"
                        SchoolStatus.SUSPENDED -> "School Account Suspended"
                        SchoolStatus.APPROVED -> "Approved"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = when (status) {
                        SchoolStatus.PENDING -> strings.pendingApprovalMessage
                        SchoolStatus.REJECTED -> "Reason: ${school?.rejectionReason?.ifBlank { "Please contact Super Admin." }}"
                        SchoolStatus.SUSPENDED -> "This school has been temporarily suspended by the Super Admin. ${school?.rejectionReason.orEmpty()}"
                        SchoolStatus.APPROVED -> "Loading dashboard..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (school != null) {
                    Surface(
                        color = SaffronLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(school.schoolName, fontWeight = FontWeight.Bold, color = SaffronDark)
                            Text("School ID: ${school.id}", style = MaterialTheme.typography.labelSmall)
                            Text("UDISE: ${school.schoolCode} • ${school.district}, ${school.state}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pending_sign_out_btn"),
                    border = BorderStroke(1.dp, SaffronPrimary),
                ) {
                    Text(strings.signOut, color = SaffronPrimary)
                }
            }
        }
    }
}
