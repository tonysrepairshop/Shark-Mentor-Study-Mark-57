package com.example.sharkmentor.data.repository

import android.content.Context
import android.util.Log
import com.example.sharkmentor.core.ai.SharkTokenQuotaManager
import com.example.sharkmentor.core.util.GoogleServicesHelper
import com.example.sharkmentor.core.util.SessionManager
import com.example.sharkmentor.data.local.preferences.UserPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CoachCodeRepository(private val context: Context) {

    private fun saveLocalCoachCode(code: String, studentName: String) {
        val prefs = UserPreferences.getInstance(context)
        prefs.completeOnboardingWithInstitution(studentName, code)
        SessionManager.setLinkedToInstitution(context, true)
        SessionManager.setUserMode(context, "campus")
        Log.d("SharkAffiliation", "Yerel hafıza ve SessionManager güncellendi: $code (campus)")
    }

    fun submitCoachCode(inputCode: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (SharkTokenQuotaManager.isUnlimitedTokenCode(inputCode)) {
            SharkTokenQuotaManager.unlockUnlimitedAccess(context)
            onSuccess()
            return
        }
        val cleanCode = inputCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            onError("Lütfen bir koç veya kurum kodu giriniz.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val auth = GoogleServicesHelper.getFirebaseAuth(context)
                val db = GoogleServicesHelper.getFirebaseFirestore(context)
                if (auth == null || db == null) {
                    withContext(Dispatchers.Main) { onError("Google Play Servisleri veya veritabanı erişilemedi.") }
                    return@launch
                }

                var user = GoogleServicesHelper.getCurrentUser(auth, context)
                if (user == null) {
                    try {
                        val authResult = auth.signInAnonymously().await()
                        user = authResult.user
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { onError("Oturum açılamadı: ${e.localizedMessage}") }
                        return@launch
                    }
                }

                val uid = user?.uid
                if (uid == null) {
                    withContext(Dispatchers.Main) { onError("Kullanıcı kimliği doğrulanamadı.") }
                    return@launch
                }

                val studentIdentifier = "Öğrenci #${uid.takeLast(4).uppercase()}"
                val studentName = user.displayName?.takeIf { it.isNotBlank() } ?: studentIdentifier
                val studentEmail = user.email ?: ""

                var targetCoachId = ""
                var targetCoachName = "Rehber Koç"
                var targetInstCode = ""
                var targetInstName = "SharkMentor Kurum"

                // 1. Check coach_codes/{cleanCode}
                try {
                    val ccDoc = db.collection("coach_codes").document(cleanCode).get().await()
                    if (ccDoc.exists()) {
                        targetCoachId = ccDoc.getString("coachId") ?: ccDoc.getString("uid") ?: ""
                        targetInstCode = ccDoc.getString("institutionId") ?: ccDoc.getString("institutionCode") ?: ""
                    }
                } catch (e: Exception) {
                    Log.w("SharkAffiliation", "coach_codes check: ${e.message}")
                }

                // 2. Check pairingCodes/{cleanCode}
                if (targetCoachId.isBlank()) {
                    try {
                        val pcDoc = db.collection("pairingCodes").document(cleanCode).get().await()
                        if (pcDoc.exists()) {
                            targetCoachId = pcDoc.getString("coachId") ?: ""
                            targetCoachName = pcDoc.getString("coachName") ?: targetCoachName
                            targetInstCode = pcDoc.getString("institutionCode") ?: pcDoc.getString("institutionId") ?: ""
                            targetInstName = pcDoc.getString("institutionName") ?: targetInstName
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "pairingCodes check: ${e.message}")
                    }
                }

                // 3. Query coaches where coachCode == cleanCode
                if (targetCoachId.isBlank()) {
                    try {
                        val cSnap = db.collection("coaches").whereEqualTo("coachCode", cleanCode).get().await()
                        val cDoc = cSnap.documents.firstOrNull()
                        if (cDoc != null) {
                            targetCoachId = cDoc.id
                            targetCoachName = cDoc.getString("name") ?: cDoc.getString("displayName") ?: cDoc.getString("coachName") ?: targetCoachName
                            targetInstCode = cDoc.getString("institutionCode") ?: cDoc.getString("institutionId") ?: ""
                            targetInstName = cDoc.getString("institutionName") ?: targetInstName
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "coaches coachCode check: ${e.message}")
                    }
                }

                // 4. Query coaches where branchCode == cleanCode
                if (targetCoachId.isBlank()) {
                    try {
                        val cSnap2 = db.collection("coaches").whereEqualTo("branchCode", cleanCode).get().await()
                        val cDoc2 = cSnap2.documents.firstOrNull()
                        if (cDoc2 != null) {
                            targetCoachId = cDoc2.id
                            targetCoachName = cDoc2.getString("name") ?: cDoc2.getString("displayName") ?: cDoc2.getString("coachName") ?: targetCoachName
                            targetInstCode = cDoc2.getString("institutionCode") ?: cDoc2.getString("institutionId") ?: ""
                            targetInstName = cDoc2.getString("institutionName") ?: targetInstName
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "coaches branchCode check: ${e.message}")
                    }
                }

                // 5. Query users where coachCode == cleanCode
                if (targetCoachId.isBlank()) {
                    try {
                        val uSnap = db.collection("users").whereEqualTo("coachCode", cleanCode).get().await()
                        val uDoc = uSnap.documents.firstOrNull()
                        if (uDoc != null) {
                            targetCoachId = uDoc.id
                            targetCoachName = uDoc.getString("name") ?: uDoc.getString("displayName") ?: targetCoachName
                            targetInstCode = uDoc.getString("institutionCode") ?: uDoc.getString("institutionId") ?: ""
                            targetInstName = uDoc.getString("institutionName") ?: targetInstName
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "users coachCode check: ${e.message}")
                    }
                }

                // 6. Enrich Coach info if coachId is known
                if (targetCoachId.isNotBlank()) {
                    try {
                        val cDoc = db.collection("coaches").document(targetCoachId).get().await()
                        if (cDoc.exists()) {
                            targetCoachName = cDoc.getString("name") ?: cDoc.getString("displayName") ?: cDoc.getString("coachName") ?: targetCoachName
                            if (targetInstCode.isBlank()) targetInstCode = cDoc.getString("institutionCode") ?: cDoc.getString("institutionId") ?: ""
                            if (targetInstName == "SharkMentor Kurum") targetInstName = cDoc.getString("institutionName") ?: targetInstName
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "coaches enrich check: ${e.message}")
                    }
                    if (targetInstCode.isBlank()) {
                        try {
                            val uDoc = db.collection("users").document(targetCoachId).get().await()
                            if (uDoc.exists()) {
                                targetCoachName = uDoc.getString("name") ?: uDoc.getString("displayName") ?: targetCoachName
                                targetInstCode = uDoc.getString("institutionCode") ?: uDoc.getString("institutionId") ?: ""
                                if (targetInstName == "SharkMentor Kurum") targetInstName = uDoc.getString("institutionName") ?: targetInstName
                            }
                        } catch (e: Exception) {
                            Log.w("SharkAffiliation", "users enrich check: ${e.message}")
                        }
                    }
                }

                // 7. Check institutions if still not matched with coach
                if (targetCoachId.isBlank()) {
                    try {
                        val instDoc = db.collection("institutions").document(cleanCode).get().await()
                        if (instDoc.exists()) {
                            targetInstCode = instDoc.id
                            targetInstName = instDoc.getString("name") ?: "Kurum $cleanCode"
                            targetCoachName = targetInstName

                            // Check if institution has coaches
                            val cSnap = db.collection("coaches").whereEqualTo("institutionCode", targetInstCode).get().await()
                            val firstCoach = cSnap.documents.firstOrNull()
                            if (firstCoach != null) {
                                targetCoachId = firstCoach.id
                                targetCoachName = firstCoach.getString("name") ?: firstCoach.getString("coachName") ?: targetCoachName
                            } else {
                                val adminUid = instDoc.getString("adminUid") ?: instDoc.getString("managerId")
                                if (!adminUid.isNullOrBlank()) {
                                    targetCoachId = adminUid
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("SharkAffiliation", "institutions check: ${e.message}")
                    }
                }

                if (targetCoachId.isBlank() && targetInstCode.isBlank()) {
                    withContext(Dispatchers.Main) {
                        onError("Geçersiz veya bulunamayan koç/kurum kodu ($cleanCode). Lütfen kodunuzu kontrol ediniz.")
                    }
                    return@launch
                }

                val finalCoachId = if (targetCoachId.isNotBlank()) targetCoachId else cleanCode
                val finalInstCode = if (targetInstCode.isNotBlank()) targetInstCode else cleanCode

                val studentData = hashMapOf<String, Any>(
                    "id" to uid,
                    "uid" to uid,
                    "name" to studentName,
                    "displayName" to studentName,
                    "email" to studentEmail,
                    "assignedCoachId" to finalCoachId,
                    "assignedCoachName" to targetCoachName,
                    "coachId" to finalCoachId,
                    "coachCode" to cleanCode,
                    "institutionCode" to finalInstCode,
                    "institutionId" to finalInstCode,
                    "institutionName" to targetInstName,
                    "branchCode" to finalInstCode,
                    "isInstitutional" to true,
                    "userMode" to "campus",
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val batch = db.batch()
                batch.set(db.collection("students").document(uid), studentData, SetOptions.merge())
                batch.set(db.collection("users").document(uid), studentData, SetOptions.merge())

                if (targetCoachId.isNotBlank()) {
                    batch.set(db.collection("coaches").document(targetCoachId).collection("students").document(uid), studentData, SetOptions.merge())
                    if (targetInstCode.isNotBlank()) {
                        batch.set(db.document("institutions/$targetInstCode/coaches/$targetCoachId/students/$uid"), studentData, SetOptions.merge())
                    }
                }

                batch.commit().await()

                saveLocalCoachCode(finalInstCode, studentName)

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("SharkAffiliation", "submitCoachCode exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError("Bağlantı hatası: ${e.localizedMessage}")
                }
            }
        }
    }
}
