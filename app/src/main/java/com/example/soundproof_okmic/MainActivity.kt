package com.example.soundproof_okmic

import android.Manifest
import android.util.Log
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberSmartRecord
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.soundproof_okmic.ui.theme.SoundProof_OKmicTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression.color
import com.mapbox.mapboxsdk.style.expressions.Expression.get
import com.mapbox.mapboxsdk.style.expressions.Expression.has
import com.mapbox.mapboxsdk.style.expressions.Expression.not
import com.mapbox.mapboxsdk.style.expressions.Expression.step
import com.mapbox.mapboxsdk.style.expressions.Expression.stop
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.FillLayer

// For unified UI adjustments
data object InScreenOffset{
    val x = (-12).dp
    val y = (-12).dp
}

class MainActivity : ComponentActivity() {
    // create a supabase Client
    private val supabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        ) {
            install(Postgrest)
        }
    }

    // declare audioManager object to outlive the current activity
    private val audioManager by viewModels<AudioManager>()

    private lateinit var databaseManager: DatabaseManager

    // Main
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Init maps client
        Mapbox.getInstance(this)
        enableEdgeToEdge()
        // Init the database client object
        databaseManager = DatabaseManager(
            supabaseClient = supabaseClient,
            audioManager = audioManager
        )
        setContent {
            SoundProof_OKmicTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = AudioScreen
                )
                {
                    composable<AudioScreen>{
                        MainLayout(navController = navController, audioManager = audioManager, modifier = Modifier.fillMaxSize())
                    }
                    composable<MyCapturesScreen>{
                        MyCapturesLayout(navController, audioManager = audioManager, databaseManager = databaseManager, modifier = Modifier.fillMaxSize())
                    }
                    composable<MapsScreen>{
                        NoiseMapLayout(navController, audioManager = audioManager, databaseManager = databaseManager, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        audioManager.stopRecording()
    }

    override fun onDestroy() {
        super.onDestroy()
        databaseManager.cleanup()
    }
}

// === MAIN LAYOUT ===
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainLayout(modifier: Modifier = Modifier, navController: NavController, audioManager: AudioManager = viewModel())
{
    val audioStream by audioManager.audioStream.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val isFineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val isCoarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (isAudioGranted && (isFineLocationGranted || isCoarseLocationGranted)) {
            audioManager.startRecording()
        } else {
            audioManager.changeRecordingState(false)
        }
    }

    LaunchedEffect(audioStream.isRecording) {
        if (audioStream.isRecording) {
            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission && (hasFineLocationPermission || hasCoarseLocationPermission)) {
                audioManager.startRecording()
            } else {
                permissionLauncher.launch(arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        } else {
            val hasLocationPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocationPermission) {
                // Ask GPS for location when recording is stopped
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        audioManager.stopRecording(location.latitude, location.longitude)
                    } else {
                        // In case of random errors getting location
                        audioManager.stopRecording(0.0, 0.0)
                    }
                }.addOnFailureListener {
                    audioManager.stopRecording(0.0, 0.0)
                }
            } else {
                audioManager.stopRecording(0.0, 0.0)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopNavBar(navController, audioManager, Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) },
        floatingActionButton = {}
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
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.width(this@BoxWithConstraints.maxWidth.value.dp/2)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text("Current: ${audioStream.currentDb} [dB]")
                            Text("Loudest: ${audioStream.maxDb} [dB]")
                            Text("Lowest: ${audioStream.minDb} [dB]")
                        }
                    }
                    BoxWithConstraints(
                        modifier = Modifier.width(this@BoxWithConstraints.maxWidth.value.dp/2),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.End
                        ) {
                            FloatingModeButton(
                                audioStream = audioStream,
                                onModeChange = { isRecording, mode ->
                                    audioManager.changeRecordingState(isRecording)
                                    audioManager.changeRecordingMode(mode)
                                }
                            )
                            FloatingRecordButton(
                                audioStream = audioStream,
                                onRecordingChange = { audioManager.changeRecordingState(it) }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .fillMaxWidth(),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                AudioCanvasDB(isRecording = audioStream.isRecording, dbHistory = audioStream.dbHistory, totalSamples = audioStream.totalSamples)
                AudioCanvasFFT(isRecording = audioStream.isRecording, fftResults = audioStream.fourierResults.toList())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(navController: NavController, audioManager: AudioManager, modifier: Modifier = Modifier) {
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
            DropDownMenu(navController, audioManager = audioManager, modifier = Modifier.padding(14.dp))
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
fun DropDownMenu(navController: NavController, audioManager: AudioManager, modifier: Modifier = Modifier)
{
    val currentAudioMode by audioManager.audioStream.collectAsStateWithLifecycle()
    var expanded by remember {mutableStateOf(false)}
    var showSettings by remember {mutableStateOf(false)}

    if(showSettings)
    {
        SettingsDialogueWindow(
            onDismiss = { showSettings = false },
            audioManager = audioManager
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
                enabled = if(currentAudioMode.mode.name == "FREEROAM") true else false,
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

// Used for toggling recording mode
@Composable
fun FloatingModeButton(onModeChange: (Boolean, String) -> Unit, audioStream: AudioStream)
{
    Button(
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 8.dp
        ),
        shape = ButtonDefaults.shape,
        onClick = {
            val nextMode = if (audioStream.mode.name == "FREEROAM") "NOISETEST" else "FREEROAM"
            onModeChange(false, nextMode)
        },
        modifier = Modifier.offset(x= InScreenOffset.x, y = InScreenOffset.y)
    ) {
        Text(
            text = if (audioStream.mode.name == "FREEROAM") "FREE" else "TEST"
        )
        Icon(
            imageVector = Icons.Rounded.SaveAs,
            contentDescription = "Modechange_audio"
        )
    }
}

@Composable
fun FloatingRecordButton(onRecordingChange: (Boolean) -> Unit, audioStream: AudioStream)
{
    Button(
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 8.dp
        ),
        shape = ButtonDefaults.shape,
        onClick = { onRecordingChange(!audioStream.isRecording) },
        modifier = Modifier.offset(x= InScreenOffset.x, y = InScreenOffset.y)
    ) {
        Text(
            text = if (audioStream.isRecording) "STOP " else "REC "
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

            val minDb = -80f
            val maxDb = -20f

            val sampleRate = 48000f
            val nyquist = sampleRate / 2
            val minFreq = 20f
            val maxFreq = nyquist
            
            val logMin = log10(minFreq)
            val logMax = log10(maxFreq)

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

            // --- X AXIS (FREQUENCY) LOGARITHMIC ---
            val labels = mapOf(20f to "20", 100f to "100", 1000f to "1k", 5000f to "5k", 10000f to "10k", 20000f to "20k")
            labels.forEach { (freq, label) ->
                val x = leftMargin + ((log10(freq) - logMin) / (logMax - logMin)) * graphWidth
                if (x >= leftMargin) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x, 0f),
                        end = Offset(x, graphHeight),
                        strokeWidth = 1f
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        style = textStyle,
                        topLeft = Offset(x - 10.dp.toPx(), graphHeight + 5.dp.toPx())
                    )
                }
            }

            // --- SPECTRUM BARS ---
            val numBins = fftResults.size
            for (i in 0 until numBins) {
                val freqStart = (i.toFloat() / numBins) * nyquist
                val freqEnd = ((i + 1).toFloat() / numBins) * nyquist
                
                if (freqEnd < minFreq) continue
                
                val xStart = leftMargin + ((log10(max(minFreq, freqStart)) - logMin) / (logMax - logMin)) * graphWidth
                val xEnd = leftMargin + ((log10(freqEnd) - logMin) / (logMax - logMin)) * graphWidth
                
                val magnitude = fftResults[i]
                val normalizedMag = ((magnitude - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                val barHeight = normalizedMag * graphHeight

                drawRect(
                    color = strokeColor,
                    topLeft = Offset(xStart, graphHeight - barHeight),
                    size = Size(
                        width = (xEnd - xStart).coerceAtLeast(1f),
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
fun MyCapturesLayout(
    navController: NavController,
    audioManager: AudioManager,
    databaseManager: DatabaseManager,
    modifier: Modifier = Modifier
) {
    // List for raw data retrieval from supabase
    var measurementsList by remember { mutableStateOf<List<NoiseMeasurementDto>>(emptyList()) }
    var isCheckingDb by remember { mutableStateOf(true) }

    // Download data
    LaunchedEffect(Unit) {
        measurementsList = databaseManager.fetchAllMeasurements()
        isCheckingDb = false
    }

    Scaffold(
        topBar = { TopNavBar(navController, audioManager = audioManager, modifier = Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isCheckingDb) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(measurementsList) { item ->
                        RawMeasurementRow(dto = item)
                    }
                }
            }
        }
    }
}

@Composable
fun RawMeasurementRow(dto: NoiseMeasurementDto, onDelete: (NoiseMeasurementDto) -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timestamp: ${dto.timestamp_ms}",
                    style = MaterialTheme.typography.labelLarge
                )
                IconButton(onClick = { onDelete(dto) }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete measurement",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(text = "Location (WKT): ${dto.location}")
            Text(text = "Average dB: ${dto.avg_db}")
            Text(text = "Spectrogram samples: ${dto.spectrogram.size}")

            // Spectrogram draw!
            if (dto.spectrogram.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black)
                ) {
                    SpectrogramDrawing(
                        rawData = dto.spectrogram,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun SpectrogramDrawing(
    rawData: List<Float>,
    fWindowSize: Int = 2048,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.Gray, fontSize = 10.sp)

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height

        val paddingLeft = 55.dp.toPx()
        val paddingBottom = 25.dp.toPx()

        val graphWidth = totalWidth - paddingLeft
        val graphHeight = totalHeight - paddingBottom

        val frequencyBins = fWindowSize / 2
        if (frequencyBins <= 0 || rawData.isEmpty()) return@Canvas

        val timeSteps = rawData.size / frequencyBins
        if (timeSteps <= 0) return@Canvas

        val cellWidth = graphWidth / timeSteps

        val minDb = -100f
        val maxDb = -30f
        val dbRange = maxDb - minDb

        val maxHz = 24000.0
        val logMaxHz = ln(maxHz + 1.0)

        for (t in 0 until timeSteps) {
            val xPos = paddingLeft + (t * cellWidth)

            for (f in 0 until frequencyBins) {
                val flatIndex = t * frequencyBins + f
                if (flatIndex >= rawData.size) break

                val db = rawData[flatIndex]
                val intensity = ((db - minDb) / dbRange).coerceIn(0f, 1f)

                val currentHz = (f.toDouble() / frequencyBins) * maxHz
                val logCurrentHz = ln(currentHz + 1.0)
                val yPos = graphHeight - ((logCurrentHz / logMaxHz).toFloat() * graphHeight)

                val nextHz = ((f + 1).toDouble() / frequencyBins) * maxHz
                val logNextHz = ln(nextHz + 1.0)
                val nextYPos = graphHeight - ((logNextHz / logMaxHz).toFloat() * graphHeight)

                val cellHeight = (yPos - nextYPos).coerceAtLeast(1f)
                if (intensity > 0.05f) {
                    drawRect(
                        color = Color(
                            red = intensity,
                            green = intensity * 0.8f,
                            blue = (1f - intensity) * 0.5f,
                            alpha = 1f
                        ),
                        topLeft = Offset(xPos, nextYPos),
                        size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                    )
                }
            }
        }

        val freqLabels = listOf(
            100 to "100 Hz",
            500 to "500 Hz",
            1000 to "1 kHz",
            5000 to "5 kHz",
            10000 to "10 kHz",
            20000 to "20 kHz"
        )

        freqLabels.forEach { (hz, text) ->
            val logCurrentHz = ln(hz.toDouble() + 1.0)
            val normalizedY = (logCurrentHz / logMaxHz).toFloat().coerceIn(0f, 1f)
            val yPos = graphHeight - (normalizedY * graphHeight)

            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(paddingLeft, yPos),
                end = Offset(totalWidth, yPos),
                strokeWidth = 1f
            )

            val textLayoutResult = textMeasurer.measure(text, style = labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = labelStyle,
                topLeft = Offset(
                    x = paddingLeft - textLayoutResult.size.width - 8f,
                    y = yPos - (textLayoutResult.size.height / 2f)
                )
            )
        }

        val totalSeconds = timeSteps / 10f
        val stepIntervalSeconds = when {
            totalSeconds <= 5 -> 1
            totalSeconds <= 20 -> 2
            else -> 5
        }

        for (sec in 0..totalSeconds.toInt() step stepIntervalSeconds) {
            val correspondingTimeStep = sec * 10
            if (correspondingTimeStep >= timeSteps) break

            val xPos = paddingLeft + (correspondingTimeStep * cellWidth)

            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(xPos, 0f),
                end = Offset(xPos, graphHeight),
                strokeWidth = 1f
            )

            val text = "${sec}.0s"
            val textLayoutResult = textMeasurer.measure(text, style = labelStyle)

            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = labelStyle,
                topLeft = Offset(
                    x = xPos - (textLayoutResult.size.width / 2f),
                    y = graphHeight + 6f
                )
            )
        }

        drawLine(Color.Gray, Offset(paddingLeft, 0f), Offset(paddingLeft, graphHeight), 2f)
        drawLine(Color.Gray, Offset(paddingLeft, graphHeight), Offset(totalWidth, graphHeight), 2f)
    }
}

@Composable
fun NoiseMapLayout(
    navController: NavController,
    audioManager: AudioManager,
    databaseManager: DatabaseManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val warsawCenter = LatLng(52.2297, 21.0122)

    val mapView = remember { MapView(context) }

    var buildings by remember { mutableStateOf<List<BuildingDto>>(emptyList()) }
    var streets by remember { mutableStateOf<List<StreetDto>>(emptyList()) }
    var measurementsList by remember { mutableStateOf<List<NoiseMeasurementDto>>(emptyList()) }

    var featureCollection by remember { mutableStateOf<FeatureCollection?>(null) }

    var geoJsonSourceRef by remember { mutableStateOf<GeoJsonSource?>(null) }
    var mapStyleRef by remember { mutableStateOf<com.mapbox.mapboxsdk.maps.Style?>(null) }

    // -------------------------
    // LIFECYCLE
    // -------------------------
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // -------------------------
    // LOAD DATA
    // -------------------------
    LaunchedEffect(Unit) {
        measurementsList = databaseManager.fetchAllMeasurements()
        buildings = databaseManager.fetchBuildings()
        streets = databaseManager.fetchStreets()
    }

    // -------------------------
    // BUILD GEOJSON (OK)
    // -------------------------
    LaunchedEffect(measurementsList, buildings, streets) {

        val noiseFeatures = measurementsList.mapNotNull { dto ->
            val latLng = dto.toLatLng() ?: return@mapNotNull null

            Feature.fromGeometry(
                Point.fromLngLat(latLng.longitude, latLng.latitude)
            ).apply {
                addNumberProperty("avg_db", dto.avg_db)
            }
        }

        val buildingFeatures = buildings.mapNotNull { wkbToFeature(it.geom) }
        val streetFeatures = streets.mapNotNull { wkbToFeature(it.geom) }

        val all = noiseFeatures + buildingFeatures + streetFeatures

        featureCollection = FeatureCollection.fromFeatures(all)

        Log.d("GEO_DEBUG", "FINAL FEATURES: ${all.size}")
    }

    // -------------------------
    // UI
    // -------------------------
    Scaffold(
        topBar = { TopNavBar(navController, audioManager, Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(navController, Modifier.fillMaxWidth()) },
        modifier = modifier
    ) { innerPadding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {

            AndroidView(
                factory = {

                    mapView.apply {

                        getMapAsync { map ->

                            map.cameraPosition = CameraPosition.Builder()
                                .target(warsawCenter)
                                .zoom(11.0)
                                .build()

                            map.setStyle(
                                Style.Builder()
                                    .fromUri("https://demotiles.maplibre.org/style.json")
                            ) { style ->

                                mapStyleRef = style

                                val source = GeoJsonSource(
                                    "noise-source",
                                    FeatureCollection.fromFeatures(emptyList()),
                                    com.mapbox.mapboxsdk.style.sources.GeoJsonOptions()
                                        .withCluster(true)
                                        .withClusterRadius(60)
                                        .withClusterMaxZoom(14)
                                )

                                style.addSource(source)
                                geoJsonSourceRef = source

                                // -------------------------
                                // LAYERS
                                // -------------------------

                                val pointLayer = CircleLayer("noise-points", "noise-source")
                                pointLayer.setProperties(
                                    PropertyFactory.circleRadius(7f),
                                    PropertyFactory.circleColor(
                                        step(
                                            get("avg_db"),
                                            color(Color.Green.toArgb()),
                                            stop(50.0, color(Color.Yellow.toArgb())),
                                            stop(80.0, color(Color.Red.toArgb()))
                                        )
                                    )
                                )
                                pointLayer.setFilter(has("avg_db"))

                                val clusterLayer = CircleLayer("clusters", "noise-source")
                                clusterLayer.setProperties(
                                    PropertyFactory.circleRadius(18f),
                                    PropertyFactory.circleColor(color(Color.Gray.toArgb()))
                                )
                                clusterLayer.setFilter(has("point_count"))

                                val clusterText = SymbolLayer("cluster-text", "noise-source")
                                clusterText.setProperties(
                                    PropertyFactory.textField(get("point_count_abbreviated")),
                                    PropertyFactory.textSize(12f),
                                    PropertyFactory.textColor(color(Color.White.toArgb()))
                                )
                                clusterText.setFilter(has("point_count"))

                                style.addLayer(clusterLayer)
                                style.addLayer(clusterText)
                                style.addLayer(pointLayer)

                                // FIRST DATA PUSH
                                featureCollection?.let {
                                    source.setGeoJson(it)
                                }
                            }
                        }
                    }
                },
                update = {
                    val source = geoJsonSourceRef
                    val fc = featureCollection

                    if (source != null && fc != null) {
                        Log.d("MAP_DEBUG", "Updating GeoJSON: ${fc.features()?.size}")
                        source.setGeoJson(fc)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// === SETTINGS DIALOG MENU ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialogueWindow(onDismiss: () -> Unit, modifier: Modifier = Modifier, audioManager: AudioManager)
{
    // Get viewModel configSettings
    val currentConfigState by audioManager.configData.collectAsStateWithLifecycle()

    // For connecting UI updates to viewModel changes
    var sliderPosition: Float by remember { mutableFloatStateOf(currentConfigState.noiseGateThreshold) }
    var isGateEnabled: Boolean by remember { mutableStateOf(currentConfigState.noiseGateEnabled) }
    var algo: String by remember { mutableStateOf(currentConfigState.algo) }
    var fWindowSize: Int by remember { mutableIntStateOf(currentConfigState.fWindowSize) }
    var bufferSize: Int by remember { mutableIntStateOf(currentConfigState.bufferSize) }

    // Dropdown Menus controls
    var expandedBufferMenu by remember { mutableStateOf(false) }
    var expandedWindowMenu by remember { mutableStateOf(false) }
    var expandedAlgoMenu by remember { mutableStateOf(false) }

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
                    text = "Remember to apply your changes for them to take effect!"
                )

                // NOISE GATE
                Text(text = "Enable Noise Gate")
                Checkbox(
                    checked = isGateEnabled,
                    onCheckedChange = {isGateEnabled = it}
                )
                if(isGateEnabled)
                {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { newValue ->
                            sliderPosition = newValue
                        },
                        valueRange = -80f..0f
                    )
                    Text("$sliderPosition dB")
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
                            Text("$bufferSize")
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedBufferMenu,
                            onDismissRequest = {expandedBufferMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("512") },
                                onClick = { bufferSize = 512; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("1024") },
                                onClick = { bufferSize = 1024; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("2048") },
                                onClick = { bufferSize = 2048; expandedBufferMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("4096") },
                                onClick = { bufferSize = 4096; expandedBufferMenu = false }
                            )
                        }
                    }
                }

                // WINDOW SIZE
                LaunchedEffect(bufferSize)
                {
                    if (bufferSize < fWindowSize) {
                        fWindowSize = bufferSize
                    }
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
                            Text("$fWindowSize")
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedWindowMenu,
                            onDismissRequest = {expandedWindowMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("512") },
                                onClick = { fWindowSize = 512; expandedWindowMenu = false }
                            )
                            if(bufferSize >= 1024){
                                DropdownMenuItem(
                                    text = { Text("1024") },
                                    onClick = { fWindowSize = 1024; expandedWindowMenu = false }
                                )
                            }
                            if(bufferSize >= 2048) {
                                DropdownMenuItem(
                                    text = { Text("2048") },
                                    onClick = { fWindowSize= 2048; expandedWindowMenu = false }
                                )
                            }
                            if(bufferSize >= 4096) {
                                DropdownMenuItem(
                                    text = { Text("4096") },
                                    onClick = { fWindowSize = 4096; expandedWindowMenu = false }
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
                            Text(algo)
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = "")
                        }

                        DropdownMenu(
                            expanded = expandedAlgoMenu,
                            onDismissRequest = {expandedAlgoMenu = false}
                        ){
                            DropdownMenuItem(
                                text = { Text("Hann") },
                                onClick = { algo = "Hann"; expandedAlgoMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Hamming") },
                                onClick = { algo = "Hamming"; expandedAlgoMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Blackman") },
                                onClick = { algo = "Blackman"; expandedAlgoMenu = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        audioManager.stopRecording()
                        audioManager.setNoiseGateEnabled(isGateEnabled)
                        audioManager.setNoiseGateThreshold(sliderPosition)
                        audioManager.changeBufferSize(bufferSize)
                        audioManager.changeFWindowSize(fWindowSize)
                        audioManager.setAlgo(algo)
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
