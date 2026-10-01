package com.mathmilo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mathmilo.data.repository.UserPreferences
import com.mathmilo.ui.MathMiloNavHost
import com.mathmilo.ui.theme.MathMiloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash is dismissed as soon as the first frame is ready — no artificial delay.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MathMiloApp).container
        setContent {
            val prefs by container.preferences.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
            MathMiloTheme(reducedMotion = prefs.reducedMotion) {
                MathMiloNavHost()
            }
        }
    }
}
