package com.example.soundproof_okmic

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.tooling.preview.Preview
import com.example.soundproof_okmic.ui.theme.SoundProof_OKmicTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar

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

}

@Composable
fun TopNavBar()
{

}

@Composable
fun BottomNavBar()
{

}

@Composable
fun FloatingRecordButton()
{
    var expanded = remember { mutableStateOf(false) }

    Column {
        Text("Some text")
        if (expanded.value) {
            Text("More details")
        }

        Button(
            // The expand details event is processed by the UI that
            // modifies this composable's internal state.
            onClick = { expanded.value = !expanded.value }
        ) {
            val expandText = if (expanded.value) "Collapse" else "Expand"
            Text("$expandText details")
        }

        // The refresh event is processed by the ViewModel that is in charge
        // of the UI's business logic.
//        Button(onClick = { viewModel.refreshNews() }) {
//            Text("Refresh data")
//        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SoundProof_OKmicTheme {
        MainLayout()
    }
}