package com.example.soundproof_okmic

import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.rounded.FiberSmartRecord
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.soundproof_okmic.ui.theme.SoundProof_OKmicTheme

data object InScreenOffset{
    val x = (-12).dp
    val y = (-12).dp
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SoundProof_OKmicTheme {
                MainLayout(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun MainLayout(modifier: Modifier = Modifier)
{
    Scaffold(
        modifier = modifier,
        topBar = { TopNavBar(Modifier.fillMaxWidth()) },
        bottomBar = { BottomNavBar(Modifier.fillMaxWidth()) },
        floatingActionButton = { FloatingRecordButton() }
    ) { innerPadding ->
        Column {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .offset(-InScreenOffset.x, -InScreenOffset.y)
            ) {
                Text("Loudest: ")
                Text("Lowest: ")
            }
            AudioCanvas()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(modifier: Modifier = Modifier)
{
    CenterAlignedTopAppBar(
        actions = {
            DropDownMenu(modifier = Modifier.padding(14.dp))
        },
        title = { Text("Audio capture") },
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondary
        )
    )
}


@Composable
fun DropDownMenu(modifier: Modifier = Modifier)
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
                onClick = { },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun BottomNavBar(modifier: Modifier = Modifier)
{
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "Heatmap"
                )
            },
            label = {  }
        )
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Heatmap"
                )
            },
            label = {  }
        )
    }
}

@Composable
fun FloatingRecordButton()
{
    var isRecording by remember { mutableStateOf(false) }

    Button(
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 8.dp
        ),
        shape = ButtonDefaults.shape,
        onClick = { isRecording = !isRecording },
        modifier = Modifier.offset(x= InScreenOffset.x, y = InScreenOffset.y)
    ) {
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
fun AudioCanvas(isRecording: Boolean = false)
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

@Preview(showBackground = true)
@Composable
fun MainScreen() {
    SoundProof_OKmicTheme {
        MainLayout()
    }
}