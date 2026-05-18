package com.example.soundproof_okmic

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

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
    external fun openAudio()
    external fun startAudio()
    external fun stopAudio()
    external fun setBufferSize(bufferSize: Int)
    external fun getAudioResults(): FloatArray?

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

    override fun onPause() {
        super.onPause()
        stopAudio()
    }
}

// === MAIN LAYOUT ===
@Composable
fun MainLayout(modifier: Modifier = Modifier, navController: NavController)
{
    var isRecording by remember { mutableStateOf(false) }
    var loudestDb by remember { mutableFloatStateOf(0.0f) }
    var lowestDb by remember { mutableFloatStateOf(0.0f) }
    var currentDb by remember { mutableFloatStateOf(0.0f) }
    var fftResults by remember { mutableStateOf(floatArrayOf()) }

    // Historia próbek i licznik czasu
    val dbHistory = remember { mutableStateListOf<Float>() }
    var totalSamples by remember { mutableLongStateOf(0L) }
    val maxHistorySize = 100

    // Necessary check of permissions to record from mic
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            isRecording = false
        }
    }

    val context = LocalContext.current
    val micChannelActivity = remember(context) {
        context.findActivity() as? MainActivity
    }

    LaunchedEffect(isRecording && micChannelActivity != null) {
        if (isRecording) {
            // Only if mic recording was allowed
            if (micChannelActivity?.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                micChannelActivity.openAudio()
                micChannelActivity.startAudio()
                dbHistory.clear()
                totalSamples = 0L
                while (isRecording) {
                    val results = micChannelActivity.getAudioResults()
                    if (results != null && results.size >= 3) {
                        currentDb = results[0]
                        loudestDb = results[1]
                        lowestDb = results[2]
                        if (results.size > 3) {
                            fftResults = results.sliceArray(3 until results.size)
                        }
                    }

                    dbHistory.add(currentDb)
                    totalSamples++
                    if (dbHistory.size > maxHistorySize) {
                        dbHistory.removeAt(0)
                    }

                    delay(100) // Update UI every 100ms
                }
            } else {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        } else {
            micChannelActivity?.stopAudio()
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
                Text("Current: $currentDb [dB]")
                Text("Loudest: $loudestDb [dB]")
                Text("Lowest: $lowestDb [dB]")
                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                AudioCanvas(isRecording = isRecording, dbHistory = dbHistory, totalSamples = totalSamples, fftResults = fftResults)
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
fun AudioCanvas(isRecording: Boolean, dbHistory: List<Float>, totalSamples: Long, fftResults: FloatArray = floatArrayOf())
{
    if(isRecording && dbHistory.isNotEmpty())
    {
        val strokeColor = MaterialTheme.colorScheme.primary
        val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        val textMeasurer = rememberTextMeasurer()
        val textStyle = TextStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
            val leftMargin = 50.dp.toPx()
            val bottomMargin = 40.dp.toPx()
            val graphWidth = size.width - leftMargin
            val graphHeight = size.height - bottomMargin
            
            val maxSamples = 100 
            val dx = graphWidth / (maxSamples - 1)
            
            val minDb = -100f
            val maxDb = 0f

            // --- PODZIAŁKA Y (dB) ---
            for (db in -100..0 step 20) {
                val y = graphHeight - ((db - minDb) / (maxDb - minDb) * graphHeight)
                drawLine(
                    color = gridColor,
                    start = Offset(leftMargin, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = "$db",
                    style = textStyle,
                    topLeft = Offset(5.dp.toPx(), y - 10.dp.toPx())
                )
            }

            // --- PODZIAŁKA X (Czas) ---
            val firstSampleIndex = totalSamples - dbHistory.size
            for (i in 0 until dbHistory.size) {
                val absoluteIndex = firstSampleIndex + i
                if (absoluteIndex >= 0 && absoluteIndex % 20 == 0L) { // Co 2 sekundy (20 * 100ms)
                    val x = leftMargin + (i * dx)
                    val timeSeconds = absoluteIndex / 10
                    
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, graphHeight),
                        strokeWidth = 1f
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${timeSeconds}s",
                        style = textStyle,
                        topLeft = Offset(x - 10.dp.toPx(), graphHeight + 5.dp.toPx())
                    )
                }
            }

            // --- WYKRES ---
            for (i in 0 until dbHistory.size - 1) {
                val startX = leftMargin + (i * dx)
                val startY = graphHeight - ((dbHistory[i] - minDb) / (maxDb - minDb) * graphHeight)
                
                val endX = leftMargin + ((i + 1) * dx)
                val endY = graphHeight - ((dbHistory[i + 1] - minDb) / (maxDb - minDb) * graphHeight)
                
                drawLine(
                    color = strokeColor,
                    start = Offset(startX, startY.coerceIn(0f, graphHeight)),
                    end = Offset(endX, endY.coerceIn(0f, graphHeight)),
                    strokeWidth = 4f
                )
            }

            // Osie
            drawLine(textStyle.color, Offset(leftMargin, 0f), Offset(leftMargin, graphHeight), 2f)
            drawLine(textStyle.color, Offset(leftMargin, graphHeight), Offset(size.width, graphHeight), 2f)
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
