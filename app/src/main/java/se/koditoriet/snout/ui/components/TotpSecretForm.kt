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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.sp
import se.koditoriet.snout.appStrings
import se.koditoriet.snout.codec.isValidBase32
import se.koditoriet.snout.ui.components.SecretVisibility.Hidden
import se.koditoriet.snout.ui.components.SecretVisibility.Visible
import se.koditoriet.snout.ui.primaryHint
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

    val fieldModifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(padding),
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

        val metadataIsValid = issuer.isNotBlank()
        val (secretDataIsValid, saveData) = if (T::class == TotpSecretFormResult.TotpSecret::class) {
            secretDataPartialForm(fieldModifier, hideSecretsFromAccessibility) {
                onSave(it as T)
            }
        } else {
            Pair(true) { metadata: NewTotpSecret.Metadata ->
                onSave(TotpSecretFormResult.TotpMetadata(metadata) as T)
            }
        }

        Spacer(Modifier.height(16.dp))

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
                Text(appStrings.generic.save, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun secretDataPartialForm(
    fieldModifier: Modifier,
    hideSecretsFromAccessibility: Boolean,
    onSave: (TotpSecretFormResult.TotpSecret) -> Unit,
): Pair<Boolean, (NewTotpSecret.Metadata) -> Unit> {
    val screenStrings = appStrings.totpSecretForm
    var secret by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("6") }
    var period by remember { mutableStateOf("30") }
    var selectedAlgorithm by remember { mutableStateOf(TotpAlgorithm.SHA1) }
    var secretVisibility by remember { mutableStateOf(SecretVisibility.Hidden) }

    val secretIsValid = secret.isNotBlank() and isValidBase32(secret)
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
                .padding(horizontal = 16.dp),
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
        value = secret,
        onValueChange = { secret = it.trim() },
        label = { Text(screenStrings.secret) },
        isError = !secretIsValid,
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
        value = digits,
        onValueChange = { digits = it.filter { c -> c.isDigit() } },
        label = { Text(screenStrings.digits) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = digits.isBlank(),
    )
    OutlinedTextField(
        modifier = fieldModifier,
        value = period,
        onValueChange = { period = it.filter { c -> c.isDigit() } },
        label = { Text(screenStrings.period) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        isError = period.isBlank(),
    )
    Dropdown<TotpAlgorithm>(
        label = screenStrings.algorithm,
        selectedItem = selectedAlgorithm,
        modifier = fieldModifier,
        onItemSelected = { selectedAlgorithm = it },
    )
    val inputs = listOf(
        secretIsValid,
        digits.isNotBlank(),
        period.isNotBlank(),
    )
    return Pair(inputs.all { it }) { metadata: NewTotpSecret.Metadata ->
        val secretData = NewTotpSecret.SecretData(
            secret = secret.toCharArray(),
            digits = digits.toInt(),
            period = period.toInt(),
            algorithm = selectedAlgorithm,
        )
        val secret = TotpSecretFormResult.TotpSecret(
            NewTotpSecret(metadata, secretData)
        )
        onSave(secret)
    }
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
