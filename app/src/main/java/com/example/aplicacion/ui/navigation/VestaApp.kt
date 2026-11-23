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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import com.example.aplicacion.R
import com.example.aplicacion.model.UserProfile
import com.example.aplicacion.ui.auth.*
import com.example.aplicacion.ui.inicio.HomeScreen
import com.example.aplicacion.ui.inicio.InitialBalanceViewModel
import com.example.aplicacion.ui.ajustes.ProfileScreen
import com.example.aplicacion.ui.movimientos.CashStatus
import com.example.aplicacion.ui.movimientos.CashSummary
import com.example.aplicacion.ui.movimientos.MovementFormScreen
import com.example.aplicacion.ui.movimientos.MovementsScreen
import com.example.aplicacion.ui.movimientos.MovementsViewModel
import kotlinx.coroutines.flow.first

/** Separa los grafos público y privado: Atrás nunca recupera una pantalla de otra sesión. */
@Composable
fun VestaApp(
    viewModel: AuthViewModel = hiltViewModel(),
    balanceViewModel: @Composable () -> InitialBalanceViewModel = { hiltViewModel() },
    cashViewModel: @Composable (NavBackStackEntry) -> MovementsViewModel = { hiltViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.access) {
        AccessStatus.CHECKING, AccessStatus.FAILED -> AccessCheckScreen(state, viewModel::restoreSession, viewModel::signOut)
        AccessStatus.SIGNED_OUT -> AuthNavigation(state, viewModel)
        AccessStatus.INCOMPLETE -> CompleteProfileScreen(state, viewModel::updateName, viewModel::completeProfile, viewModel::signOut)
        AccessStatus.READY -> state.profile?.let { profile ->
            key(profile.uid) { MainNavigation(profile, viewModel::signOut, balanceViewModel, cashViewModel) }
        }
    }
}

/** Mantiene login y registro separados para retirar contraseñas al abandonar un formulario. */
@Composable
private fun AuthNavigation(state: AuthUiState, viewModel: AuthViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Login) {
        composable<Login> {
            AuthScreen(state, false, viewModel::updateName, viewModel::updateEmail,
                onSubmit = { password, _ -> viewModel.signIn(password) },
                onPasswordReset = viewModel::sendPasswordReset,
                onSwitch = { viewModel.clearErrors(); nav.navigate(Register) { launchSingleTop = true } })
        }
        composable<Register> {
            AuthScreen(state, true, viewModel::updateName, viewModel::updateEmail,
                onSubmit = viewModel::register,
                onSwitch = { viewModel.clearErrors(); nav.popBackStack() })
        }
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

/** Mantiene el saldo en Inicio y las demás secciones en destinos independientes. */
@Composable
private fun MainNavigation(
    profile: UserProfile,
    onSignOut: () -> Unit,
    balanceViewModel: @Composable () -> InitialBalanceViewModel,
    cashViewModel: @Composable (NavBackStackEntry) -> MovementsViewModel,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val labels = listOf("Inicio", "Movimientos", "Inversiones", "Ajustes")
    val icons = listOf(R.drawable.ic_home, R.drawable.ic_movements, R.drawable.ic_investments, R.drawable.ic_profile)
    val selected = when {
        entry?.destination?.hasRoute<Movements>() == true ||
            entry?.destination?.hasRoute<NewMovement>() == true -> 1
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
            composable<Home> { entry ->
                val movements = cashViewModel(remember(entry) { nav.getBackStackEntry(nav.graph.id) })
                val cashState by movements.state.collectAsStateWithLifecycle()
                val homeViewModel = balanceViewModel()
                val balanceState by homeViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(cashState.balance) {
                    cashState.balance?.let(homeViewModel::acceptConfirmedBalance)
                }
                LaunchedEffect(profile.uid) {
                    if (cashState.balance == null) homeViewModel.load(profile.uid)
                }
                LaunchedEffect(balanceState.balance) {
                    if (balanceState.balance != null) {
                        movements.state.first { !it.busy }
                        movements.load(profile.uid)
                    }
                }
                ScrollableContent {
                    HomeScreen(
                        profile = profile,
                        state = balanceState,
                        onAmountChange = homeViewModel::updateAmount,
                        onDateChange = homeViewModel::updateDate,
                        onSave = { homeViewModel.save(profile.uid) },
                        onRetry = { homeViewModel.load(profile.uid) },
                        onProfile = { nav.navigate(Settings) },
                        cashSummary = { balance ->
                            CashSummary(balance, cashState, movements::selectMonth) { movements.load(profile.uid) }
                        },
                    )
                }
            }
            composable<Movements> { entry ->
                val movements = cashViewModel(remember(entry) { nav.getBackStackEntry(nav.graph.id) })
                val cashState by movements.state.collectAsStateWithLifecycle()
                LaunchedEffect(profile.uid) { movements.load(profile.uid) }
                MovementsScreen(cashState, movements, profile.uid) {
                    nav.navigate(NewMovement) { launchSingleTop = true }
                }
            }
            composable<NewMovement> { entry ->
                val movements = cashViewModel(remember(entry) { nav.getBackStackEntry(nav.graph.id) })
                val cashState by movements.state.collectAsStateWithLifecycle()
                LaunchedEffect(profile.uid) {
                    if (cashState.movements == null) movements.load(profile.uid)
                }
                ScrollableContent {
                    if (cashState.balance == null || cashState.movements == null) {
                        Column(Modifier.padding(24.dp)) {
                            CashStatus(cashState) { movements.load(profile.uid) }
                            TextButton(onClick = { nav.popBackStack() }) { Text("Volver a Movimientos") }
                        }
                    } else {
                        MovementFormScreen(cashState, movements, profile.uid) {
                            if (nav.currentBackStackEntry == entry) nav.popBackStack()
                        }
                    }
                }
            }
            composable<Investments> { PendingScreen("Inversiones") }
            composable<Settings> { ScrollableContent { ProfileScreen(profile, onSignOut) } }
        }
    }
}

/** Limita la anchura y permite recorrer el contenido privado con texto grande. */
@Composable
private fun ScrollableContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
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
