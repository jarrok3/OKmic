package com.example.soundproof_okmic

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.FiberSmartRecord
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.selects.select
import kotlinx.serialization.Serializable
import kotlin.math.exp

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

// VIEWMODEL FOR STORING CONFIGURATION SETTINGS
// Onaudiostart should read from these to create the AudioStream with specified parameters
class ConfigurationSettings{

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
    external fun setFWindowSize(fwindowSize: Int)
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

    // Sample history and time counter
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
            if (micChannelActivity?.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
                    ++totalSamples
                    if (dbHistory.size > maxHistorySize) {
                        dbHistory.removeAt(0)
                    }

                    delay(100) // Update UI every 100ms
                }
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                AudioCanvasDB(isRecording = isRecording, dbHistory = dbHistory, totalSamples = totalSamples)
                AudioCanvasFFT(isRecording = isRecording, fftResults = fftResults.toList())
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
    var showSettings by remember {mutableStateOf(false)}

    if(showSettings)
    {
        SettingsDialogueWindow(
            onDismiss = { showSettings = false }
        )
    }

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
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "MyCaptures"
                    )
                },
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
            DropdownMenuItem(
                modifier = Modifier.fillMaxWidth(),
                text = { Text("Settings") },
                onClick = {
                    expanded = false
                    showSettings = true
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings"
                    )
                },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary),
            )
            HorizontalDivider()
            DropdownMenuItem(
                modifier = Modifier.fillMaxWidth(),
                text = { Text("About/Help") },
                onClick = {  },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "About/Help"
                    )
                },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary),
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
}

@Composable
fun AudioCanvasDB(isRecording: Boolean, dbHistory: List<Float>, totalSamples: Long)
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

            // --- Y AXIS (RELATIVE SOUND LEVEL) ---
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

            // --- X AXIS (TIME) ---
            val firstSampleIndex = totalSamples - dbHistory.size
            for (i in 0 until dbHistory.size) {
                val absoluteIndex = firstSampleIndex + i
                if (absoluteIndex >= 0 && absoluteIndex % 20 == 0L) {
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

            // --- FIGURE ---
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

            // Axis lines
            drawLine(textStyle.color, Offset(leftMargin, 0f), Offset(leftMargin, graphHeight), 2f)
            drawLine(textStyle.color, Offset(leftMargin, graphHeight), Offset(size.width, graphHeight), 2f)
        }
    }
}

@Composable
fun AudioCanvasFFT(isRecording: Boolean, fftResults: List<Float>)
{
    if(isRecording && fftResults.isNotEmpty())
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

            val barWidth = graphWidth / fftResults.size

            val minDb = -80f
            val maxDb = -20f

            // --- Y AXIS (RELATIVE SOUND LEVEL) ---
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

            // --- X AXIS (FREQUENCY) ---
            val labels = listOf("0", "5k", "10k", "15k", "20k")
            labels.forEachIndexed { index, label ->
                val x = leftMargin + (index.toFloat() / (labels.size - 1)) * graphWidth
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = textStyle,
                    topLeft = Offset(x - 10.dp.toPx(), graphHeight + 5.dp.toPx())
                )
            }

            // --- SPECTRUM BARS ---
            for (i in fftResults.indices) {
                val x = leftMargin + (i * barWidth)
                val magnitude = fftResults[i]
                val normalizedMag = ((magnitude - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                val barHeight = normalizedMag * graphHeight

                drawRect(
                    color = strokeColor,
                    topLeft = Offset(x, graphHeight - barHeight),
                    size = Size(
                        width = barWidth.coerceAtLeast(1f),
                        height = barHeight
                    )
                )
            }

            // Axis lines
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

// === SETTINGS DIALOG MENU ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialogueWindow(onDismiss: () -> Unit, modifier: Modifier = Modifier)
{
    var enableNoiseGate by remember{ mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var expandedBufferMenu by remember { mutableStateOf(false) }
    var selectedBufferSize by remember { mutableIntStateOf(1024) }
    var expandedWindowMenu by remember { mutableStateOf(false) }
    var selectedWindowSize by remember { mutableIntStateOf(1024) }
    var expandedAlgoMenu by remember { mutableStateOf(false) }
    var selectedAlgo by remember { mutableStateOf("Hann") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            windowTitle = "Settings"
        )
    ) {
        Surface(
            modifier = modifier.fillMaxWidth().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ){
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            )
            {
                Text(
                    text = "Remember to apply your changes before leaving this window"
                )

                // NOISE GATE
                Text(text = "Enable Noise Gate")
                Checkbox(
                    checked = enableNoiseGate,
                    onCheckedChange = {enableNoiseGate = it}
                )
                if(enableNoiseGate)
                {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        valueRange = -80f..0f
                    )
                }

                // BUFFER SIZE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select buffer size: ")
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { expandedBufferMenu = !expandedBufferMenu }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("$selectedBufferSize")
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedBufferMenu,
                            onDismissRequest = {expandedBufferMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("512") },
                                onClick = { selectedBufferSize = 512; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("1024") },
                                onClick = { selectedBufferSize = 1024; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("2048") },
                                onClick = { selectedBufferSize = 2048; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("4096") },
                                onClick = { selectedBufferSize = 4096; expandedBufferMenu = false }
                            )
                        }
                    }
                }

                // WINDOW SIZE
                if(selectedBufferSize<selectedWindowSize)
                {
                    selectedWindowSize = selectedBufferSize
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select window size: ")
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { expandedWindowMenu = !expandedWindowMenu }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("$selectedWindowSize")
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedWindowMenu,
                            onDismissRequest = {expandedWindowMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("512") },
                                onClick = { selectedWindowSize = 512; expandedWindowMenu = false }
                            )
                            if(selectedBufferSize>=1024){
                                DropdownMenuItem(
                                    text = { Text("1024") },
                                    onClick = { selectedWindowSize = 1024; expandedWindowMenu = false }
                                )
                            }
                            if(selectedBufferSize>=2048) {
                                DropdownMenuItem(
                                    text = { Text("2048") },
                                    onClick = { selectedWindowSize = 2048; expandedWindowMenu = false }
                                )
                            }
                            if(selectedBufferSize>=4096) {
                                DropdownMenuItem(
                                    text = { Text("4096") },
                                    onClick = { selectedWindowSize = 4096; expandedWindowMenu = false }
                                )
                            }
                        }
                    }
                }

                // WINDOW ALGORITHM
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Select windowing algorithm: ")
                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedAlgoMenu = !expandedAlgoMenu }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(selectedAlgo)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedAlgoMenu,
                            onDismissRequest = {expandedAlgoMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("Hann") },
                                onClick = { selectedAlgo = "Hann"; expandedAlgoMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Hamming") },
                                onClick = { selectedAlgo = "Hamming"; expandedAlgoMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Blackman") },
                                onClick = { selectedAlgo = "Blackman"; expandedAlgoMenu = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("Apply Changes")
                }
            }
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
