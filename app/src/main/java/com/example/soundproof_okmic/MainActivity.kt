package com.example.soundproof_okmic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.FiberSmartRecord
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.soundproof_okmic.ui.theme.SoundProof_OKmicTheme
import kotlinx.serialization.Serializable

data object InScreenOffset{
    val x = (-12).dp
    val y = (-12).dp
}

class MainActivity : ComponentActivity() {
    // External OBOE lib inclusion
    companion object {
        init {
            System.loadLibrary("native-audio-lib")
        }
    }

    // External functions for Audio handling
    external fun startAudio(): Boolean
    external fun stopAudio()
    external fun getAmplitude(): Float

    // Main
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundProof_OKmicTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = AudioScreen
                )
                {
                    composable<AudioScreen>{
                        MainLayout(navController = navController, modifier = Modifier.fillMaxSize())
                    }
                    composable<MyCapturesScreen>{
                        MyCapturesLayout(navController, modifier = Modifier.fillMaxSize())
                    }
                    composable<MapsScreen>{
                        NoiseMapLayout(navController, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// === MAIN LAYOUT ===
@Composable
fun MainLayout(modifier: Modifier = Modifier, navController: NavController)
{
    var isRecording by remember { mutableStateOf(false) }
    var loudestDb by remember { mutableFloatStateOf(0.0f) }
    var lowestDb by remember { mutableFloatStateOf(0.0f) }

    // Necessary check of permissions to record from mic
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            isRecording = false
        }
    }

    val micChannelActivity = androidx.compose.ui.platform.LocalContext.current as MainActivity

    LaunchedEffect(isRecording) {
        if (isRecording) {
            // Only if mic recording was allowed
            if (micChannelActivity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                micChannelActivity.startAudio()
                while (isRecording) {
                    val amp = micChannelActivity.getAmplitude()
                    if (amp > 0) {
                        val db = 20 * kotlin.math.log10(amp.toDouble()).toFloat()
                        if (db > loudestDb) loudestDb = db
                        if (lowestDb == 0.0f || db < lowestDb) lowestDb = db
                    }
                    delay(16) // ~60fps update
                }
            } else {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        } else {
            micChannelActivity.stopAudio()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopNavBar(navController, Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) },
        floatingActionButton = { FloatingRecordButton(isRecording = isRecording, onRecordingChange = { isRecording = it }) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopStart
        ) {
            val horizontalMargin = maxWidth * 0.05f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalMargin), // Apply margin
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loudest: $loudestDb [dB]")
                Text("Lowest: $lowestDb [dB]")
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                AudioCanvas(isRecording = isRecording)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(navController: NavController, modifier: Modifier = Modifier) {
    // Remember route
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val title = when {
        currentDestination?.hasRoute(AudioScreen::class) == true -> "Audio capture"
        currentDestination?.hasRoute(MyCapturesScreen::class) == true -> "My Captures"
        currentDestination?.hasRoute(MapsScreen::class) == true -> "Noise Map"
        else -> "SoundProof"
    }

    CenterAlignedTopAppBar(
        actions = {
            DropDownMenu(navController, modifier = Modifier.padding(14.dp))
        },
        title = { Text(title) },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondary
        )
    )
}


@Composable
fun DropDownMenu(navController: NavController, modifier: Modifier = Modifier)
{
    var expanded by remember {mutableStateOf(false)}

    Box(
        modifier = modifier
    )
    {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Nav"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ){
            DropdownMenuItem(
                modifier = Modifier.fillMaxWidth(),
                text = { Text("My Captures") },
                onClick = {
                    expanded = false
                    navController.navigate(MyCapturesScreen) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun BottomNavBar(navController: NavController, modifier: Modifier = Modifier)
{
    // Observing NavController state to determine the value for the "selected" property
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.hasRoute(MapsScreen::class) } == true,
            onClick = {
                navController.navigate(MapsScreen) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Heatmap"
                )
            },
            label = {  }
        )
        NavigationBarItem(
            selected = currentDestination?.hierarchy?.any { it.hasRoute(AudioScreen::class) } == true,
            onClick = {
                navController.navigate(AudioScreen) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Audio"
                )
            },
            label = {  }
        )
    }
}

@Composable
fun FloatingRecordButton(isRecording: Boolean, onRecordingChange: (Boolean) -> Unit)
{

    Button(
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 8.dp
        ),
        shape = ButtonDefaults.shape,
        onClick = { onRecordingChange(!isRecording) },
        modifier = Modifier.offset(x= InScreenOffset.x, y = InScreenOffset.y)
    ) {
        Text(
            text = "REC "
        )
        Icon(
            imageVector = Icons.Rounded.FiberSmartRecord,
            contentDescription = "Record_audio"
        )
    }

    // The refresh event is processed by the ViewModel that is in charge
    // of the UI's business logic.
//        Button(onClick = { viewModel.refreshNews() }) {
//            Text("Refresh data")
//        }

}

@Composable
fun AudioCanvas(isRecording: Boolean)
{
    if(isRecording)
    {
        Canvas(
            modifier = Modifier
                .padding(0.dp)
                .fillMaxWidth()
                .height(400.dp)
                .background(
                    color = MaterialTheme.colorScheme.background
                )
        )
        {
            drawLine(
                color = Color.Blue,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = 10f
            )
        }
    }
}


// === MY CAPTURES LAYOUT ===
@Composable
fun MyCapturesLayout(navController: NavController, modifier : Modifier = Modifier) {
    Scaffold(
        topBar = { TopNavBar(navController, Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "Dzień Dobry Polsko witam serdecznie!"
            )
        }
    }
}

// === NOISE MAP LAYOUT ===
@Composable
fun NoiseMapLayout(navController: NavController, modifier: Modifier = Modifier){
    Scaffold(
        topBar = { TopNavBar(navController, Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Text(
                text = "Dzień Dobry Polsko witam serdecznie!"
            )
        }
    }
}

// Screen navigation
@Serializable
object AudioScreen

@Serializable
object MyCapturesScreen

@Serializable
object MapsScreen

// Previews
@Preview(showBackground = true)
@Composable
fun MainScreen() {
    SoundProof_OKmicTheme {
        MainLayout(navController = rememberNavController())
    }
}
