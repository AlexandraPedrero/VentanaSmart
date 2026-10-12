package com.example.ventanasmart

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun currentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun register(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(
            email.trim(),
            password
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, null)
            } else {
                onResult(
                    false,
                    task.exception?.localizedMessage
                        ?: "No se pudo crear la cuenta"
                )
            }
        }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(
            email.trim(),
            password
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onResult(true, null)
            } else {
                onResult(
                    false,
                    task.exception?.localizedMessage
                        ?: "No se pudo iniciar sesión"
                )
            }
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun sendPasswordReset(
        email: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(true, null)
                } else {
                    onResult(
                        false,
                        task.exception?.localizedMessage
                            ?: "No se pudo enviar el correo"
                    )
                }
            }
    }
}