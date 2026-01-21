package se.koditoriet.snout.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import se.koditoriet.snout.ui.components.LoadingSpinner


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoadingScreen(isLoadingState: MutableState<Boolean>) {
    if (isLoadingState.value) {
        Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
                    .testTag("LoadingScreen")
            ) {
                LoadingSpinner()
            }
        }
    }
}