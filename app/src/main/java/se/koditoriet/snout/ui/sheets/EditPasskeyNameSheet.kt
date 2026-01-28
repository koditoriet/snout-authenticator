package se.koditoriet.snout.ui.sheets

import BottomSheetContextualHeader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.ui.components.PasskeyIcon
import se.koditoriet.snout.ui.components.sheet.BottomSheetGlobalHeader
import se.koditoriet.snout.ui.theme.BUTTON_FONT_SIZE
import se.koditoriet.snout.ui.theme.INPUT_FIELD_PADDING
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.ui.theme.SPACING_S
import se.koditoriet.snout.vault.Passkey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPasskeyNameSheet(
    prefilledDisplayName: String? = null,
    existingPasskey: Passkey? = null,
    onSave: (displayName: String) -> Unit,
) {
    val sheetStrings = appStrings.credentialProvider
    var displayName by remember {
        val text = prefilledDisplayName ?: existingPasskey?.displayName ?: ""
        val textFieldValue = TextFieldValue(
            text = text,
            selection = TextRange(text.length),
        )
        mutableStateOf(textFieldValue)
    }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val fieldModifier = Modifier
        .fillMaxWidth()
        .padding(INPUT_FIELD_PADDING)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SPACING_S),
    ) {
        if (existingPasskey != null) {
            BottomSheetContextualHeader(
                heading = existingPasskey.displayName,
                subheading = "${existingPasskey.rpId} \u2022 ${existingPasskey.userName}",
                icon = { PasskeyIcon() }
            )
        } else {
            BottomSheetGlobalHeader(
                heading = sheetStrings.editPasskeyDisplayName,
            )
        }

        OutlinedTextField(
            modifier = fieldModifier.focusRequester(focusRequester),
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text(sheetStrings.passkeyDisplayName) },
            isError = displayName.text.isBlank(),
            singleLine = true,
        )

        Spacer(Modifier.height(SPACING_L))

        Row(horizontalArrangement = Arrangement.Center, modifier = fieldModifier) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !displayName.text.isBlank(),
                onClick = { onSave(displayName.text) },
            ) {
                Text(appStrings.generic.save, fontSize = BUTTON_FONT_SIZE)
            }
        }
    }
}
