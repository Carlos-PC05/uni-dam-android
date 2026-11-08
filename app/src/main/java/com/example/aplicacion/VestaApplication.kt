package com.example.aplicacion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Punto de creación del contenedor de dependencias Hilt. */
@HiltAndroidApp
class VestaApplication : Application()
