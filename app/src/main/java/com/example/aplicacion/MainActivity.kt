package com.example.aplicacion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aplicacion.ui.navigation.VestaApp
import com.example.aplicacion.ui.theme.AplicacionTheme
import dagger.hilt.android.AndroidEntryPoint

/** Actividad única que aloja la navegación Compose y recibe las dependencias Hilt. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Instala el tema y la raíz de acceso sin realizar consultas desde la actividad. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AplicacionTheme { VestaApp() } }
    }
}
