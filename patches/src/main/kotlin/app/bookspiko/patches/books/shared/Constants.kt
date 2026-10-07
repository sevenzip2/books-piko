package app.bookspiko.patches.books.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    const val PLAY_BOOKS_PACKAGE = "com.google.android.apps.books"

    /**
     * Versions the fingerprints were verified against, newest first.
     * The trailing target without a version lets other releases be tried experimentally:
     * every patch fails loudly instead of patching the wrong code when a fingerprint no longer fits.
     */
    val COMPATIBILITY_PLAY_BOOKS = Compatibility(
        name = "Google Play Books",
        packageName = PLAY_BOOKS_PACKAGE,
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x1A73E8,
        targets = listOf(
            AppTarget(version = "2026.9.4.1 (386871)"),
            AppTarget(version = null, versionCodes = null, isExperimental = true),
        ),
    )

    const val EXTENSION_PACKAGE = "Lapp/morphe/extension/playbooks"
    const val GMS_EXTENSION_CLASS = "$EXTENSION_PACKAGE/gms/GmsCoreSupport;"
    const val FONT_EXTENSION_CLASS = "$EXTENSION_PACKAGE/fonts/ReaderFontPatch;"
    const val FONT_SETTINGS_ACTIVITY = "app.morphe.extension.playbooks.fonts.FontSettingsActivity"
}
