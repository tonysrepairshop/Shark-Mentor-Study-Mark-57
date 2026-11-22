package com.example.sharkmentor.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sharkmentor.core.badges.SharkBadgeCatalog
import com.example.sharkmentor.core.badges.SharkBadgeShowcaseRow
import com.example.sharkmentor.core.util.AvatarHelper
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.core.util.SharkAvatar
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.data.repository.CoachCodeRepository
import com.example.sharkmentor.service.sync.SyncWorker
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.sharkmentor.ui.screens.leaguechat.BadgeShowcaseSelectorSheet
import com.example.sharkmentor.ui.screens.profile.ProfileBadgesTab
import com.example.sharkmentor.ui.screens.profile.ProfileViewModel
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.SetOptions
import java.util.Locale

private val DEFAULT_GRADE_OPTIONS = listOf(
    "8. Sınıf",
    "9. Sınıf",
    "10. Sınıf",
    "11. Sınıf",
    "12. Sınıf",
    "Mezun",
    "Üniversite"
)

private val DEFAULT_EXAM_OPTIONS = listOf(
    "YKS 2026",
    "YKS 2027",
    "YKS Sayısal",
    "YKS Eşit Ağırlık",
    "YKS Sözel",
    "LGS 2026",
    "LGS 2027",
    "KPSS",
    "DGS",
    "ALES"
)

private fun isMeaningfulProfileName(candidate: String?): Boolean {
    val clean = SyncWorker.sanitizeTurkishText(candidate).trim()
    if (clean.length < 2) return false
    val lower = clean.lowercase(Locale("tr"))
    return lower != "öğrenci" &&
        lower != "ogrenci" &&
        lower != "kayıtlı öğrenci" &&
        lower != "kayitli ogrenci" &&
        lower != "student" &&
        lower != "null"
}

private fun saveProfileNameLocallyAndCloud(
    context: Context,
    uid: String?,
    isGuest: Boolean,
    rawName: String
) {
    val clean = SyncWorker.sanitizeTurkishText(rawName.trim())
    if (!isMeaningfulProfileName(clean)) return

    UserPreferences.getInstance(context).updateStudentName(clean)

    if (!isGuest && !uid.isNullOrBlank()) {
        runCatching {
            GoogleServicesHelper.getCurrentUser(context = context)
                ?.updateProfile(userProfileChangeRequest { displayName = clean })
        }
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return
        val namePayload = mapOf("name" to clean, "displayName" to clean)
        db.collection("students").document(uid)
            .set(namePayload, SetOptions.merge())
        db.collection("users").document(uid)
            .set(namePayload, SetOptions.merge())
        db.collection("institutions").document("GLOBAL_INDIVIDUAL")
            .collection("weeklyLeague").document(uid)
            .set(mapOf("displayName" to clean), SetOptions.merge())
        val friendCode = UserPreferences.getInstance(context).friendCode
        if (!friendCode.isNullOrBlank()) {
            db.collection("friend_codes").document(friendCode)
                .set(mapOf("displayName" to clean), SetOptions.merge())
        }
    }
}

private fun saveStudentGradeLocallyAndCloud(
    context: Context,
    uid: String?,
    isGuest: Boolean,
    rawGrade: String
) {
    val clean = SyncWorker.sanitizeTurkishText(rawGrade.trim())
    if (clean.isBlank()) return

    UserPreferences.getInstance(context).updateStudentGrade(clean)

    if (!isGuest && !uid.isNullOrBlank()) {
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return
        val payload = mapOf("grade" to clean)
        db.collection("students").document(uid).set(payload, SetOptions.merge())
        db.collection("users").document(uid).set(payload, SetOptions.merge())
    }
}

private fun saveStudentTargetExamLocallyAndCloud(
    context: Context,
    uid: String?,
    isGuest: Boolean,
    rawExam: String
) {
    val clean = SyncWorker.sanitizeTurkishText(rawExam.trim())
    if (clean.isBlank()) return

    val userPrefs = UserPreferences.getInstance(context)
    userPrefs.updateStudentTargetExam(clean)
    com.example.sharkmentor.data.repository.CountdownRepository.getInstance(context).updateTitle(clean)

    if (!isGuest && !uid.isNullOrBlank()) {
        val db = GoogleServicesHelper.getFirebaseFirestore(context) ?: return
        db.collection("students").document(uid).set(
            mapOf(
                "targetExam" to clean,
                "targetExamTitle" to clean
            ),
            SetOptions.merge()
        )
        db.collection("users").document(uid).set(
            mapOf("targetExam" to clean),
            SetOptions.merge()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = viewModel(),
    onBackClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onLoginClick: () -> Unit = onLogoutClick
) {
    val context = LocalContext.current
    val userPrefs = remember(context) { UserPreferences.getInstance(context) }
    val user = GoogleServicesHelper.getCurrentUser(context = context)
    val uid = user?.uid ?: "ID Alınamadı"
    val isGuest = user == null || user.isAnonymous

    val syncedNamePref by userPrefs.studentNameFlow.collectAsStateWithLifecycle()
    val syncedGradePref by userPrefs.studentGradeFlow.collectAsStateWithLifecycle()
    val syncedTargetExamPref by userPrefs.studentTargetExamFlow.collectAsStateWithLifecycle()

    var studentName by remember {
        val initial = userPrefs.studentName?.takeIf { isMeaningfulProfileName(it) }
            ?: user?.displayName?.takeIf { isMeaningfulProfileName(it) }
            ?: user?.email?.substringBefore("@")?.takeIf { isMeaningfulProfileName(it) }
            ?: ""
        mutableStateOf(initial)
    }
    var studentEmail by remember { mutableStateOf(user?.email ?: "") }
    var studentGrade by remember {
        mutableStateOf(
            userPrefs.studentGrade?.takeIf { it.isNotBlank() } ?: "12. Sınıf"
        )
    }
    var studentTargetExam by remember {
        val fallbackCountdown = context.getSharedPreferences("sharkmentor_prefs", Context.MODE_PRIVATE)
            .getString("target_exam_title", null)?.takeIf { it.isNotBlank() }
        mutableStateOf(
            userPrefs.studentTargetExam?.takeIf { it.isNotBlank() }
                ?: fallbackCountdown
                ?: "YKS 2027"
        )
    }

    LaunchedEffect(syncedNamePref) {
        val candidate = syncedNamePref?.takeIf { isMeaningfulProfileName(it) }
        if (!candidate.isNullOrBlank()) {
            studentName = SyncWorker.sanitizeTurkishText(candidate)
        }
    }
    LaunchedEffect(syncedGradePref) {
        val candidate = syncedGradePref?.trim()
        if (!candidate.isNullOrBlank()) {
            studentGrade = SyncWorker.sanitizeTurkishText(candidate)
        }
    }
    LaunchedEffect(syncedTargetExamPref) {
        val candidate = syncedTargetExamPref?.trim()
        if (!candidate.isNullOrBlank()) {
            studentTargetExam = SyncWorker.sanitizeTurkishText(candidate)
        }
    }

    var coachName by remember { mutableStateOf("") }
    var institutionName by remember { mutableStateOf("") }
    var hasCoach by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var inputCode by remember { mutableStateOf("") }
    var inputStudentName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var linkError by remember { mutableStateOf("") }
    var showUnlinkConfirm by remember { mutableStateOf(false) }
    var showBookingSheet by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    var showBadgeShowcaseSheet by remember { mutableStateOf(false) }
    var showNameEditDialog by remember { mutableStateOf(false) }
    var showGradeEditDialog by remember { mutableStateOf(false) }
    var showTargetExamEditDialog by remember { mutableStateOf(false) }

    var unlockedBadgeIds by remember { mutableStateOf(SharkBadgeCatalog.getUnlockedBadgeIds(context)) }
    var showcaseBadgeIds by remember { mutableStateOf(SharkBadgeCatalog.getShowcaseBadgeIds(context)) }
    var badgeProgress by remember { mutableStateOf(SharkBadgeCatalog.getBadgeProgressCounts(context)) }
    var mentorPoints by remember { mutableStateOf(SharkBadgeCatalog.getMentorPoints(context)) }

    val activeMeetings by viewModel.studentMeetings.collectAsStateWithLifecycle()
    val avatarState by viewModel.avatarState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.selectGalleryAvatar(uri)
            showAvatarPickerSheet = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bookingMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.avatarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uid) {
        unlockedBadgeIds = SharkBadgeCatalog.getUnlockedBadgeIds(context)
        showcaseBadgeIds = SharkBadgeCatalog.getShowcaseBadgeIds(context)
        badgeProgress = SharkBadgeCatalog.getBadgeProgressCounts(context)
        mentorPoints = SharkBadgeCatalog.getMentorPoints(context)

        if (user != null && !isGuest) {
            val db = GoogleServicesHelper.getFirebaseFirestore(context)
            db?.collection("students")?.document(uid)?.get()
                ?.addOnSuccessListener { snapshot ->
                    val nameInDb = listOf("name", "displayName", "fullName", "username")
                        .firstNotNullOfOrNull { field ->
                            snapshot.getString(field)?.takeIf { isMeaningfulProfileName(it) }
                        }
                    if (!nameInDb.isNullOrBlank()) {
                        studentName = SyncWorker.sanitizeTurkishText(nameInDb)
                        saveProfileNameLocallyAndCloud(context, uid, isGuest = false, rawName = studentName)
                    } else if (isMeaningfulProfileName(studentName)) {
                        saveProfileNameLocallyAndCloud(context, uid, isGuest = false, rawName = studentName)
                    }

                    val remotePoints = snapshot.getLong("mentorPoints")?.toInt() ?: 0
                    val mergedPoints = maxOf(remotePoints, SharkBadgeCatalog.getMentorPoints(context))
                    mentorPoints = SharkBadgeCatalog.saveMentorPoints(context, mergedPoints)

                    val remoteProgress = SharkBadgeCatalog.parseRemoteProgressMap(snapshot.get("badgeProgress"))
                    badgeProgress = SharkBadgeCatalog.mergeBadgeProgress(context, remoteProgress)

                    val remoteUnlocked = (snapshot.get("unlockedBadges") as? List<*>)
                        ?.mapNotNull { it?.toString()?.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    unlockedBadgeIds = SharkBadgeCatalog.mergeUnlockedBadges(context, remoteUnlocked)

                    val remoteShowcase = (snapshot.get("showcaseBadges") as? List<*>)
                        ?.mapNotNull { it?.toString()?.trim() }
                        ?.filter { it.isNotBlank() }
                        ?.take(SharkBadgeCatalog.MAX_SHOWCASE_BADGES)
                        .orEmpty()
                    if (remoteShowcase.isNotEmpty() && SharkBadgeCatalog.getShowcaseBadgeIds(context).isEmpty()) {
                        showcaseBadgeIds = SharkBadgeCatalog.saveShowcaseBadgeIds(context, remoteShowcase)
                    } else {
                        showcaseBadgeIds = SharkBadgeCatalog.getShowcaseBadgeIds(context)
                    }

                    val weekKey = SharkBadgeCatalog.currentIsoWeekKey()
                    val remoteBonus = SharkBadgeCatalog.parseWeeklyBonusForWeek(snapshot.get("weeklyBonusByWeek"), weekKey)
                    val localBonus = SharkBadgeCatalog.getWeeklyBonusSeconds(context, weekKey)
                    if (remoteBonus > localBonus) {
                        SharkBadgeCatalog.saveWeeklyBonusSeconds(context, remoteBonus, weekKey)
                    }

                    val hasAvatarFields = snapshot.contains("avatarBase64") || snapshot.contains("avatarPresetId")
                    viewModel.syncAvatarFromCloud(
                        remoteBase64 = snapshot.getString("avatarBase64"),
                        remotePresetId = snapshot.getString("avatarPresetId"),
                        hasCloudFields = hasAvatarFields
                    )

                    val emailInDb = snapshot.getString("email")
                    if (!emailInDb.isNullOrBlank()) studentEmail = emailInDb

                    val gradeInDb = listOf("grade", "gradeLevel", "sinif")
                        .firstNotNullOfOrNull { f -> snapshot.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                    if (!gradeInDb.isNullOrBlank()) {
                        studentGrade = SyncWorker.sanitizeTurkishText(gradeInDb)
                        userPrefs.updateStudentGrade(studentGrade)
                    }

                    val examInDb = listOf("targetExam", "targetExamTitle")
                        .firstNotNullOfOrNull { f -> snapshot.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                    if (!examInDb.isNullOrBlank()) {
                        studentTargetExam = SyncWorker.sanitizeTurkishText(examInDb)
                        userPrefs.updateStudentTargetExam(studentTargetExam)
                    }

                    // Fallback to users/{uid} if grade or targetExam was stored on the registration document
                    if (gradeInDb.isNullOrBlank() || examInDb.isNullOrBlank()) {
                        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                            if (userDoc != null && userDoc.exists()) {
                                if (gradeInDb.isNullOrBlank()) {
                                    val userGrade = listOf("grade", "gradeLevel", "sinif")
                                        .firstNotNullOfOrNull { f -> userDoc.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                                    if (!userGrade.isNullOrBlank()) {
                                        studentGrade = SyncWorker.sanitizeTurkishText(userGrade)
                                        saveStudentGradeLocallyAndCloud(context, uid, isGuest = false, rawGrade = studentGrade)
                                    } else if (!userPrefs.studentGrade.isNullOrBlank()) {
                                        saveStudentGradeLocallyAndCloud(context, uid, isGuest = false, rawGrade = userPrefs.studentGrade!!)
                                    }
                                }
                                if (examInDb.isNullOrBlank()) {
                                    val userExam = listOf("targetExam", "targetExamTitle")
                                        .firstNotNullOfOrNull { f -> userDoc.getString(f)?.trim()?.takeIf { it.isNotBlank() } }
                                    if (!userExam.isNullOrBlank()) {
                                        studentTargetExam = SyncWorker.sanitizeTurkishText(userExam)
                                        saveStudentTargetExamLocallyAndCloud(context, uid, isGuest = false, rawExam = studentTargetExam)
                                    } else if (!userPrefs.studentTargetExam.isNullOrBlank()) {
                                        saveStudentTargetExamLocallyAndCloud(context, uid, isGuest = false, rawExam = userPrefs.studentTargetExam!!)
                                    }
                                }
                            } else {
                                if (gradeInDb.isNullOrBlank() && !userPrefs.studentGrade.isNullOrBlank()) {
                                    saveStudentGradeLocallyAndCloud(context, uid, isGuest = false, rawGrade = userPrefs.studentGrade!!)
                                }
                                if (examInDb.isNullOrBlank() && !userPrefs.studentTargetExam.isNullOrBlank()) {
                                    saveStudentTargetExamLocallyAndCloud(context, uid, isGuest = false, rawExam = userPrefs.studentTargetExam!!)
                                }
                            }
                        }
                    }

                    val instInDb = snapshot.getString("institutionName")
                    val coachCodeInDb = snapshot.getString("coachCode") ?: snapshot.getString("institutionCode")

                    if (!instInDb.isNullOrBlank() || !coachCodeInDb.isNullOrBlank()) {
                        institutionName = instInDb ?: "Bağlı Kurum"
                        hasCoach = true
                        SessionManager.setLinkedToInstitution(context, true)
                        SessionManager.setUserMode(context, "campus")
                    }

                    val coachId = snapshot.getString("coachId")
                    if (!coachId.isNullOrBlank()) {
                        db.collection("coaches").document(coachId).get()
                            .addOnSuccessListener { cDoc ->
                                coachName = cDoc.getString("coachName") ?: "Koç"
                                if (institutionName.isBlank() || institutionName == "Bağlı Kurum") {
                                    institutionName = cDoc.getString("institutionName") ?: "Kurum"
                                }
                                hasCoach = true
                                SessionManager.setLinkedToInstitution(context, true)
                                SessionManager.setUserMode(context, "campus")
                                isLoading = false
                            }
                            .addOnFailureListener { isLoading = false }
                    } else {
                        isLoading = false
                    }
                }
                ?.addOnFailureListener { isLoading = false }
        } else {
            isLoading = false
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    expandedHeight = 50.dp,
                    title = { Text("Profil ve Hesap") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Text(
                                text = "Profil & Hesap",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Text(
                                text = "Rozetler (${unlockedBadgeIds.size}/${SharkBadgeCatalog.ALL_BADGES.size})",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val displayEmail = if (isGuest) {
            "Misafir Oturumu (E-posta Bağlı Değil)"
        } else {
            studentEmail.ifBlank { "E-posta Belirtilmemiş" }
        }
        val effectiveDisplayName = if (isGuest) "Misafir Öğrenci" else studentName.ifBlank { "Adını Belirle" }

        if (selectedTabIndex == 1) {
            ProfileBadgesTab(
                unlockedBadgeIds = unlockedBadgeIds,
                showcaseBadgeIds = showcaseBadgeIds,
                badgeProgress = badgeProgress,
                mentorPoints = mentorPoints,
                onOpenShowcaseEditor = { showBadgeShowcaseSheet = true },
                onUpdateShowcaseIds = { updated ->
                    val saved = SharkBadgeCatalog.saveShowcaseBadgeIds(context, updated)
                    showcaseBadgeIds = saved
                    if (!isGuest && user != null) {
                        SharkBadgeCatalog.syncShowcaseAndBadgesToCloud(
                            context = context,
                            uid = uid,
                            showcaseIds = saved,
                            unlockedIds = unlockedBadgeIds,
                            mentorPoints = mentorPoints,
                            badgeProgress = badgeProgress
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Info Card
            item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clickable { showAvatarPickerSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            SharkAvatar(
                                avatarBase64 = avatarState.avatarBase64,
                                avatarPresetId = avatarState.avatarPresetId,
                                displayName = effectiveDisplayName,
                                size = 58.dp
                            )
                            if (avatarState.isUpdating) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.38f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Profil Görseli Seç",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable(enabled = !isGuest) { showNameEditDialog = true }
                            ) {
                                Text(
                                    text = effectiveDisplayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!isGuest) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Adı Düzenle",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (isGuest) "Geçici Oturum" else "Doğrulanmış Öğrenci Hesabı",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Görseli veya adını değiştirmek için dokun",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Email Row
                    ProfileDetailRow(
                        icon = Icons.Default.Email,
                        label = "E-Posta Adresi",
                        value = displayEmail
                    )

                    // Grade Row (tap to edit without altering visual appearance)
                    ProfileDetailRow(
                        icon = Icons.Default.Grade,
                        label = "Sınıf Seviyesi",
                        value = studentGrade,
                        onClick = { showGradeEditDialog = true }
                    )

                    // Target Exam Row (tap to edit without altering visual appearance)
                    ProfileDetailRow(
                        icon = Icons.Default.School,
                        label = "Hedef Sınav",
                        value = studentTargetExam,
                        onClick = { showTargetExamEditDialog = true }
                    )

                    // Institution / Coach Status Row
                    ProfileDetailRow(
                        icon = Icons.Default.Business,
                        label = "Kurum & Koç Bağlantı Durumu",
                        value = if (hasCoach) "$institutionName ${if (coachName.isNotBlank()) "(Koç: $coachName)" else ""}" else "Bireysel Öğrenci (Study Modu)"
                    )

                    // UID Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Kullanıcı ID",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Kullanıcı ID (UID):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(
                                    text = uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            } // end User Info Card item

            // ── Mentor Puanı & 5 Rozet Vitrini Card ──────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "5 Rozet Vitrini & Mentorluk",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$mentorPoints Mentor Puanı • ${unlockedBadgeIds.size}/${SharkBadgeCatalog.ALL_BADGES.size} Rozet Kazanıldı",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { showBadgeShowcaseSheet = true }) {
                                Text(
                                    text = "Vitrini Düzenle (Maks. 5)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (showcaseBadgeIds.isNotEmpty()) {
                            SharkBadgeShowcaseRow(
                                badgeIds = showcaseBadgeIds,
                                compact = false,
                                badgeProgress = badgeProgress
                            )
                        } else {
                            Text(
                                text = "Grup ve soru sohbetlerinde soru paylaşarak veya çözerek rozetler kazanıp en fazla 5 rozetini burada sergileyebilirsin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { selectedTabIndex = 1 }) {
                                Text(
                                    text = "Tüm Rozetleri Gör →",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // ── Kurum & Koç Bağlantısı Form Section ──────────────────────────────────
            if (!isGuest) {
                if (!hasCoach) {
                    item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Kurum & Koç Bağlantısı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Bir kurum veya eğitim koçuyla mı çalışıyorsun? Kodunu girerek bağlantını aktifleştir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = inputCode,
                                onValueChange = { inputCode = it },
                                label = { Text("Kurum / Koç Kodu") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = inputStudentName,
                                onValueChange = { inputStudentName = it },
                                label = { Text("Öğrenci Adı Soyadı") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (linkError.isNotBlank()) {
                                Text(linkError, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    if (inputCode.isBlank()) {
                                        linkError = "Lütfen geçerli bir kurum/koç kodu girin."
                                        return@Button
                                    }
                                    isSubmitting = true
                                    linkError = ""
                                    val cleanCode = inputCode.trim()
                                    CoachCodeRepository(context).submitCoachCode(
                                        inputCode = cleanCode,
                                        onSuccess = {
                                            isSubmitting = false
                                            hasCoach = true
                                            institutionName = "Bağlı Kurum ($cleanCode)"
                                            if (isMeaningfulProfileName(inputStudentName)) {
                                                val updatedName = SyncWorker.sanitizeTurkishText(inputStudentName.trim())
                                                studentName = updatedName
                                                saveProfileNameLocallyAndCloud(context, uid, isGuest = isGuest, rawName = updatedName)
                                            }
                                            Toast.makeText(context, "Koç ve kurum bağlantısı sağlandı.", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { err ->
                                            isSubmitting = false
                                            linkError = err
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Bağlan", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    } // end hasCoach form item
                } else {
                    item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mevcut Bağlantı",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "$institutionName ${if (coachName.isNotBlank()) "\nKoç: $coachName" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Button(
                                onClick = { showUnlinkConfirm = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "Koçla Bağlantıyı Kes",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    } // end hasCoach card item
                }
            }

            item {
            CloudBackupSection(
                onBackupClick = { /* TODO */ },
                onRestoreClick = { /* TODO */ },
                isSyncing = false,
                lastBackupDate = null
            )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
            if (isGuest) {
                Button(
                    onClick = onLoginClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("E-posta ile Giriş Yap / Kayıt Ol", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onLogoutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Çıkış Yap", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            }
            } // end Login/Logout item
        }
        } // end Tab 0/1 branch
    }


    if (showUnlinkConfirm) {
        AlertDialog(
            onDismissRequest = { showUnlinkConfirm = false },
            title = { Text("Bağlantıyı Sonlandır") },
            text = { Text("Koçunuzla olan bağlantınızı kesmek istediğinize emin misiniz? Campus modundan çıkarak bireysel Study moduna döneceksiniz.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkConfirm = false
                        viewModel.unlinkCoach { _, _ -> }
                          hasCoach = false
                          institutionName = ""
                          coachName = ""
                        Toast.makeText(context, "Bağlantı kesildi.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sonlandır", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showNameEditDialog) {
        var draftName by remember(studentName) { mutableStateOf(studentName) }
        AlertDialog(
            onDismissRequest = { showNameEditDialog = false },
            title = { Text("Görünecek Adını Güncelle", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(36) },
                    label = { Text("Adın veya Takma Adın") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = SyncWorker.sanitizeTurkishText(draftName.trim())
                        if (isMeaningfulProfileName(clean)) {
                            studentName = clean
                            saveProfileNameLocallyAndCloud(context, uid, isGuest = isGuest, rawName = clean)
                            showNameEditDialog = false
                            Toast.makeText(context, "Görünen ad \"$clean\" olarak güncellendi.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Lütfen geçerli bir ad girin.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = draftName.trim().length >= 2
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showBadgeShowcaseSheet) {
        BadgeShowcaseSelectorSheet(
            unlockedBadgeIds = unlockedBadgeIds,
            initialSelectedIds = showcaseBadgeIds,
            badgeProgress = badgeProgress,
            mentorPoints = mentorPoints,
            onDismiss = { showBadgeShowcaseSheet = false },
            onSaveShowcase = { selected ->
                val saved = SharkBadgeCatalog.saveShowcaseBadgeIds(context, selected)
                showcaseBadgeIds = saved
                if (!isGuest && user != null) {
                    SharkBadgeCatalog.syncShowcaseAndBadgesToCloud(
                        context = context,
                        uid = uid,
                        showcaseIds = saved,
                        unlockedIds = unlockedBadgeIds,
                        mentorPoints = mentorPoints,
                        badgeProgress = badgeProgress
                    )
                }
                Toast.makeText(context, "Vitrin rozetleriniz kaydedildi.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAvatarPickerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val previewName = if (isGuest) "Misafir Öğrenci" else studentName.ifBlank { "Öğrenci" }

        ModalBottomSheet(
            onDismissRequest = { showAvatarPickerSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profil Görseli Seç",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                SharkAvatar(
                    avatarBase64 = avatarState.avatarBase64,
                    avatarPresetId = avatarState.avatarPresetId,
                    displayName = previewName,
                    size = 78.dp
                )

                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Galeriden Fotoğraf Seç", fontWeight = FontWeight.SemiBold)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Hazır Avatarlar",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AvatarHelper.PRESETS.chunked(5).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = avatarState.avatarBase64.isBlank() &&
                                    avatarState.avatarPresetId == preset.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.selectPresetAvatar(preset.id)
                                        }
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(preset.startColor, preset.endColor)
                                                )
                                            )
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.White.copy(alpha = 0.35f)
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = preset.icon,
                                            contentDescription = preset.label,
                                            tint = preset.iconTint,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetAvatar()
                            showAvatarPickerSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Varsayılana Sıfırla", fontSize = 12.sp, maxLines = 1)
                    }

                    TextButton(
                        onClick = { showAvatarPickerSheet = false },
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Text("Tamam", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showGradeEditDialog) {
        var draftGrade by remember(studentGrade) { mutableStateOf(studentGrade) }
        AlertDialog(
            onDismissRequest = { showGradeEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Sınıf Seviyesi",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DEFAULT_GRADE_OPTIONS.forEach { option ->
                            val selected = draftGrade.trim().equals(option, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = { draftGrade = option },
                                label = { Text(option, fontSize = 12.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draftGrade,
                        onValueChange = { draftGrade = it.take(36) },
                        label = { Text("Sınıf Düzeyi") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = SyncWorker.sanitizeTurkishText(draftGrade.trim())
                        if (clean.isNotBlank()) {
                            studentGrade = clean
                            saveStudentGradeLocallyAndCloud(
                                context = context,
                                uid = uid,
                                isGuest = isGuest,
                                rawGrade = clean
                            )
                            showGradeEditDialog = false
                            Toast.makeText(context, "Sınıf seviyesi kaydedildi.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = draftGrade.trim().isNotBlank()
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGradeEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showTargetExamEditDialog) {
        var draftExam by remember(studentTargetExam) { mutableStateOf(studentTargetExam) }
        AlertDialog(
            onDismissRequest = { showTargetExamEditDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Hedef Sınav",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DEFAULT_EXAM_OPTIONS.forEach { option ->
                            val selected = draftExam.trim().equals(option, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = { draftExam = option },
                                label = { Text(option, fontSize = 12.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draftExam,
                        onValueChange = { draftExam = it.take(40) },
                        label = { Text("Hedef Sınav Adı") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = SyncWorker.sanitizeTurkishText(draftExam.trim())
                        if (clean.isNotBlank()) {
                            studentTargetExam = clean
                            saveStudentTargetExamLocallyAndCloud(
                                context = context,
                                uid = uid,
                                isGuest = isGuest,
                                rawExam = clean
                            )
                            showTargetExamEditDialog = false
                            Toast.makeText(context, "Hedef sınav kaydedildi.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = draftExam.trim().isNotBlank()
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetExamEditDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

}

@Composable
private fun ProfileDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth()
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CloudBackupSection(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    isSyncing: Boolean,
    lastBackupDate: String?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bulut & Drive Yedekleme",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = lastBackupDate?.let { "Son Yedek: $it" } ?: "Henüz yedek alınmadı",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBackupClick,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Drive'a Yedekle", maxLines = 1, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRestoreClick,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Geri Yükle", maxLines = 1, fontSize = 12.sp)
                }
            }
        }
    }
}


