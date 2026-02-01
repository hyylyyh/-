package com.jungleadventure.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jungleadventure.shared.AndroidAssetResourceReader
import com.jungleadventure.shared.GameApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GameApp(resourceReader = AndroidAssetResourceReader(this))
        }
    }
}