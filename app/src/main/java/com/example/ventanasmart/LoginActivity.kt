
package com.example.ventanasmart

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val usuario = auth.currentUser

        if (usuario != null) {
            FirestoreRepository.registerUserProfile(
                email = usuario.email.orEmpty(),
                onSuccess = { irAlMain() },
                onError = { irAlMain() }
            )
            return
        }

        setContent {
            MaterialTheme {
                LoginPantalla(
                    irAlMain = { irAlMain() }
                )
            }
        }
    }

    private fun irAlMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

@Composable
fun LoginPantalla(
    irAlMain: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }

    var correo by remember { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        FondoPaisaje()

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF061A2B).copy(alpha = 0.25f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(30.dp))

            Text(
                "VENTANASMART",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(5.dp))

            Text(
                "Control inteligente de tu hogar",
                color = Color.White,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(24.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.97f))
                    .padding(22.dp)
            ) {
                Text(
                    "Bienvenido",
                    color = Color(0xFF12304A),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Inicia sesión o crea tu cuenta",
                    color = Color(0xFF657786),
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = correo,
                    onValueChange = {
                        correo = it
                        error = ""
                        mensaje = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando,
                    singleLine = true,
                    label = { Text("Correo electrónico") },
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF39BDE8),
                        unfocusedBorderColor = Color(0xFFB8C7D1),
                        focusedLabelColor = Color(0xFF159AC4),
                        cursorColor = Color(0xFF159AC4)
                    )
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                        error = ""
                        mensaje = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando,
                    singleLine = true,
                    label = { Text("Contraseña") },
                    shape = RoundedCornerShape(15.dp),
                    visualTransformation = if (mostrarContrasena) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF39BDE8),
                        unfocusedBorderColor = Color(0xFFB8C7D1),
                        focusedLabelColor = Color(0xFF159AC4),
                        cursorColor = Color(0xFF159AC4)
                    )
                )

                Button(
                    onClick = {
                        mostrarContrasena = !mostrarContrasena
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        if (mostrarContrasena) {
                            "Ocultar contraseña"
                        } else {
                            "Mostrar contraseña"
                        },
                        color = Color(0xFF12304A),
                        fontSize = 13.sp
                    )
                }

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = Color(0xFFD32F2F),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (mensaje.isNotBlank()) {
                    Text(
                        mensaje,
                        color = Color(0xFF167447),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        when {
                            correo.isBlank() -> {
                                error = "Ingresa tu correo electrónico"
                            }

                            !Patterns.EMAIL_ADDRESS
                                .matcher(correo.trim())
                                .matches() -> {
                                error = "Ingresa un correo válido"
                            }

                            contrasena.isBlank() -> {
                                error = "Ingresa tu contraseña"
                            }

                            else -> {
                                error = ""
                                mensaje = ""
                                cargando = true

                                auth.signInWithEmailAndPassword(
                                    correo.trim(),
                                    contrasena
                                ).addOnCompleteListener { tarea ->
                                    if (tarea.isSuccessful) {
                                        FirestoreRepository.registerUserProfile(
                                            email = correo.trim(),
                                            onSuccess = {
                                                cargando = false
                                                irAlMain()
                                            },
                                            onError = {
                                                cargando = false
                                                irAlMain()
                                            }
                                        )
                                    } else {
                                        cargando = false
                                        error = tarea.exception
                                            ?.localizedMessage
                                            ?: "No se pudo iniciar sesión."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = !cargando,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF39BDE8)
                    )
                ) {
                    Text(
                        if (cargando) "CONECTANDO..." else "INICIAR SESIÓN",
                        color = Color(0xFF08263D),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        when {
                            correo.isBlank() -> {
                                error = "Ingresa tu correo electrónico"
                            }

                            !Patterns.EMAIL_ADDRESS
                                .matcher(correo.trim())
                                .matches() -> {
                                error = "Ingresa un correo válido"
                            }

                            contrasena.length < 6 -> {
                                error =
                                    "La contraseña debe tener al menos 6 caracteres"
                            }

                            else -> {
                                error = ""
                                mensaje = ""
                                cargando = true

                                auth.createUserWithEmailAndPassword(
                                    correo.trim(),
                                    contrasena
                                ).addOnCompleteListener { tarea ->
                                    if (tarea.isSuccessful) {
                                        FirestoreRepository.registerUserProfile(
                                            email = correo.trim(),
                                            onSuccess = {
                                                cargando = false
                                                irAlMain()
                                            },
                                            onError = { problema ->
                                                cargando = false
                                                error =
                                                    "Cuenta creada, pero no se pudo registrar el perfil: " +
                                                            (problema.localizedMessage
                                                                ?: "error desconocido")
                                            }
                                        )
                                    } else {
                                        cargando = false
                                        error = tarea.exception
                                            ?.localizedMessage
                                            ?: "No se pudo crear la cuenta."
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !cargando,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF12304A)
                    )
                ) {
                    Text(
                        "CREAR CUENTA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (
                            !Patterns.EMAIL_ADDRESS
                                .matcher(correo.trim())
                                .matches()
                        ) {
                            error =
                                "Escribe tu correo para recuperar la contraseña."
                        } else {
                            error = ""
                            mensaje = ""
                            cargando = true

                            auth.sendPasswordResetEmail(
                                correo.trim()
                            ).addOnCompleteListener { tarea ->
                                cargando = false

                                if (tarea.isSuccessful) {
                                    mensaje =
                                        "Si la cuenta existe, recibirás instrucciones por correo."
                                } else {
                                    error = tarea.exception
                                        ?.localizedMessage
                                        ?: "No se pudo enviar el correo."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !cargando,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        "¿Olvidaste tu contraseña?",
                        color = Color(0xFF12304A),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Tu hogar, más inteligente.",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FondoPaisaje() {
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
            color = Color(0xFFFFE0A3).copy(alpha = 0.9f),
            radius = 110f,
            center = Offset(ancho * 0.78f, alto * 0.25f)
        )

        drawCircle(
            color = Color(0xFFFFF0C2).copy(alpha = 0.35f),
            radius = 165f,
            center = Offset(ancho * 0.78f, alto * 0.25f)
        )

        dibujarNube(ancho * 0.18f, alto * 0.20f, 1f)
        dibujarNube(ancho * 0.70f, alto * 0.13f, 0.75f)

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

        val nieve1 = Path().apply {
            moveTo(ancho * 0.25f, alto * 0.52f)
            lineTo(ancho * 0.18f, alto * 0.59f)
            lineTo(ancho * 0.23f, alto * 0.57f)
            lineTo(ancho * 0.27f, alto * 0.61f)
            lineTo(ancho * 0.32f, alto * 0.59f)
            close()
        }
        drawPath(nieve1, Color.White.copy(alpha = 0.8f))

        val nieve2 = Path().apply {
            moveTo(ancho * 0.65f, alto * 0.47f)
            lineTo(ancho * 0.58f, alto * 0.56f)
            lineTo(ancho * 0.64f, alto * 0.53f)
            lineTo(ancho * 0.70f, alto * 0.58f)
            lineTo(ancho * 0.73f, alto * 0.55f)
            close()
        }
        drawPath(nieve2, Color.White.copy(alpha = 0.75f))

        drawRect(
            color = Color(0xFF153D32),
            topLeft = Offset(0f, alto * 0.78f),
            size = Size(ancho, alto * 0.22f)
        )

        dibujarArbol(ancho * 0.08f, alto * 0.76f, 1.1f)
        dibujarArbol(ancho * 0.18f, alto * 0.79f, 0.8f)
        dibujarArbol(ancho * 0.88f, alto * 0.77f, 1.2f)
        dibujarArbol(ancho * 0.96f, alto * 0.80f, 0.75f)

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF061A2B).copy(alpha = 0.30f)
                ),
                startY = alto * 0.60f,
                endY = alto
            )
        )
    }
}

fun DrawScope.dibujarNube(
    x: Float,
    y: Float,
    escala: Float
) {
    drawCircle(
        color = Color.White.copy(alpha = 0.72f),
        radius = 30f * escala,
        center = Offset(x, y)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.72f),
        radius = 42f * escala,
        center = Offset(x + 35f * escala, y - 8f * escala)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.72f),
        radius = 28f * escala,
        center = Offset(x + 70f * escala, y)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.72f),
        topLeft = Offset(x - 5f * escala, y),
        size = Size(80f * escala, 30f * escala)
    )
}

fun DrawScope.dibujarArbol(
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
        color = Color(0xFF2B7350),
        radius = 25f * escala,
        center = Offset(x + 22f * escala, y - 5f * escala)
    )
}
