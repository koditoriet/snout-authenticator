package se.koditoriet.snout

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class AppStrings(private val ctx: Context) {
    val generic by lazy { Generic(ctx) }
    val setupScreen by lazy { SetupScreen(ctx) }
    val seedDisplayScreen by lazy { SeedDisplayScreen(ctx) }
    val seedInputScreen by lazy { SeedInputScreen(ctx) }
    val lockScreen by lazy { LockScreen(ctx) }
    val secretsScreen by lazy { SecretsScreen(ctx) }
    val addSecretScreens by lazy { AddSecretScreens(ctx) }
    val managePasskeysScreen by lazy { ManagePasskeysScreen(ctx) }
    val settingsScreen by lazy { SettingsScreen(ctx) }

    val totpSecretForm by lazy { TotpSecretForm(ctx) }

    val listView by lazy { ListView(ctx) }

    val viewModel by lazy { ViewModel(ctx) }

    val credentialProvider by lazy { CredentialProvider(ctx) }

    class Generic(ctx: Context) {
        val appName by ctx.s(R.string.app_name)
        val ok by ctx.s(R.string.generic_ok)
        val back by ctx.s(R.string.generic_back)
        val cancel by ctx.s(R.string.generic_cancel)
        val continueOn by ctx.s(R.string.generic_continue)
        val save by ctx.s(R.string.generic_save)
        val seconds by ctx.s(R.string.generic_seconds)
        val selectItem by ctx.s(R.string.generic_select_item)
        val thisIsIrrevocable by ctx.s(R.string.generic_this_is_irrevocable)
        val keyStorageStrongbox by ctx.s(R.string.generic_key_storage_strongbox)
        val keyStorageTee by ctx.s(R.string.generic_key_storage_tee)
        val keyStorageSoftware by ctx.s(R.string.generic_key_storage_software)
        val keyStorageUnknown by ctx.s(R.string.generic_key_storage_unknown)
        val dragToChangeOrder by ctx.s(R.string.generic_key_drag_to_change_order)
    }

    inner class SetupScreen(ctx: Context) {
        val welcome by lazy { ctx.s(R.string.setup_welcome, generic.appName) }

        val enableBackups by ctx.s(R.string.setup_enable_backups)
        val enableBackupsDescription by ctx.s(R.string.setup_enable_backups_description)

        val enableBackupsCardEnable by ctx.s(R.string.setup_enable_backups_card_enable)
        val enableBackupsCardEnableDescription by ctx.s(R.string.setup_enable_backups_card_enable_description)

        val enableBackupsCardDisable by ctx.s(R.string.setup_enable_backups_card_disable)
        val enableBackupsCardDisableDescription by ctx.s(R.string.setup_enable_backups_card_disable_description)

        val enableBackupsCardImport by ctx.s(R.string.setup_enable_backups_card_import)
        val enableBackupsCardImportDescription by ctx.s(R.string.setup_enable_backups_card_import_description)
    }

    class SeedDisplayScreen(ctx: Context) {
        val recoveryPhrase by ctx.s(R.string.seed_display_recovery_phrase)
        val writeThisDown by ctx.s(R.string.seed_display_write_this_down)
        val keepThemSafe by ctx.s(R.string.seed_display_keep_them_safe)
        val printAsQr by ctx.s(R.string.seed_display_print_qr)
        val printAsQrWarning by ctx.s(R.string.seed_display_print_qr_warning)
    }

    class SeedInputScreen(ctx: Context) {
        val enterRecoveryPhrase by ctx.s(R.string.seed_input_enter_recovery_phrase)
        val restoreVault by ctx.s(R.string.seed_input_restore_vault)
    }

    class SecretsScreen(private val ctx: Context) {
        val addSecret by ctx.s(R.string.secrets_add_secret)
        val filterPlaceholder by ctx.s(R.string.secrets_filter_placeholder)
        val lockScreen by ctx.s(R.string.secrets_lock_screen)
        val settings by ctx.s(R.string.secrets_settings)
        val generateOneTimeCode by ctx.s(R.string.secrets_generate_one_time_code)
        val copyCode by ctx.s(R.string.secrets_copy_one_time_code)
        val codeCopied by ctx.s(R.string.secrets_copied_one_time_code)

        val addSecretSheetHeading by ctx.s(R.string.secrets_add_secret_sheet_heading)
        val addSecretSheetDescription by ctx.s(R.string.secrets_add_secret_sheet_description)
        val addSecretSheetScanQrCode by ctx.s(R.string.secrets_add_secret_sheet_scan_qr_code)
        val addSecretSheetScanImage by ctx.s(R.string.secrets_add_secret_sheet_scan_image)
        val addSecretSheetImportFile by ctx.s(R.string.secrets_add_secret_sheet_import_file)
        val addSecretSheetEnterManually by ctx.s(R.string.secrets_add_secret_sheet_enter_manually)

        val actionsSheetNoAccount by ctx.s(R.string.secrets_actions_sheet_no_account)
        val actionsSheetEdit by ctx.s(R.string.secrets_actions_sheet_edit)
        val actionsSheetDelete by ctx.s(R.string.secrets_actions_sheet_delete)
        val actionsSheetDeleteWarning by ctx.s(R.string.secrets_actions_sheet_delete_warning)
    }

    class AddSecretScreens(ctx: Context) {
        val addSecret by ctx.s(R.string.add_secret_add_secret)
    }

    class LockScreen(ctx: Context) {
        val vaultLocked by ctx.s(R.string.locked_vault_locked)
        val tapToUnlock by ctx.s(R.string.locked_tap_to_unlock)
    }

    class ManagePasskeysScreen(ctx: Context) {
        val heading by ctx.s(R.string.manage_passkeys_heading)
        val filterPlaceholder by ctx.s(R.string.manage_passkeys_filter_placeholder)
        val actionsSheetEdit by ctx.s(R.string.manage_passkeys_actions_sheet_edit)
        val actionsSheetDelete by ctx.s(R.string.manage_passkeys_actions_sheet_delete)
        val actionsSheetDeleteWarning by ctx.s(R.string.manage_passkeys_actions_sheet_delete_warning)
    }

    class SettingsScreen(private val ctx: Context) {
        val heading by ctx.s(R.string.settings_heading)

        val managePasskeys by ctx.s(R.string.settings_manage_passkeys)
        val managePasskeysDescription by ctx.s(R.string.settings_manage_passkeys_description)

        val enableBackups by ctx.s(R.string.settings_enable_backups)
        val enableBackupsDescription by ctx.s(R.string.settings_enable_backups_description)
        val enableBackupsExport by ctx.s(R.string.settings_enable_backups_export)
        val enableBackupsDisableDialogConfirm by ctx.s(R.string.settings_enable_backups_disable_dialog_confirm)
        val enableBackupsDisableDialogText by ctx.s(R.string.settings_enable_backups_disable_dialog_text)

        val biometricLock by ctx.s(R.string.settings_biometric_lock)
        val biometricLockDescription by ctx.s(R.string.settings_biometric_lock_description)

        val lockOnMinimize by ctx.s(R.string.settings_lock_on_minimize)
        val lockOnMinimizeDescription by ctx.s(R.string.settings_lock_on_minimize_description)
        val lockOnMinimizeGracePeriod by ctx.s(R.string.settings_lock_on_minimize_grace_period)

        val screenSecurity by ctx.s(R.string.settings_screen_security)
        val screenSecurityDescription by ctx.s(R.string.settings_screen_security_description)

        val hideSecretsFromScreenReaders by
            ctx.s(R.string.settings_hide_secrets_from_screen_readers)
        val hideSecretsFromScreenReadersDescription by
            ctx.s(R.string.settings_hide_secrets_from_screen_readers_description)

        val enableDeveloperFeatures by
            ctx.s(R.string.settings_enable_developer_options)
        val enableDeveloperFeaturesDescription by
            ctx.s(R.string.settings_enable_developer_options_description)

        val keyStorageOverview by
            ctx.s(R.string.settings_key_storage_overview)
        val keyStorageOverviewDescription by
            ctx.s(R.string.settings_key_storage_overview_description)
        val keyStorageOverviewDescriptionSheetHeading by
            ctx.s(R.string.settings_key_storage_overview_sheet_heading)
        val keyStorageOverviewDescriptionSheetDescription by
            ctx.s(R.string.settings_key_storage_overview_sheet_description)
        val keyStorageOverviewDescriptionSheetBackupsDisabled by
            ctx.s(R.string.settings_key_storage_overview_sheet_backups_disabled)
        val keyStorageOverviewDescriptionSheetGradeExcellent by
            ctx.s(R.string.settings_key_storage_overview_sheet_grade_excellent)
        val keyStorageOverviewDescriptionSheetGradeGood by
            ctx.s(R.string.settings_key_storage_overview_sheet_grade_good)
        val keyStorageOverviewDescriptionSheetGradeWeak by
            ctx.s(R.string.settings_key_storage_overview_sheet_grade_weak)
        fun keyStorageOverviewDescriptionSheetDescription(storageClass: String) =
            ctx.s(R.string.settings_key_storage_overview_sheet_backup_keys_stored_in, storageClass)
        fun keyStorageOverviewDescriptionSheetSecretGroupGrade(
            groupSize: Int,
            totalSecrets: Int,
            storageClass: String,
        ) =
            ctx.resources.getQuantityString(
                R.plurals.settings_key_storage_overview_sheet_secret_group_grade,
                groupSize,
                groupSize,
                totalSecrets,
                storageClass,
            )
        fun keyStorageOverviewDescriptionSheetPasskeyGroupGrade(
            groupSize: Int,
            totalPasskeys: Int,
            storageClass: String,
        ) =
            ctx.resources.getQuantityString(
                R.plurals.settings_key_storage_overview_sheet_passkey_group_grade,
                groupSize,
                groupSize,
                totalPasskeys,
                storageClass,
            )

        val eraseData by ctx.s(R.string.settings_erase_data)
        val eraseDataDescription by ctx.s(R.string.settings_erase_data_description)
        val eraseDataDialogConfirm by ctx.s(R.string.settings_erase_data_dialog_confirm)
        val eraseDataDialogText by ctx.s(R.string.settings_erase_data_dialog_text)
    }

    class TotpSecretForm(ctx: Context) {
        val issuer by ctx.s(R.string.secret_form_issuer)
        val userName by ctx.s(R.string.secret_form_username)
        val secret by ctx.s(R.string.secret_form_secret)
        val digits by ctx.s(R.string.secret_form_digits)
        val period by ctx.s(R.string.secret_form_period)
        val algorithm by ctx.s(R.string.secret_form_algorithm)
        val showSecret by ctx.s(R.string.secret_form_show_secret)
        val hideSecret by ctx.s(R.string.secret_form_hide_secret)
        val currentlyUnusableWithScreenReader by ctx.s(R.string.secret_form_currently_unusable_with_screen_reader)
    }

    class ViewModel(private val ctx: Context) {
        val authUsePasskey by ctx.s(R.string.view_model_auth_use_passkey)
        val authUsePasskeySubtitle by ctx.s(R.string.view_model_auth_use_passkey_subtitle)

        val authUnlockVault by ctx.s(R.string.view_model_auth_unlock_vault)
        val authUnlockVaultSubtitle by ctx.s(R.string.view_model_auth_unlock_vault_subtitle)

        val authRevealCode by ctx.s(R.string.view_model_auth_reveal_code)
        val authRevealCodeSubtitle by ctx.s(R.string.view_model_auth_reveal_code_subtitle)

        fun authToggleBioprompt(active: Boolean) = when (active) {
            true -> R.string.view_model_auth_vault_bioprompt_enable
            false -> R.string.view_model_auth_vault_bioprompt_disable
        }.let { ctx.getString(it) }

        fun authToggleBiopromptSubtitle(active: Boolean) = when (active) {
            true -> R.string.view_model_auth_vault_bioprompt_enable_subtitle
            false -> R.string.view_model_auth_vault_bioprompt_disable_subtitle
        }.let { ctx.getString(it) }
    }

    class CredentialProvider(ctx: Context) {
        val authenticationActionTitle by ctx.s(R.string.credential_provider_authentication_action_title)
        val passkeyAlreadyExists by ctx.s(R.string.credential_provider_passkey_already_exists)
        val passkeyAlreadyExistsExplanation by ctx.s(R.string.credential_provider_passkey_already_exists_explanation)
        val unableToEstablishTrust by ctx.s(R.string.credential_provider_unable_to_establish_trust)
        val unableToEstablishTrustExplanation by ctx.s(R.string.credential_provider_unable_to_establish_trust_explanation)
        val editPasskeyDisplayName by ctx.s(R.string.credential_provider_edit_passkey_display_name)
        val passkeyDisplayName by ctx.s(R.string.credential_provider_passkey_display_name)
    }

    class ListView(private val ctx: Context) {
        val filterClear by ctx.s(R.string.list_view_filter_clear)

        fun sortAlphabetically(active: Boolean) = when (active) {
            true -> R.string.list_view_sort_alphabetically_active
            false -> R.string.list_view_sort_alphabetically_inactive
        }.let { ctx.getString(it) }

        fun filter(active: Boolean) = when (active) {
            true -> R.string.list_view_filter_active
            false -> R.string.list_view_filter_inactive
        }.let { ctx.getString(it) }

    }
}

private fun Context.s(id: Int): Lazy<String> = lazy { getString(id) }
private fun Context.s(id: Int, param: String): String = getString(id, param)

val appStrings: AppStrings
    @Composable
    get() = AppStrings(LocalContext.current)

val Context.appStrings: AppStrings
    get() = AppStrings(this)
