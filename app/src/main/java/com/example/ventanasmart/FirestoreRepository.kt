
package com.example.ventanasmart

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale
import java.util.UUID

data class VentanaItem(
    val id: String,
    val name: String,
    val ownerId: String,
    val isOpen: Boolean,
    val code: String = ""
)

data class VentanaHistorialItem(
    val id: String,
    val accion: String,
    val modo: String,
    val isOpen: Boolean?,
    val createdAt: Long?,
    val email: String = "",
    val userId: String = ""
)

data class VentanaProgramacion(
    val closeAtMillis: Long? = null,
    val createdByEmail: String = "",
    val createdByUid: String = ""
)

object FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun currentUserId(): String? =
        auth.currentUser?.uid

    private fun currentEmail(): String =
        auth.currentUser?.email ?: ""

    private fun logError(message: String, error: Exception) {
        Log.e("VentanaSmart", message, error)
    }

    private fun generateWindowCode(): String {
        return "VS-" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .take(12)
            .uppercase(Locale.ROOT)
    }

    // ---------------------------------------------------------
    // PERFIL DEL USUARIO
    // ---------------------------------------------------------

    fun registerUserProfile(
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val data = hashMapOf<String, Any>(
            "uid" to uid,
            "email" to email.trim().lowercase(Locale.ROOT),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("userProfiles")
            .document(uid)
            .set(data)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                logError("Error guardando perfil", error)
                onError(error)
            }
    }

    // ---------------------------------------------------------
    // CREAR VENTANA CON CÓDIGO PERMANENTE
    // ---------------------------------------------------------

    fun createWindow(
        name: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        if (name.isBlank()) {
            onError(Exception("Escribe un nombre para la ventana"))
            return
        }

        val windowRef = db.collection("windows").document()

        val data = hashMapOf<String, Any>(
            "name" to name.trim(),
            "ownerId" to uid,
            "members" to listOf(uid),
            "isOpen" to false,
            "code" to generateWindowCode(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        windowRef.set(data)
            .addOnSuccessListener { onSuccess(windowRef.id) }
            .addOnFailureListener { error ->
                logError("Error creando ventana", error)
                onError(error)
            }
    }

    // ---------------------------------------------------------
    // OBTENER O RECUPERAR EL CÓDIGO PERMANENTE
    // ---------------------------------------------------------

    fun createWindowInviteCode(
        windowId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val windowRef = db.collection("windows").document(windowId)

        windowRef.get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onError(Exception("La ventana no existe"))
                    return@addOnSuccessListener
                }

                val existingCode = document.getString("code")

                if (!existingCode.isNullOrBlank()) {
                    onSuccess(existingCode)
                    return@addOnSuccessListener
                }

                val newCode = generateWindowCode()

                windowRef.update(
                    mapOf(
                        "code" to newCode,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    onSuccess(newCode)
                }.addOnFailureListener { error ->
                    logError("Error guardando código", error)
                    onError(error)
                }
            }
            .addOnFailureListener { error ->
                logError("Error consultando código", error)
                onError(error)
            }
    }

    // ---------------------------------------------------------
    // AGREGAR UNA VENTANA MEDIANTE SU CÓDIGO
    // ---------------------------------------------------------

    fun joinWindowByCode(
        code: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val normalizedCode = code.trim().uppercase(Locale.ROOT)

        if (normalizedCode.isBlank()) {
            onError(Exception("Introduce el código de la ventana"))
            return
        }

        db.collection("windows")
            .whereEqualTo("code", normalizedCode)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    onError(Exception("No existe una ventana con ese código"))
                    return@addOnSuccessListener
                }

                val windowRef = result.documents.first().reference

                db.runTransaction { transaction ->
                    val document = transaction.get(windowRef)

                    if (!document.exists()) {
                        throw Exception("La ventana ya no existe")
                    }

                    val members = (document.get("members") as? List<*>)
                        ?.filterIsInstance<String>()
                        ?.toMutableList()
                        ?: mutableListOf()

                    if (uid !in members) {
                        members.add(uid)
                        transaction.update(
                            windowRef,
                            mapOf(
                                "members" to members,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    windowRef.id
                }.addOnSuccessListener { joinedWindowId ->
                    onSuccess(joinedWindowId)
                }.addOnFailureListener { error ->
                    logError("Error agregando ventana por código", error)
                    onError(error)
                }
            }
            .addOnFailureListener { error ->
                logError("Error buscando ventana por código", error)
                onError(error)
            }
    }

    // ---------------------------------------------------------
    // COMPARTIR POR CORREO (COMPATIBILIDAD)
    // ---------------------------------------------------------

    fun shareWindowByEmail(
        windowId: String,
        email: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)

        ensureWindowOwner(
            windowId = windowId,
            onSuccess = {
                db.collection("userProfiles")
                    .whereEqualTo("email", normalizedEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        val targetUid = result.documents
                            .firstOrNull()
                            ?.getString("uid")

                        if (targetUid.isNullOrBlank()) {
                            onError(Exception("No se encontró ese usuario"))
                            return@addOnSuccessListener
                        }

                        val windowRef = db.collection("windows")
                            .document(windowId)

                        db.runTransaction { transaction ->
                            val document = transaction.get(windowRef)

                            if (!document.exists()) {
                                throw Exception("La ventana no existe")
                            }

                            val members = (document.get("members") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?.toMutableList()
                                ?: mutableListOf()

                            if (targetUid !in members) {
                                members.add(targetUid)
                                transaction.update(
                                    windowRef,
                                    mapOf(
                                        "members" to members,
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                            }
                        }.addOnSuccessListener {
                            onSuccess()
                        }.addOnFailureListener { error ->
                            logError("Error compartiendo ventana", error)
                            onError(error)
                        }
                    }
                    .addOnFailureListener { error ->
                        logError("Error buscando usuario", error)
                        onError(error)
                    }
            },
            onError = onError
        )
    }

    fun ensureWindowOwner(
        windowId: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        db.collection("windows")
            .document(windowId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onError(Exception("La ventana no existe"))
                    return@addOnSuccessListener
                }

                if (document.getString("ownerId") != uid) {
                    onError(Exception("No tienes permiso para realizar esta acción"))
                    return@addOnSuccessListener
                }

                onSuccess(uid)
            }
            .addOnFailureListener { error ->
                logError("Error verificando propietario", error)
                onError(error)
            }
    }

    // ---------------------------------------------------------
    // OBSERVAR LAS VENTANAS DEL USUARIO
    // ---------------------------------------------------------

    fun observeMyWindows(
        onChange: (List<VentanaItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration? {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return null
        }

        return db.collection("windows")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logError("Error observando ventanas", error)
                    onError(error)
                    return@addSnapshotListener
                }

                val windows = snapshot?.documents?.map { document ->
                    VentanaItem(
                        id = document.id,
                        name = document.getString("name") ?: "Mi ventana",
                        ownerId = document.getString("ownerId") ?: "",
                        isOpen = document.getBoolean("isOpen") ?: false,
                        code = document.getString("code") ?: ""
                    )
                } ?: emptyList()

                onChange(windows)
            }
    }

    // ---------------------------------------------------------
    // OBSERVAR EL ESTADO DE UNA VENTANA COMPARTIDA
    // ---------------------------------------------------------

    fun observeWindowState(
        windowId: String,
        onChange: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("windows")
            .document(windowId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    logError("Error observando estado", error)
                    onError(error)
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    onChange(document.getBoolean("isOpen") ?: false)
                }
            }
    }

    // ---------------------------------------------------------
    // PROGRAMACIÓN DE CIERRE COMPARTIDA ENTRE USUARIOS
    // ---------------------------------------------------------

    fun observeWindowSchedule(
        windowId: String,
        onChange: (VentanaProgramacion) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("windows")
            .document(windowId)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    logError("Error observando programación", error)
                    onError(error)
                    return@addSnapshotListener
                }

                val schedule = if (document != null && document.exists()) {
                    VentanaProgramacion(
                        closeAtMillis = document.getLong("scheduleCloseAt"),
                        createdByEmail = document.getString("scheduleCreatedByEmail") ?: "",
                        createdByUid = document.getString("scheduleCreatedByUid") ?: ""
                    )
                } else {
                    VentanaProgramacion()
                }

                onChange(schedule)
            }
    }

    fun scheduleWindowClose(
        windowId: String,
        closeAtMillis: Long,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        if (closeAtMillis <= System.currentTimeMillis()) {
            onError(Exception("La hora de cierre debe estar en el futuro"))
            return
        }

        val windowRef = db.collection("windows").document(windowId)

        db.runTransaction { transaction ->
            val document = transaction.get(windowRef)

            if (!document.exists()) {
                throw Exception("La ventana no existe")
            }

            val members = (document.get("members") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            if (uid !in members) {
                throw Exception("No tienes acceso a esta ventana")
            }

            transaction.update(
                windowRef,
                mapOf(
                    "scheduleCloseAt" to closeAtMillis,
                    "scheduleCreatedByEmail" to currentEmail(),
                    "scheduleCreatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            logError("Error programando cierre compartido", error)
            onError(error)
        }
    }

    fun cancelWindowClose(
        windowId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val windowRef = db.collection("windows").document(windowId)

        db.runTransaction { transaction ->
            val document = transaction.get(windowRef)

            if (!document.exists()) {
                throw Exception("La ventana no existe")
            }

            val members = (document.get("members") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            if (uid !in members) {
                throw Exception("No tienes acceso a esta ventana")
            }

            transaction.update(
                windowRef,
                mapOf(
                    "scheduleCloseAt" to FieldValue.delete(),
                    "scheduleCreatedByEmail" to FieldValue.delete(),
                    "scheduleCreatedByUid" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            logError("Error cancelando cierre compartido", error)
            onError(error)
        }
    }

    // ---------------------------------------------------------
    // ABRIR O CERRAR VENTANA Y GUARDAR HISTORIAL
    // ---------------------------------------------------------

    fun setWindowState(
        windowId: String,
        isOpen: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
        modo: String = "Manual"
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val windowRef = db.collection("windows").document(windowId)
        val historyRef = windowRef.collection("history").document()

        db.runTransaction { transaction ->
            val document = transaction.get(windowRef)

            if (!document.exists()) {
                throw Exception("La ventana no existe")
            }

            val members = (document.get("members") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            if (uid !in members) {
                throw Exception("No tienes acceso a esta ventana")
            }

            transaction.update(
                windowRef,
                mapOf(
                    "isOpen" to isOpen,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            transaction.set(
                historyRef,
                hashMapOf<String, Any>(
                    "action" to if (isOpen) "Ventana abierta" else "Ventana cerrada",
                    "modo" to modo,
                    "isOpen" to isOpen,
                    "userId" to uid,
                    "email" to currentEmail(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            logError("Error cambiando estado de ventana", error)
            onError(error)
        }
    }

    // ---------------------------------------------------------
    // OBSERVAR EL HISTORIAL DE UNA VENTANA
    // ---------------------------------------------------------

    fun observeWindowHistory(
        windowId: String,
        onChange: (List<VentanaHistorialItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("windows")
            .document(windowId)
            .collection("history")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    logError("Error observando historial", error)
                    onError(error)
                    return@addSnapshotListener
                }

                val records = snapshot?.documents?.map { document ->
                    val timestamp = document.getTimestamp("createdAt")

                    VentanaHistorialItem(
                        id = document.id,
                        accion = document.getString("action")
                            ?: document.getString("accion")
                            ?: "Evento",
                        modo = document.getString("modo") ?: "Manual",
                        isOpen = document.getBoolean("isOpen"),
                        createdAt = timestamp?.toDate()?.time,
                        email = document.getString("email") ?: "",
                        userId = document.getString("userId") ?: ""
                    )
                } ?: emptyList()

                onChange(records)
            }
    }

    // ---------------------------------------------------------
    // AGREGAR UN EVENTO AL HISTORIAL
    // ---------------------------------------------------------

    fun addHistoryRecord(
        windowId: String,
        accion: String,
        modo: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val windowRef = db.collection("windows").document(windowId)
        val historyRef = windowRef.collection("history").document()

        db.runTransaction { transaction ->
            val document = transaction.get(windowRef)

            if (!document.exists()) {
                throw Exception("La ventana no existe")
            }

            val members = (document.get("members") as? List<*>)
                ?.filterIsInstance<String>()
                ?: emptyList()

            if (uid !in members) {
                throw Exception("No tienes acceso a esta ventana")
            }

            transaction.set(
                historyRef,
                hashMapOf<String, Any>(
                    "action" to accion,
                    "modo" to modo,
                    "userId" to uid,
                    "email" to currentEmail(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            logError("Error agregando historial", error)
            onError(error)
        }
    }

    // ---------------------------------------------------------
    // ELIMINAR UN REGISTRO DEL HISTORIAL
    // ---------------------------------------------------------

    fun deleteWindowHistoryRecord(
        windowId: String,
        recordId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val windowRef = db.collection("windows").document(windowId)
        val historyRef = windowRef.collection("history").document(recordId)

        db.runTransaction { transaction ->
            val window = transaction.get(windowRef)

            if (!window.exists()) {
                throw Exception("La ventana no existe")
            }

            if (window.getString("ownerId") != uid) {
                throw Exception("Solo el propietario puede borrar el historial")
            }

            transaction.delete(historyRef)
        }.addOnSuccessListener {
            onSuccess()
        }.addOnFailureListener { error ->
            logError("Error eliminando registro", error)
            onError(error)
        }
    }

    // ---------------------------------------------------------
    // COMPATIBILIDAD CON EL ESTADO ANTIGUO POR USUARIO
    // ---------------------------------------------------------

    private fun userDocument() =
        db.collection("users")
            .document(currentUserId() ?: "sin_usuario")

    fun observeWindowState(
        onChange: (Boolean) -> Unit
    ): ListenerRegistration {
        return userDocument()
            .collection("window")
            .document("state")
            .addSnapshotListener { document, error ->
                if (error != null) {
                    logError("Error observando estado antiguo", error)
                    return@addSnapshotListener
                }

                onChange(document?.getBoolean("isOpen") ?: false)
            }
    }

    fun setWindowState(
        isOpen: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
        modo: String = "Manual"
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        val stateRef = userDocument()
            .collection("window")
            .document("state")

        stateRef.set(
            hashMapOf<String, Any>(
                "isOpen" to isOpen,
                "modo" to modo,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            addHistoryRecord(
                accion = if (isOpen) "Ventana abierta" else "Ventana cerrada",
                modo = modo,
                onSuccess = onSuccess,
                onError = onError
            )
        }.addOnFailureListener { error ->
            logError("Error guardando estado antiguo", error)
            onError(error)
        }
    }

    fun addHistoryRecord(
        accion: String,
        modo: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = currentUserId()

        if (uid == null) {
            onError(Exception("Debes iniciar sesión"))
            return
        }

        userDocument()
            .collection("history")
            .add(
                hashMapOf<String, Any>(
                    "accion" to accion,
                    "action" to accion,
                    "modo" to modo,
                    "userId" to uid,
                    "email" to currentEmail(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                logError("Error agregando historial antiguo", error)
                onError(error)
            }
    }

    fun deleteHistoryRecord(
        recordId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        userDocument()
            .collection("history")
            .document(recordId)
            .delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                logError("Error eliminando historial antiguo", error)
                onError(error)
            }
    }
}
