package com.example.ventanasmart

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

private val AzulVentana = Color(0xFF12304A)
private val CelesteVentana = Color(0xFF159AC4)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                PantallaPrincipal(
                    abrirHistorial = { windowId ->
                        val intent = Intent(
                            this,
                            HistorialActivity::class.java
                        )
                        intent.putExtra("windowId", windowId)
                        startActivity(intent)
                    },
                    cerrarSesion = {
                        FirebaseAuth.getInstance().signOut()

                        val intent = Intent(
                            this,
                            LoginActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PantallaPrincipal(
    abrirHistorial: (String) -> Unit,
    cerrarSesion: () -> Unit
) {
    val context = LocalContext.current

    var ventanas by remember {
        mutableStateOf<List<VentanaItem>>(emptyList())
    }

    var selectedWindowId by remember {
        mutableStateOf<String?>(null)
    }

    var ventanaAbierta by remember { mutableStateOf(false) }
    var modoAutomatico by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var menuVentanasAbierto by remember { mutableStateOf(false) }

    var nombreNuevaVentana by remember { mutableStateOf("") }
    var creandoVentana by remember { mutableStateOf(false) }

    var codigoVentana by remember { mutableStateOf("") }
    var cargandoCodigo by remember { mutableStateOf(false) }
    var codigoParaAgregar by remember { mutableStateOf("") }
    var agregandoVentana by remember { mutableStateOf(false) }

    // NUEVO: selector Wi-Fi
    var mostrarSelectorWifi by remember { mutableStateOf(false) }
    var redWifiSeleccionada by remember { mutableStateOf("") }

    var mensajeFirebase by remember {
        mutableStateOf("Cargando ventanas de Firebase...")
    }

    var mensajeProgramacion by remember {
        mutableStateOf("No hay cierre programado")
    }

    var minutosPersonalizados by remember { mutableStateOf("15") }

    var programacion by remember {
        mutableStateOf(VentanaProgramacion())
    }

    var horaActual by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    var procesandoCierre by remember { mutableStateOf(false) }

    val uidActual = FirebaseAuth.getInstance().currentUser?.uid

    val ventanaSeleccionada = ventanas.firstOrNull {
        it.id == selectedWindowId
    }

    LaunchedEffect(Unit) {
        while (true) {
            horaActual = System.currentTimeMillis()
            delay(1000L)
        }
    }

    DisposableEffect(Unit) {
        val listener = FirestoreRepository.observeMyWindows(
            onChange = { lista ->
                ventanas = lista

                if (lista.none { it.id == selectedWindowId }) {
                    selectedWindowId = lista.firstOrNull()?.id
                }

                mensajeFirebase = "Ventanas sincronizadas con Firebase"
            },
            onError = { error ->
                mensajeFirebase =
                    "Error Firebase: ${
                        error.localizedMessage ?: "sin detalles"
                    }"
            }
        )

        onDispose {
            listener?.remove()
        }
    }

    LaunchedEffect(selectedWindowId) {
        val id = selectedWindowId
        codigoVentana = ""

        if (id != null) {
            cargandoCodigo = true

            FirestoreRepository.createWindowInviteCode(
                windowId = id,
                onSuccess = { code ->
                    codigoVentana = code
                    cargandoCodigo = false
                },
                onError = { error ->
                    cargandoCodigo = false
                    mensajeFirebase =
                        "No se pudo cargar el código: ${
                            error.localizedMessage ?: "error desconocido"
                        }"
                }
            )
        } else {
            cargandoCodigo = false
        }
    }

    DisposableEffect(selectedWindowId) {
        val id = selectedWindowId

        if (id == null) {
            ventanaAbierta = false
            onDispose { }
        } else {
            val listener = FirestoreRepository.observeWindowState(
                windowId = id,
                onChange = { estado ->
                    ventanaAbierta = estado
                },
                onError = { error ->
                    mensajeFirebase =
                        "Error de estado: ${
                            error.localizedMessage ?: "desconocido"
                        }"
                }
            )

            onDispose {
                listener.remove()
            }
        }
    }

    DisposableEffect(selectedWindowId) {
        val id = selectedWindowId

        if (id == null) {
            programacion = VentanaProgramacion()
            mensajeProgramacion = "Selecciona una ventana"
            onDispose { }
        } else {
            val listener = FirestoreRepository.observeWindowSchedule(
                windowId = id,
                onChange = { nuevaProgramacion ->
                    programacion = nuevaProgramacion

                    if (nuevaProgramacion.closeAtMillis == null) {
                        mensajeProgramacion = "No hay cierre programado"
                        procesandoCierre = false
                    }
                },
                onError = { error ->
                    mensajeProgramacion =
                        "Error de programación: ${
                            error.localizedMessage ?: "desconocido"
                        }"
                }
            )

            onDispose {
                listener.remove()
            }
        }
    }

    val tiempoRestante = programacion.closeAtMillis?.let { cierre ->
        (cierre - horaActual).coerceAtLeast(0L)
    }

    LaunchedEffect(
        selectedWindowId,
        programacion.closeAtMillis,
        horaActual,
        ventanaAbierta
    ) {
        val id = selectedWindowId
        val cierre = programacion.closeAtMillis

        if (
            id != null &&
            cierre != null &&
            horaActual >= cierre &&
            ventanaAbierta &&
            !procesandoCierre
        ) {
            procesandoCierre = true
            mensajeProgramacion = "Cerrando ventana..."

            FirestoreRepository.setWindowState(
                windowId = id,
                isOpen = false,
                modo = "Programado",
                onSuccess = {
                    mensajeProgramacion = "Ventana cerrada correctamente"

                    FirestoreRepository.cancelWindowClose(
                        windowId = id,
                        onSuccess = {
                            mensajeProgramacion =
                                "Ventana cerrada correctamente"
                        },
                        onError = { error ->
                            mensajeProgramacion =
                                "Ventana cerrada, pero no se pudo limpiar la programación: ${
                                    error.localizedMessage ?: "error"
                                }"
                        }
                    )
                },
                onError = { error ->
                    procesandoCierre = false
                    mensajeProgramacion =
                        "Error al cerrar: ${
                            error.localizedMessage ?: "desconocido"
                        }"
                }
            )
        }
    }

    fun cambiarEstado(nuevoEstado: Boolean, modo: String) {
        val id = selectedWindowId

        if (id == null) {
            mensajeFirebase = "Primero crea o selecciona una ventana"
            return
        }

        if (guardando) return

        guardando = true

        FirestoreRepository.setWindowState(
            windowId = id,
            isOpen = nuevoEstado,
            modo = modo,
            onSuccess = {
                guardando = false
                mensajeFirebase = "Cambio guardado en Firebase"

                if (!nuevoEstado && programacion.closeAtMillis != null) {
                    FirestoreRepository.cancelWindowClose(
                        windowId = id,
                        onSuccess = {
                            mensajeProgramacion =
                                "Ventana cerrada; programación cancelada"
                        },
                        onError = { error ->
                            mensajeProgramacion =
                                "Ventana cerrada, pero no se pudo cancelar: ${
                                    error.localizedMessage ?: "error"
                                }"
                        }
                    )
                }
            },
            onError = { error ->
                guardando = false
                mensajeFirebase =
                    "No se guardó: ${
                        error.localizedMessage ?: "error desconocido"
                    }"
            }
        )
    }

    fun guardarEvento(accion: String, modo: String) {
        val id = selectedWindowId

        if (id == null) {
            mensajeFirebase = "Primero crea o selecciona una ventana"
            return
        }

        FirestoreRepository.addHistoryRecord(
            windowId = id,
            accion = accion,
            modo = modo,
            onSuccess = {},
            onError = { error ->
                mensajeFirebase =
                    "Error al guardar: ${
                        error.localizedMessage ?: "desconocido"
                    }"
            }
        )
    }

    fun iniciarProgramacion(minutos: Int) {
        val id = selectedWindowId

        if (id == null) {
            mensajeProgramacion = "Selecciona una ventana primero"
            return
        }

        if (minutos <= 0) {
            mensajeProgramacion = "Ingresa un tiempo mayor que cero"
            return
        }

        if (!ventanaAbierta) {
            mensajeProgramacion = "La ventana ya está cerrada"
            return
        }

        val cierreEn = System.currentTimeMillis() + minutos * 60_000L

        mensajeProgramacion = "Guardando programación..."
        procesandoCierre = false

        FirestoreRepository.scheduleWindowClose(
            windowId = id,
            closeAtMillis = cierreEn,
            onSuccess = {
                mensajeProgramacion = "Cierre programado en $minutos min"

                guardarEvento(
                    "Cierre programado en $minutos minutos",
                    "Programado"
                )
            },
            onError = { error ->
                mensajeProgramacion =
                    "No se pudo programar: ${
                        error.localizedMessage ?: "error desconocido"
                    }"
            }
        )
    }

    fun cancelarProgramacionCompartida() {
        val id = selectedWindowId

        if (id == null) {
            mensajeProgramacion = "Selecciona una ventana primero"
            return
        }

        FirestoreRepository.cancelWindowClose(
            windowId = id,
            onSuccess = {
                procesandoCierre = false
                mensajeProgramacion = "Programación cancelada"

                guardarEvento(
                    "Programación de cierre cancelada",
                    "Programado"
                )
            },
            onError = { error ->
                mensajeProgramacion =
                    "No se pudo cancelar: ${
                        error.localizedMessage ?: "error desconocido"
                    }"
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        FondoPaisajeMain()

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF061A2B).copy(alpha = 0.22f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(15.dp))

            Text(
                "VENTANASMART",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text("Panel de control", color = Color.White)

            Spacer(Modifier.height(18.dp))

            // MIS VENTANAS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "MIS VENTANAS",
                        color = AzulVentana,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Box {
                        OutlinedButton(
                            onClick = { menuVentanasAbierto = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                ventanaSeleccionada?.name
                                    ?: "Seleccionar ventana"
                            )
                        }

                        DropdownMenu(
                            expanded = menuVentanasAbierto,
                            onDismissRequest = {
                                menuVentanasAbierto = false
                            }
                        ) {
                            ventanas.forEach { ventana ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(ventana.name)
                                            Text(
                                                if (ventana.ownerId == uidActual) {
                                                    "Creada por esta cuenta"
                                                } else {
                                                    "Ventana compartida"
                                                },
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedWindowId = ventana.id
                                        menuVentanasAbierto = false
                                        mensajeFirebase =
                                            "Ventana seleccionada: ${ventana.name}"
                                    }
                                )
                            }
                        }
                    }

                    if (ventanas.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Todavía no tienes ventanas. Crea la primera abajo.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }

                    // NUEVO: BUSCADOR DE REDES WI-FI
                    Spacer(Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { mostrarSelectorWifi = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📶 BUSCAR DISPOSITIVOS WI-FI")
                    }

                    if (redWifiSeleccionada.isNotBlank()) {
                        Text(
                            "Red seleccionada: $redWifiSeleccionada",
                            color = AzulVentana,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        "La selección prepara la configuración futura del dispositivo.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = nombreNuevaVentana,
                        onValueChange = { nombreNuevaVentana = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nombre de la nueva ventana") },
                        placeholder = { Text("Ej.: Dormitorio") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val nombre = nombreNuevaVentana.trim()

                            if (nombre.isBlank()) {
                                mensajeFirebase =
                                    "Escribe un nombre para la ventana"
                            } else if (!creandoVentana) {
                                creandoVentana = true

                                FirestoreRepository.createWindow(
                                    name = nombre,
                                    onSuccess = { id ->
                                        selectedWindowId = id
                                        nombreNuevaVentana = ""
                                        creandoVentana = false
                                        mensajeFirebase =
                                            "Ventana creada correctamente"
                                    },
                                    onError = { error ->
                                        creandoVentana = false
                                        mensajeFirebase =
                                            "No se pudo crear: ${
                                                error.localizedMessage ?: "error"
                                            }"
                                    }
                                )
                            }
                        },
                        enabled = !creandoVentana,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CelesteVentana
                        )
                    ) {
                        Text(
                            if (creandoVentana) "CREANDO..."
                            else "AÑADIR VENTANA"
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // CÓDIGO PERMANENTE Y AGREGAR VENTANA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "CÓDIGO DE LA VENTANA",
                        color = AzulVentana,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Comparte este código para que otra cuenta agregue la misma ventana.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = codigoVentana,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Código permanente") },
                        placeholder = {
                            Text(
                                if (selectedWindowId == null) {
                                    "Selecciona una ventana"
                                } else {
                                    "Cargando código..."
                                }
                            )
                        },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (codigoVentana.isNotBlank()) {
                                val clipboard =
                                    context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "Código VentanaSmart",
                                        codigoVentana
                                    )
                                )

                                mensajeFirebase = "Código copiado"
                            }
                        },
                        enabled = codigoVentana.isNotBlank() &&
                                !cargandoCodigo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("COPIAR CÓDIGO")
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = Color.LightGray
                    )

                    Text(
                        "AGREGAR UNA VENTANA",
                        color = AzulVentana,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Introduce el código que te compartieron.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = codigoParaAgregar,
                        onValueChange = {
                            codigoParaAgregar = it.uppercase()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Código de otra ventana") },
                        placeholder = { Text("Ej.: VS-A7K9M2QX") },
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val code = codigoParaAgregar.trim()

                            if (code.isBlank()) {
                                mensajeFirebase =
                                    "Introduce el código de la ventana"
                            } else if (!agregandoVentana) {
                                agregandoVentana = true
                                mensajeFirebase = "Buscando ventana..."

                                FirestoreRepository.joinWindowByCode(
                                    code = code,
                                    onSuccess = { id ->
                                        agregandoVentana = false
                                        codigoParaAgregar = ""
                                        selectedWindowId = id
                                        mensajeFirebase =
                                            "Ventana agregada correctamente"
                                    },
                                    onError = { error ->
                                        agregandoVentana = false
                                        mensajeFirebase =
                                            "No se pudo agregar: ${
                                                error.localizedMessage
                                                    ?: "error desconocido"
                                            }"
                                    }
                                )
                            }
                        },
                        enabled = !agregandoVentana,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CelesteVentana
                        )
                    ) {
                        Text(
                            if (agregandoVentana) "AGREGANDO..."
                            else "AGREGAR VENTANA POR CÓDIGO"
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ESTADO DE LA VENTANA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.94f)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "ESTADO DE LA VENTANA",
                        color = AzulVentana,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        if (selectedWindowId == null) {
                            "CREA UNA VENTANA"
                        } else if (ventanaAbierta) {
                            "🟢 VENTANA ABIERTA"
                        } else {
                            "🔴 VENTANA CERRADA"
                        },
                        color = if (ventanaAbierta) {
                            Color(0xFF168A45)
                        } else {
                            Color(0xFFB3261E)
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(15.dp))

                    Button(
                        onClick = {
                            cambiarEstado(
                                !ventanaAbierta,
                                if (modoAutomatico) "Automático" else "Manual"
                            )
                        },
                        enabled = !guardando && selectedWindowId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AzulVentana
                        )
                    ) {
                        Text(
                            if (guardando) "GUARDANDO..."
                            else if (ventanaAbierta) "CERRAR VENTANA"
                            else "ABRIR VENTANA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        mensajeFirebase,
                        color = AzulVentana,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // MODO AUTOMÁTICO
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.94f)
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "MODO AUTOMÁTICO",
                            color = AzulVentana,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (modoAutomatico) "Activado" else "Desactivado",
                            color = Color.Gray
                        )
                    }

                    Switch(
                        checked = modoAutomatico,
                        onCheckedChange = { activar ->
                            modoAutomatico = activar
                            guardarEvento(
                                if (activar) "Automático activado"
                                else "Automático desactivado",
                                "Sistema"
                            )
                        },
                        enabled = selectedWindowId != null
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // PROGRAMAR CIERRE
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.94f)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        "PROGRAMAR CIERRE",
                        color = AzulVentana,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        mensajeProgramacion,
                        color = CelesteVentana,
                        fontWeight = FontWeight.Bold
                    )

                    if (programacion.closeAtMillis != null) {
                        Spacer(Modifier.height(8.dp))

                        val restante = tiempoRestante ?: 0L
                        val horas = restante / 3_600_000L
                        val minutos = (restante / 60_000L) % 60L
                        val segundos = (restante / 1_000L) % 60L

                        Text(
                            "Tiempo restante: %02d:%02d:%02d".format(
                                horas,
                                minutos,
                                segundos
                            ),
                            color = AzulVentana,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val programadoPor =
                            programacion.createdByEmail.ifBlank {
                                if (programacion.createdByUid.isNotBlank()) {
                                    "Usuario ${programacion.createdByUid.take(8)}"
                                } else {
                                    "Usuario no identificado"
                                }
                            }

                        Text(
                            "Programado por: $programadoPor",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )

                        Text(
                            if (restante > 0L) {
                                "La programación se comparte entre las cuentas."
                            } else {
                                "La hora programada ya llegó."
                            },
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Minutos rápidos",
                        color = AzulVentana,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 5, 10, 30).forEach { minutos ->
                            Button(
                                onClick = { iniciarProgramacion(minutos) },
                                enabled = selectedWindowId != null &&
                                        ventanaAbierta,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(
                                    horizontal = 2.dp,
                                    vertical = 8.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CelesteVentana
                                )
                            ) {
                                Text("$minutos m")
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Programar en horas",
                        color = AzulVentana,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { iniciarProgramacion(60) },
                            enabled = selectedWindowId != null &&
                                    ventanaAbierta,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AzulVentana
                            )
                        ) {
                            Text("1 HORA")
                        }

                        Button(
                            onClick = { iniciarProgramacion(120) },
                            enabled = selectedWindowId != null &&
                                    ventanaAbierta,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AzulVentana
                            )
                        ) {
                            Text("2 HORAS")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = minutosPersonalizados,
                        onValueChange = {
                            minutosPersonalizados = it.filter { c ->
                                c.isDigit()
                            }.take(5)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minutos personalizados") },
                        placeholder = { Text("Ejemplo: 45") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val minutos =
                                minutosPersonalizados.toIntOrNull()

                            if (minutos == null || minutos <= 0) {
                                mensajeProgramacion =
                                    "Escribe una cantidad válida de minutos"
                            } else {
                                iniciarProgramacion(minutos)
                            }
                        },
                        enabled = selectedWindowId != null &&
                                ventanaAbierta,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CelesteVentana
                        )
                    ) {
                        Text("PROGRAMAR TIEMPO PERSONALIZADO")
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            cancelarProgramacionCompartida()
                        },
                        enabled = selectedWindowId != null &&
                                programacion.closeAtMillis != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "CANCELAR PROGRAMACIÓN",
                            color = AzulVentana
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ESTADO DEL SISTEMA
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.94f)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "ESTADO DEL SISTEMA",
                        color = AzulVentana,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("☀ Sensor de luz: Simulación", color = Color.DarkGray)
                    Text("☁ Sensor de lluvia: Simulación", color = Color.DarkGray)
                    Text("● Datos de ventana: Firebase", color = Color.DarkGray)
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    selectedWindowId?.let { abrirHistorial(it) }
                },
                enabled = selectedWindowId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AzulVentana
                )
            ) {
                Text("VER HISTORIAL", color = Color.White)
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = cerrarSesion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB3261E)
                )
            ) {
                Text("CERRAR SESIÓN", color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
        }

        // NUEVO: ventana emergente del selector Wi-Fi
        if (mostrarSelectorWifi) {
            SelectorRedesWifi(
                alSeleccionar = { red ->
                    redWifiSeleccionada = red
                    mostrarSelectorWifi = false
                    mensajeFirebase =
                        "Red seleccionada: $red. Aún no se ha conectado ningún dispositivo."
                },
                alCerrar = {
                    mostrarSelectorWifi = false
                }
            )
        }
    }
}

@Composable
fun SelectorRedesWifi(
    alSeleccionar: (String) -> Unit,
    alCerrar: () -> Unit
) {
    val context = LocalContext.current

    var redes by remember { mutableStateOf<List<String>>(emptyList()) }
    var buscando by remember { mutableStateOf(false) }
    var mensaje by remember {
        mutableStateOf("Pulsa el botón para buscar redes cercanas.")
    }

    val wifiManager = remember {
        context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    fun actualizarRedes() {
        try {
            redes = wifiManager.scanResults
                .mapNotNull { resultado ->
                    resultado.SSID.takeIf { it.isNotBlank() }
                }
                .distinct()
                .sorted()

            mensaje = if (redes.isEmpty()) {
                "No hay redes visibles. Comprueba el Wi-Fi y la ubicación."
            } else {
                "Redes detectadas: ${redes.size}"
            }
        } catch (_: SecurityException) {
            mensaje = "Android no autorizó el acceso a las redes."
        }

        buscando = false
    }

    DisposableEffect(wifiManager) {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                actualizarRedes()
            }
        }

        val filtro = IntentFilter(
            WifiManager.SCAN_RESULTS_AVAILABLE_ACTION
        )

        ContextCompat.registerReceiver(
            context,
            receptor,
            filtro,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receptor)
            } catch (_: IllegalArgumentException) {
                // El receptor ya estaba desconectado.
            }
        }
    }

    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            buscarRedesWifi(
                wifiManager = wifiManager,
                alActualizarMensaje = { mensaje = it },
                alCambiarBuscando = { buscando = it },
                alActualizarRedes = { actualizarRedes() }
            )
        } else {
            mensaje =
                "Debes conceder el permiso de ubicación para escanear redes."
        }
    }

    fun buscar() {
        val tienePermiso = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!tienePermiso) {
            permisoLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            return
        }

        if (!wifiManager.isWifiEnabled) {
            mensaje = "Activa el Wi-Fi para buscar redes."
            return
        }

        buscarRedesWifi(
            wifiManager = wifiManager,
            alActualizarMensaje = { mensaje = it },
            alCambiarBuscando = { buscando = it },
            alActualizarRedes = { actualizarRedes() }
        )
    }

    Dialog(onDismissRequest = alCerrar) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "REDES WI-FI",
                    color = AzulVentana,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    mensaje,
                    color = Color.DarkGray,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { buscar() },
                    enabled = !buscando,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CelesteVentana
                    )
                ) {
                    Text(
                        if (buscando) "BUSCANDO..."
                        else "BUSCAR REDES REALES"
                    )
                }

                if (mensaje.contains("Activa el Wi-Fi")) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_WIFI_SETTINGS)
                            )
                        }
                    ) {
                        Text("ABRIR AJUSTES DE WI-FI")
                    }
                }

                if (
                    mensaje.contains("ubicación") ||
                    mensaje.contains("ubicacion") ||
                    mensaje.contains("No hay redes visibles")
                ) {
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        }
                    ) {
                        Text("ABRIR AJUSTES DE UBICACIÓN")
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (redes.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        redes.forEach { red ->
                            TextButton(
                                onClick = { alSeleccionar(red) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📶")
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        red,
                                        color = AzulVentana
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.LightGray)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = alCerrar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CERRAR")
                }
            }
        }
    }
}

private fun buscarRedesWifi(
    wifiManager: WifiManager,
    alActualizarMensaje: (String) -> Unit,
    alCambiarBuscando: (Boolean) -> Unit,
    alActualizarRedes: () -> Unit
) {
    try {
        if (!wifiManager.isWifiEnabled) {
            alActualizarMensaje("Activa el Wi-Fi para buscar redes.")
            alCambiarBuscando(false)
            return
        }

        alCambiarBuscando(true)
        alActualizarMensaje("Buscando redes Wi-Fi...")

        val inicioCorrecto = wifiManager.startScan()

        if (!inicioCorrecto) {
            alActualizarMensaje(
                "Android limitó el escaneo. Intentando mostrar resultados disponibles."
            )
            alActualizarRedes()
        }
    } catch (_: SecurityException) {
        alCambiarBuscando(false)
        alActualizarMensaje("No se pudo acceder a las redes Wi-Fi.")
    }
}

@Composable
fun FondoPaisajeMain() {
    Canvas(Modifier.fillMaxSize()) {
        val ancho = size.width
        val alto = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A3554),
                    Color(0xFF19799D),
                    Color(0xFF73C7D9),
                    Color(0xFFF6C98B)
                ),
                startY = 0f,
                endY = alto * 0.75f
            )
        )

        drawCircle(
            color = Color(0xFFFFE0A3),
            radius = 100f,
            center = Offset(ancho * 0.78f, alto * 0.20f)
        )

        drawCircle(
            color = Color(0xFFFFF0C2).copy(alpha = 0.30f),
            radius = 155f,
            center = Offset(ancho * 0.78f, alto * 0.20f)
        )

        val montanaLejana = Path().apply {
            moveTo(0f, alto * 0.64f)
            lineTo(ancho * 0.20f, alto * 0.45f)
            lineTo(ancho * 0.37f, alto * 0.60f)
            lineTo(ancho * 0.58f, alto * 0.40f)
            lineTo(ancho * 0.78f, alto * 0.59f)
            lineTo(ancho, alto * 0.44f)
            lineTo(ancho, alto)
            lineTo(0f, alto)
            close()
        }
        drawPath(montanaLejana, Color(0xFF4C7880))

        val montana = Path().apply {
            moveTo(0f, alto * 0.73f)
            lineTo(ancho * 0.25f, alto * 0.52f)
            lineTo(ancho * 0.44f, alto * 0.68f)
            lineTo(ancho * 0.65f, alto * 0.47f)
            lineTo(ancho * 0.85f, alto * 0.65f)
            lineTo(ancho, alto * 0.55f)
            lineTo(ancho, alto)
            lineTo(0f, alto)
            close()
        }
        drawPath(montana, Color(0xFF285B4A))

        drawRect(
            color = Color(0xFF153D32),
            topLeft = Offset(0f, alto * 0.78f),
            size = Size(ancho, alto * 0.22f)
        )

        dibujarArbolMain(ancho * 0.08f, alto * 0.76f, 1.1f)
        dibujarArbolMain(ancho * 0.18f, alto * 0.79f, 0.8f)
        dibujarArbolMain(ancho * 0.88f, alto * 0.77f, 1.2f)
        dibujarArbolMain(ancho * 0.96f, alto * 0.80f, 0.75f)
    }
}

fun DrawScope.dibujarArbolMain(
    x: Float,
    y: Float,
    escala: Float
) {
    drawRect(
        color = Color(0xFF51382B),
        topLeft = Offset(x - 8f * escala, y),
        size = Size(16f * escala, 75f * escala)
    )

    drawCircle(
        color = Color(0xFF174B38),
        radius = 34f * escala,
        center = Offset(x, y - 20f * escala)
    )

    drawCircle(
        color = Color(0xFF216044),
        radius = 27f * escala,
        center = Offset(x - 22f * escala, y - 5f * escala)
    )

    drawCircle(
        color = Color(0xFF2B7352),
        radius = 25f * escala,
        center = Offset(x + 22f * escala, y - 5f * escala)
    )
}