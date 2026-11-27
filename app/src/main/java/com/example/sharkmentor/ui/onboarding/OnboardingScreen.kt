package com.example.sharkmentor.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.sharkmentor.ui.components.AiTermsDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.local.SharkMentorDatabase
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.example.sharkmentor.service.sync.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var fullName by remember { mutableStateOf("") }
    var selectedGrade by remember { mutableStateOf("12. Sınıf") }
    var targetExam by remember { mutableStateOf("YKS 2027") }
    var institutionCode by remember { mutableStateOf("") }
    var isIndividual by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    var showAiTermsDialog by remember { mutableStateOf(false) }

    val gradeOptions = listOf("8. Sınıf", "11. Sınıf", "12. Sınıf", "Mezun", "Diğer")
    val examOptions = listOf("YKS 2026", "YKS 2027", "LGS 2026", "KPSS", "DGS")

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 50.dp,
                title = { Text("SharkMentor'a Hoş Geldiniz") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Öğrenci Profilinizi Oluşturun",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                text = "Eğitim yolculuğunuza başlamak için bilgilerinizi eksiksiz doldurun.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // Full Name Input
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it; showError = false },
                label = { Text("Ad Soyad *") },
                placeholder = { Text("Örn: Ahmet Yılmaz") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Grade Selection
            Text("Sınıf Seviyesi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                gradeOptions.take(3).forEachIndexed { idx, gr ->
                    SegmentedButton(
                        selected = selectedGrade == gr,
                        onClick = { selectedGrade = gr },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
                    ) {
                        Text(gr, fontSize = 11.sp)
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                gradeOptions.drop(3).forEachIndexed { idx, gr ->
                    SegmentedButton(
                        selected = selectedGrade == gr,
                        onClick = { selectedGrade = gr },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2)
                    ) {
                        Text(gr, fontSize = 11.sp)
                    }
                }
            }

            // Target Exam Selection
            Text("Hedef Sınav", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                examOptions.take(3).forEachIndexed { idx, ex ->
                    SegmentedButton(
                        selected = targetExam == ex,
                        onClick = { targetExam = ex },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3)
                    ) {
                        Text(ex, fontSize = 11.sp)
                    }
                }
            }

            Text(
                text = "Çalışma Modu",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ModeCard(
                    title = "Bireysel",
                    description = "Kendi kendime çalışacağım",
                    icon = Icons.Default.Person,
                    isSelected = isIndividual,
                    onClick = { isIndividual = true; showError = false },
                    modifier = Modifier.weight(1f)
                )

                ModeCard(
                    title = "Kurumsal",
                    description = "Bir koç veya kurum ile çalışacağım",
                    icon = Icons.Default.Business,
                    isSelected = !isIndividual,
                    onClick = { isIndividual = false; showError = false },
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(
                visible = !isIndividual,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OutlinedTextField(
                    value = institutionCode,
                    onValueChange = { institutionCode = it.uppercase(); showError = false },
                    label = { Text("Kurum / Koç Kodu (Opsiyonel)") },
                    placeholder = { Text("SHK-XXXXX") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            
            if (showError) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val cleanName = fullName.trim()
                    if (cleanName.isBlank()) {
                        errorMessage = "Lütfen adınızı ve soyadınızı girin."
                        showError = true
                        return@Button
                    }
                    showAiTermsDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Devam Et")
                }
            }
        }
    }
    
    if (showAiTermsDialog) {
        AiTermsDialog(
            onAccept = {
                showAiTermsDialog = false
                isLoading = true
                coroutineScope.launch {
                    try {
                        val auth = GoogleServicesHelper.getFirebaseAuth()
                        if (auth == null) {
                            errorMessage = "Google Play Servisleri / Auth erişilemedi."
                            isLoading = false
                            return@launch
                        }
                        if (auth.currentUser == null) {
                            auth.signInAnonymously().await()
                        }

                        val user = auth.currentUser
                        if (user != null) {
                            val profileUpdates = userProfileChangeRequest {
                                displayName = fullName.trim()
                            }
                            user.updateProfile(profileUpdates).await()

                            val uid = user.uid
                            val db = GoogleServicesHelper.getFirebaseFirestore()
                            if (db == null) {
                                errorMessage = "Veritabanına erişilemedi."
                                isLoading = false
                                return@launch
                            }
                            val studentPayload = hashMapOf<String, Any>(
                                "name" to fullName.trim(),
                                "email" to (user.email ?: ""),
                                "grade" to selectedGrade,
                                "targetExam" to targetExam,
                                "isStudying" to false,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )

                            val cleanCode = institutionCode.trim().uppercase()
                            val isJarvisUnlimitedCode = com.example.sharkmentor.core.ai.SharkTokenQuotaManager.isUnlimitedTokenCode(cleanCode)
                            val isUltronResetCode = com.example.sharkmentor.core.ai.SharkTokenQuotaManager.isResetTokenCode(cleanCode)
                            if (isJarvisUnlimitedCode) {
                                com.example.sharkmentor.core.ai.SharkTokenQuotaManager.unlockUnlimitedAccess(context)
                            } else if (isUltronResetCode) {
                                com.example.sharkmentor.core.ai.SharkTokenQuotaManager.lockUnlimitedAccess(context)
                            }
                            if (!isIndividual && cleanCode.isNotBlank() && !isJarvisUnlimitedCode && !isUltronResetCode) {
                                studentPayload["coachCode"] = cleanCode
                                studentPayload["userMode"] = "campus"
                                studentPayload["isInstitutional"] = true

                                var targetCoachId = ""
                                var targetCoachName = "Rehber Koç"
                                var targetInstCode = ""
                                var targetInstName = "SharkMentor Kurum"

                                // 1. coach_codes
                                try {
                                    val ccDoc = db.collection("coach_codes").document(cleanCode).get().await()
                                    if (ccDoc.exists()) {
                                        targetCoachId = ccDoc.getString("coachId") ?: ccDoc.getString("uid") ?: ""
                                        targetInstCode = ccDoc.getString("institutionId") ?: ccDoc.getString("institutionCode") ?: ""
                                    }
                                } catch (_: Exception) {}

                                // 2. pairingCodes
                                if (targetCoachId.isBlank()) {
                                    try {
                                        val pcDoc = db.collection("pairingCodes").document(cleanCode).get().await()
                                        if (pcDoc.exists()) {
                                            targetCoachId = pcDoc.getString("coachId") ?: ""
                                            targetCoachName = pcDoc.getString("coachName") ?: targetCoachName
                                            targetInstCode = pcDoc.getString("institutionCode") ?: pcDoc.getString("institutionId") ?: ""
                                            targetInstName = pcDoc.getString("institutionName") ?: targetInstName
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 3. coaches by coachCode
                                if (targetCoachId.isBlank()) {
                                    try {
                                        val cDoc = db.collection("coaches").whereEqualTo("coachCode", cleanCode).get().await().documents.firstOrNull()
                                        if (cDoc != null) {
                                            targetCoachId = cDoc.id
                                            targetCoachName = cDoc.getString("name") ?: cDoc.getString("displayName") ?: cDoc.getString("coachName") ?: targetCoachName
                                            targetInstCode = cDoc.getString("institutionCode") ?: cDoc.getString("institutionId") ?: ""
                                            targetInstName = cDoc.getString("institutionName") ?: targetInstName
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 4. users by coachCode
                                if (targetCoachId.isBlank()) {
                                    try {
                                        val uDoc = db.collection("users").whereEqualTo("coachCode", cleanCode).get().await().documents.firstOrNull()
                                        if (uDoc != null) {
                                            targetCoachId = uDoc.id
                                            targetCoachName = uDoc.getString("name") ?: uDoc.getString("displayName") ?: targetCoachName
                                            targetInstCode = uDoc.getString("institutionCode") ?: uDoc.getString("institutionId") ?: ""
                                            targetInstName = uDoc.getString("institutionName") ?: targetInstName
                                        }
                                    } catch (_: Exception) {}
                                }

                                // 5. Enrich coach info
                                if (targetCoachId.isNotBlank()) {
                                    try {
                                        val cDoc = db.collection("coaches").document(targetCoachId).get().await()
                                        if (cDoc.exists()) {
                                            targetCoachName = cDoc.getString("name") ?: cDoc.getString("displayName") ?: cDoc.getString("coachName") ?: targetCoachName
                                            if (targetInstCode.isBlank()) targetInstCode = cDoc.getString("institutionCode") ?: cDoc.getString("institutionId") ?: ""
                                            if (targetInstName == "SharkMentor Kurum") targetInstName = cDoc.getString("institutionName") ?: targetInstName
                                        }
                                    } catch (_: Exception) {}
                                    if (targetInstCode.isBlank()) {
                                        try {
                                            val uDoc = db.collection("users").document(targetCoachId).get().await()
                                            if (uDoc.exists()) {
                                                targetCoachName = uDoc.getString("name") ?: uDoc.getString("displayName") ?: targetCoachName
                                                targetInstCode = uDoc.getString("institutionCode") ?: uDoc.getString("institutionId") ?: ""
                                                if (targetInstName == "SharkMentor Kurum") targetInstName = uDoc.getString("institutionName") ?: targetInstName
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }

                                // 6. institutions collection
                                if (targetCoachId.isBlank()) {
                                    try {
                                        val instDoc = db.collection("institutions").document(cleanCode).get().await()
                                        if (instDoc.exists()) {
                                            targetInstCode = instDoc.id
                                            targetInstName = instDoc.getString("name") ?: "Kurum $cleanCode"
                                            targetCoachName = targetInstName
                                            val cDoc = db.collection("coaches").whereEqualTo("institutionCode", targetInstCode).get().await().documents.firstOrNull()
                                            if (cDoc != null) {
                                                targetCoachId = cDoc.id
                                                targetCoachName = cDoc.getString("name") ?: cDoc.getString("coachName") ?: targetCoachName
                                            } else {
                                                val adminUid = instDoc.getString("adminUid") ?: instDoc.getString("managerId")
                                                if (!adminUid.isNullOrBlank()) targetCoachId = adminUid
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                val finalCoachId = if (targetCoachId.isNotBlank()) targetCoachId else cleanCode
                                val finalInstCode = if (targetInstCode.isNotBlank()) targetInstCode else cleanCode

                                studentPayload["assignedCoachId"] = finalCoachId
                                studentPayload["assignedCoachName"] = targetCoachName
                                studentPayload["coachId"] = finalCoachId
                                studentPayload["institutionCode"] = finalInstCode
                                studentPayload["institutionId"] = finalInstCode
                                studentPayload["branchCode"] = finalInstCode
                                studentPayload["institutionName"] = targetInstName

                                SessionManager.setLinkedToInstitution(context, true)
                                SessionManager.setUserMode(context, "campus")

                                if (targetCoachId.isNotBlank()) {
                                    try {
                                        db.collection("coaches").document(targetCoachId).collection("students").document(uid).set(studentPayload, SetOptions.merge()).await()
                                        if (targetInstCode.isNotBlank()) {
                                            db.document("institutions/$targetInstCode/coaches/$targetCoachId/students/$uid").set(studentPayload, SetOptions.merge()).await()
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            db.collection("students").document(uid)
                                .set(studentPayload, SetOptions.merge())
                                .await()

                            try {
                                db.collection("users").document(uid)
                                    .set(studentPayload, SetOptions.merge())
                                    .await()
                            } catch (_: Exception) {}
                        }

                        val prefs = UserPreferences.getInstance(context)
                        val enteredJarvisCode = com.example.sharkmentor.core.ai.SharkTokenQuotaManager.isUnlimitedTokenCode(institutionCode)
                        val enteredUltronCode = com.example.sharkmentor.core.ai.SharkTokenQuotaManager.isResetTokenCode(institutionCode)
                        if (enteredJarvisCode) {
                            com.example.sharkmentor.core.ai.SharkTokenQuotaManager.unlockUnlimitedAccess(context)
                        } else if (enteredUltronCode) {
                            com.example.sharkmentor.core.ai.SharkTokenQuotaManager.lockUnlimitedAccess(context)
                        }
                        if (isIndividual || institutionCode.isBlank() || enteredJarvisCode || enteredUltronCode) {
                            prefs.completeOnboardingAsIndividual(fullName.trim())
                        } else {
                            prefs.completeOnboardingWithInstitution(fullName.trim(), institutionCode.trim())
                            withContext(Dispatchers.IO) {
                                val localDb = SharkMentorDatabase.getDatabase(context)
                                localDb.syncDao().tagUnassignedWithInstitution(institutionCode.trim())
                            }
                            SyncWorker.enqueueImmediateSync(context)
                        }
                        prefs.updateProfileDetails(
                            name = fullName.trim(),
                            grade = selectedGrade.trim(),
                            targetExam = targetExam.trim()
                        )
                        if (targetExam.isNotBlank()) {
                            context.getSharedPreferences("sharkmentor_prefs", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("target_exam_title", targetExam.trim())
                                .apply()
                        }

                        onOnboardingComplete()
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Kayıt sırasında bir hata oluştu."
                        showError = true
                    } finally {
                        isLoading = false
                    }
                }
            },
            onDismiss = {
                showAiTermsDialog = false
            }
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.2f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 11.sp
            )
        }
    }
}
