package se.koditoriet.snout.ui.components

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.codec.isValidBase32
import se.koditoriet.snout.ui.components.SecretVisibility.Hidden
import se.koditoriet.snout.ui.components.SecretVisibility.Visible
import se.koditoriet.snout.ui.primaryHint
import se.koditoriet.snout.ui.theme.BUTTON_FONT_SIZE
import se.koditoriet.snout.ui.theme.INPUT_FIELD_PADDING
import se.koditoriet.snout.ui.theme.SPACING_L
import se.koditoriet.snout.vault.NewTotpSecret
import se.koditoriet.snout.vault.TotpAlgorithm

@Composable
inline fun <reified T : TotpSecretFormResult> TotpSecretForm(
    padding: PaddingValues,
    metadata: NewTotpSecret.Metadata? = null,
    hideSecretsFromAccessibility: Boolean,
    crossinline onSave: (T) -> Unit,
) {
    val formStrings = appStrings.totpSecretForm
    var issuer by remember { mutableStateOf(metadata?.issuer ?: "") }
    var account by remember { mutableStateOf(metadata?.account ?: "") }
    var secretDataFormState by remember { mutableStateOf(SecretDataFormState()) }

    val fieldModifier = Modifier
        .fillMaxWidth()
        .padding(INPUT_FIELD_PADDING)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            modifier = fieldModifier,
            value = issuer,
            onValueChange = { issuer = it },
            label = { Text(formStrings.issuer) },
            isError = issuer.isBlank(),
        )
        OutlinedTextField(
            modifier = fieldModifier,
            value = account,
            onValueChange = { account = it },
            label = { Text(formStrings.userName) },
        )

        // TODO: this part is _really_ ugly...
        val metadataIsValid = issuer.isNotBlank()
        val (secretDataIsValid, saveData) = if (T::class == TotpSecretFormResult.TotpSecret::class) {
            SecretDataPartialForm(
                fieldModifier = fieldModifier,
                hideSecretsFromAccessibility = hideSecretsFromAccessibility,
                secretDataFormState = secretDataFormState,
                onChange = { secretDataFormState = it },
            )
            Pair(secretDataFormState.isValid) { metadata: NewTotpSecret.Metadata ->
                val newTotpSecret = NewTotpSecret(
                    metadata = metadata,
                    secretData = secretDataFormState.toSecretData(),
                )
                val result = TotpSecretFormResult.TotpSecret(newTotpSecret)
                onSave(result as T)
            }
        } else {
            Pair(true) { metadata: NewTotpSecret.Metadata ->
                onSave(TotpSecretFormResult.TotpMetadata(metadata) as T)
            }
        }

        Spacer(Modifier.height(SPACING_L))

        Row(horizontalArrangement = Arrangement.Center, modifier = fieldModifier) {
            Button(
                enabled = secretDataIsValid && metadataIsValid,
                onClick = {
                    val metadata = NewTotpSecret.Metadata(
                        issuer = issuer.trim(),
                        account = account.trim().ifBlank { null },
                    )
                    saveData(metadata)
                },
            ) {
                Text(appStrings.generic.save, fontSize = BUTTON_FONT_SIZE)
            }
        }
    }
}

@Composable
fun SecretDataPartialForm(
    fieldModifier: Modifier,
    hideSecretsFromAccessibility: Boolean,
    secretDataFormState: SecretDataFormState,
    onChange: (SecretDataFormState) -> Unit,
) {
    val screenStrings = appStrings.totpSecretForm
    var secretVisibility by remember { mutableStateOf(Hidden) }

    val accessibilityManager = LocalContext
        .current
        .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val isA11yEnabled = accessibilityManager.isEnabled

    if (isA11yEnabled && hideSecretsFromAccessibility) {
        Text(
            modifier = Modifier
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                }
                .padding(horizontal = INPUT_FIELD_PADDING),
            text = screenStrings.currentlyUnusableWithScreenReader,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primaryHint,
        )
    }

    OutlinedTextField(
        modifier = fieldModifier.semantics {
            isSensitiveData = true
            if (hideSecretsFromAccessibility) {
                hideFromAccessibility()
            }
        },
        value = secretDataFormState.secret,
        onValueChange = { onChange(secretDataFormState.copy(secret = it.trim())) },
        label = { Text(screenStrings.secret) },
        isError = !secretDataFormState.secretIsValid,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
        ),
        visualTransformation = secretVisibility.visualTransformation,
        trailingIcon = {
            secretVisibility.TrailingIcon {
                secretVisibility = secretVisibility.toggle()
            }
       },
    )
    OutlinedTextField(
        modifier = fieldModifier,
        value = secretDataFormState.digits,
        onValueChange = { onChange(secretDataFormState.copy(digits = it.filter { c -> c.isDigit() })) },
        label = { Text(screenStrings.digits) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = !secretDataFormState.digitsIsValid,
    )
    OutlinedTextField(
        modifier = fieldModifier,
        value = secretDataFormState.period,
        onValueChange = { onChange(secretDataFormState.copy(period = it.filter { c -> c.isDigit() })) },
        label = { Text(screenStrings.period) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = !secretDataFormState.periodIsValid,
    )
    Dropdown<TotpAlgorithm>(
        label = screenStrings.algorithm,
        selectedItem = secretDataFormState.algorithm,
        modifier = fieldModifier,
        onItemSelected = { onChange(secretDataFormState.copy(algorithm = it)) },
    )
}

sealed interface TotpSecretFormResult {
    data class TotpSecret(val secret: NewTotpSecret) : TotpSecretFormResult
    data class TotpMetadata(val metadata: NewTotpSecret.Metadata) : TotpSecretFormResult
}

private enum class SecretVisibility(
    val icon: ImageVector,
    val iconDescription: @Composable () -> String,
    val visualTransformation: VisualTransformation,
) {
    Visible(
        icon = Icons.Default.VisibilityOff,
        iconDescription = { appStrings.totpSecretForm.hideSecret },
        visualTransformation = VisualTransformation.None,
    ),
    Hidden(
        icon = Icons.Default.Visibility,
        iconDescription = { appStrings.totpSecretForm.showSecret },
        visualTransformation = PasswordVisualTransformation(),
    ),
}

private fun SecretVisibility.toggle(): SecretVisibility =
    when (this) {
        Visible -> Hidden
        Hidden -> Visible
    }

@Composable
private fun SecretVisibility.TrailingIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription(),
        )
    }
}

data class SecretDataFormState(
    val secret: String = "",
    val digits: String = "6",
    val period: String = "30",
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
) {
    val secretIsValid: Boolean
        get() = secret.isNotBlank() && isValidBase32(secret)

    val digitsIsValid: Boolean
        get() = digits.toIntOrNull()?.let { it > 0 } ?: false

    val periodIsValid: Boolean
        get() = period.toIntOrNull()?.let { it > 0 } ?: false

    val isValid: Boolean
        get() = secretIsValid && digitsIsValid && periodIsValid

    fun toSecretData(): NewTotpSecret.SecretData =
        NewTotpSecret.SecretData(
            secret = secret.toCharArray(),
            digits = digits.toInt(),
            period = period.toInt(),
            algorithm = algorithm,
        )
}
