package ar.libreta.reparto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ar.libreta.reparto.ui.AppViewModel
import ar.libreta.reparto.ui.LibretaApp
import ar.libreta.reparto.ui.TemaLibreta

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaLibreta {
                LibretaApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refrescarFecha()
    }
}
