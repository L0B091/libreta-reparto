package ar.libreta.reparto.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Pestania(val titulo: String, val icono: ImageVector) {
    HOY("Hoy", Icons.Filled.DateRange),
    SALDOS("Saldos", Icons.Filled.List),
    CLIENTES("Clientes", Icons.Filled.Person),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

@Composable
fun LibretaApp(vm: AppViewModel) {
    var seleccion by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.mensajes.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Pestania.entries.forEachIndexed { i, p ->
                    NavigationBarItem(
                        selected = seleccion == i,
                        onClick = { seleccion = i },
                        icon = { Icon(p.icono, contentDescription = p.titulo) },
                        label = { Text(p.titulo) },
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (Pestania.entries[seleccion]) {
            Pestania.HOY -> HoyScreen(vm, mod)
            Pestania.SALDOS -> SaldosScreen(vm, mod)
            Pestania.CLIENTES -> ClientesScreen(vm, mod)
            Pestania.AJUSTES -> AjustesScreen(vm, mod)
        }
    }
}
