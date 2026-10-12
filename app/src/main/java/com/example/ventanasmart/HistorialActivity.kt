
package com.example.ventanasmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistorialActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val windowId = intent.getStringExtra("windowId")

        setContent {
            MaterialTheme {
                if (windowId.isNullOrBlank()) {
                    PantallaHistorialSinVentana {
                        finish()
                    }
                } else {
                    PantallaHistorial(
                        windowId = windowId,
                        volver = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaHistorialSinVentana(volver: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B3552))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No se seleccionó ninguna ventana.",
            color = Color.White,
            fontSize = 20.sp
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = volver) {
            Text("VOLVER")
        }
    }
}

@Composable
fun PantallaHistorial(
    windowId: String,
    volver: () -> Unit
) {
    var registros by remember {
        mutableStateOf<List<VentanaHistorialItem>>(emptyList())
    }

    var mensaje by remember {
        mutableStateOf("Cargando historial...")
    }

    var cargando by remember { mutableStateOf(true) }
    var borrando by remember { mutableStateOf(false) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    val usuarioId = FirebaseAuth.getInstance().currentUser?.uid

    DisposableEffect(windowId, usuarioId) {
        var listener: ListenerRegistration? = null

        if (usuarioId == null) {
            mensaje = "Debes iniciar sesión para ver el historial."
            cargando = false
        } else {
            cargando = true

            listener = FirestoreRepository.observeWindowHistory(
                windowId = windowId,
                onChange = { lista ->
                    registros = lista
                    mensaje = if (lista.isEmpty()) {
                        "Todavía no hay registros para esta ventana."
                    } else {
                        ""
                    }
                    cargando = false
                },
                onError = { error ->
                    mensaje = "Error: ${
                        error.localizedMessage ?: "no se pudo cargar el historial"
                    }"
                    cargando = false
                }
            )
        }

        onDispose {
            listener?.remove()
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = {
                confirmarBorrado = false
            },
            title = {
                Text("Borrar historial")
            },
            text = {
                Text(
                    "¿Quieres eliminar todos los registros de esta ventana? " +
                            "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarBorrado = false

                        val uid = FirebaseAuth.getInstance()
                            .currentUser?.uid

                        if (uid == null) {
                            mensaje = "Debes iniciar sesión."
                        } else {
                            borrando = true

                            borrarRegistrosDeVentana(
                                windowId = windowId,
                                registros = registros,
                                onComplete = { error ->
                                    borrando = false
                                    mensaje = error ?: "Historial eliminado."
                                }
                            )
                        }
                    }
                ) {
                    Text(
                        "BORRAR",
                        color = Color(0xFFB3261E)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmarBorrado = false
                    }
                ) {
                    Text("CANCELAR")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B3552),
                        Color(0xFF237B9D),
                        Color(0xFF8BCED9)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(
                text = "VENTANASMART",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Historial de actividad",
                color = Color.White,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "HISTORIAL",
                        color = Color(0xFF12304A),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(15.dp))

                    if (cargando) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                    }

                    if (mensaje.isNotEmpty()) {
                        Text(
                            text = mensaje,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    registros.forEach { registro ->
                        val usuarioRegistro = registro.email.ifBlank {
                            registro.userId.ifBlank {
                                "Usuario no identificado"
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF1F6F8)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(15.dp)
                            ) {
                                Text(
                                    text = registro.accion,
                                    color = Color(0xFF12304A),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = "Usuario: $usuarioRegistro",
                                    color = Color(0xFF12304A),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = "Modo: ${registro.modo}",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = registro.createdAt?.let { timestamp ->
                                        SimpleDateFormat(
                                            "dd/MM/yyyy HH:mm:ss",
                                            Locale.getDefault()
                                        ).format(Date(timestamp))
                                    } ?: "Fecha pendiente",
                                    color = Color(0xFF159AC4),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    confirmarBorrado = true
                },
                enabled = !borrando && registros.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB3261E)
                )
            ) {
                Text(
                    text = if (borrando) {
                        "BORRANDO..."
                    } else {
                        "🗑 BORRAR HISTORIAL"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = volver,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF12304A)
                )
            ) {
                Text(
                    text = "← VOLVER ATRÁS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun borrarRegistrosDeVentana(
    windowId: String,
    registros: List<VentanaHistorialItem>,
    onComplete: (String?) -> Unit
) {
    if (registros.isEmpty()) {
        onComplete("No hay registros para borrar.")
        return
    }

    var pendientes = registros.size
    var huboError = false

    registros.forEach { registro ->
        FirestoreRepository.deleteWindowHistoryRecord(
            windowId = windowId,
            recordId = registro.id,
            onSuccess = {
                pendientes--

                if (pendientes == 0) {
                    onComplete(
                        if (huboError) {
                            "No se pudieron borrar todos los registros."
                        } else {
                            "Historial eliminado."
                        }
                    )
                }
            },
            onError = {
                huboError = true
                pendientes--

                if (pendientes == 0) {
                    onComplete(
                        "No se pudieron borrar todos los registros."
                    )
                }
            }
        )
    }
}
