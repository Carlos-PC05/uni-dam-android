package com.example.aplicacion.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import com.example.aplicacion.R
import com.example.aplicacion.model.UserProfile
import com.example.aplicacion.ui.auth.*
import com.example.aplicacion.ui.inicio.HomeScreen
import com.example.aplicacion.ui.ajustes.ProfileScreen

/** Separa los grafos público y privado: Atrás nunca recupera una pantalla de otra sesión. */
@Composable
fun VestaApp(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.access) {
        AccessStatus.CHECKING, AccessStatus.FAILED -> AccessCheckScreen(state, viewModel::restoreSession, viewModel::signOut)
        AccessStatus.SIGNED_OUT -> AuthNavigation(state, viewModel)
        AccessStatus.INCOMPLETE -> CompleteProfileScreen(state, viewModel::updateName, viewModel::completeProfile, viewModel::signOut)
        AccessStatus.READY -> state.profile?.let { profile ->
            key(profile.uid) { MainNavigation(profile, viewModel::signOut) }
        }
    }
}

/** Separa login/registro y superpone la recuperación sin retirar las credenciales del login. */
@Composable
private fun AuthNavigation(state: AuthUiState, viewModel: AuthViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Login) {
        composable<Login> {
            AuthScreen(
                state, false, viewModel::updateName, viewModel::updateEmail,
                onSubmit = { password, _ -> viewModel.signIn(password) },
                onSwitch = {
                    viewModel.clearErrors()
                    nav.navigate(Register) { launchSingleTop = true }
                },
                onRecovery = viewModel::openRecovery,
            )
        }
        composable<Register> {
            AuthScreen(
                state, true, viewModel::updateName, viewModel::updateEmail,
                onSubmit = viewModel::register,
                onSwitch = {
                    viewModel.clearErrors()
                    nav.popBackStack()
                },
                onRecovery = viewModel::openRecovery,
            )
        }
    }
    state.recovery?.let { recovery ->
        PasswordRecoveryDialog(
            state = recovery,
            busy = state.busy,
            onEmailChange = viewModel::updateRecoveryEmail,
            onSubmit = viewModel::recoverPassword,
            onClose = viewModel::closeRecovery,
        )
    }
}

/** Muestra espera o fallo de verificación sin confundirlos con ausencia de perfil. */
@Composable
private fun AccessCheckScreen(state: AuthUiState, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            if (state.access == AccessStatus.CHECKING) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Comprobando tu sesión…")
            } else {
                Text("No hemos podido comprobar tu perfil", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                AuthError(state.message)
                Button(onClick = onRetry, enabled = !state.busy) { Text("Reintentar") }
                TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Volver al acceso") }
            }
        }
    }
}

/** Conecta las cuatro secciones previstas; las financieras se identifican como pendientes. */
@Composable
private fun MainNavigation(profile: UserProfile, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val labels = listOf("Inicio", "Movimientos", "Inversiones", "Ajustes")
    val icons = listOf(R.drawable.ic_home, R.drawable.ic_movements, R.drawable.ic_investments, R.drawable.ic_profile)
    val selected = when {
        entry?.destination?.hasRoute<Movements>() == true -> 1
        entry?.destination?.hasRoute<Investments>() == true -> 2
        entry?.destination?.hasRoute<Settings>() == true -> 3
        else -> 0
    }
    Scaffold(bottomBar = {
        NavigationBar {
            labels.forEachIndexed { index, label ->
                NavigationBarItem(selected = selected == index,
                    icon = { Icon(painterResource(icons[index]), contentDescription = null) },
                    label = { Text(label, maxLines = 1) },
                    onClick = {
                        val route = when (index) { 1 -> Movements; 2 -> Investments; 3 -> Settings; else -> Home }
                        nav.navigate(route) {
                            popUpTo(nav.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    })
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = Home, modifier = Modifier.padding(padding)) {
            composable<Home> {
                ScrollableContent { HomeScreen(profile) { nav.navigate(Settings) } }
            }
            composable<Movements> { PendingScreen("Movimientos") }
            composable<Investments> { PendingScreen("Inversiones") }
            composable<Settings> { ScrollableContent { ProfileScreen(profile, onSignOut) } }
        }
    }
}

/** Limita la anchura y permite recorrer el contenido privado con texto grande. */
@Composable
private fun ScrollableContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { content() }
    }
}

/** Identifica expresamente un destino cuya funcionalidad pertenece a otro ticket. */
@Composable
private fun PendingScreen(title: String) {
    ScrollableContent {
        Column(Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text("Pendiente de implementación. Esta sección se incorporará en su ticket correspondiente.")
        }
    }
}
