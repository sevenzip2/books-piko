package app.bookspiko.patches.books.reader.copy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string

internal object ReaderCopyMenuFingerprint : Fingerprint(
    returnType = "L",
    parameters = listOf("L", "L", "I"),
    filters = listOf(
        resourceLiteral(ResourceType.STRING, "menu_copy"),
        resourceLiteral(ResourceType.DRAWABLE, "gs_content_copy_vd_theme_24"),
    ),
)

internal object ReaderCopyActionFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    filters = listOf(
        methodCall(smali = "Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;"),
        methodCall(smali = "Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V"),
        methodCall(smali = "Ljava/text/NumberFormat;->getPercentInstance()Ljava/text/NumberFormat;"),
    ),
)

internal object CopyQuotaInfoFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("CopyQuotaInfo(volumeId=")),
)

internal object InnerVolumeEntityFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("InnerVolumeEntity(volumeId=")),
)

internal object VolumeInfoEntityFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    filters = listOf(string("VolumeInfoEntity(title=")),
)
