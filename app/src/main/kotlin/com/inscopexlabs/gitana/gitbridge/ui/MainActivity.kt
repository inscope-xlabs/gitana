package com.inscopexlabs.gitana.gitbridge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.inscopexlabs.gitana.gitbridge.ui.navigation.BridgeNavHost
import com.inscopexlabs.gitana.gitbridge.ui.theme.BridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BridgeTheme { BridgeNavHost() } }
    }
}
