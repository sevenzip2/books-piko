package app.bookspiko.patches.books.reader.font

import app.bookspiko.patches.books.shared.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val SETTINGS_HOOK = "$EXTENSION_PACKAGE/settings/SettingsHook;"

private fun requireNibble(register: Int, what: String) {
    if (register > 15) throw PatchException("$what uses register v$register, which needs a range invoke")
}

/**
 * Adds a "Custom font" row to Settings > Ebook reading.
 *
 * The settings screen is built from a static tree of nodes and a Dagger map from item id to item.
 * The extension inserts a node with an unused id into the reading category and maps that id to a
 * second instance of the "About Google Play Books" row, which is then given its own title, subtitle
 * and click action here.
 */
context(context: BytecodePatchContext)
internal fun addFontSettingsEntry() {
    // region Register the row.

    SettingsItemsProviderFingerprint.match().let { match ->
        val method = match.method
        val root = match.instructionMatches[0].getInstruction<OneRegisterInstruction>().registerA
        val mapResult = match.instructionMatches[2]
        val map = mapResult.getInstruction<OneRegisterInstruction>().registerA
        requireNibble(root, "Settings tree")
        requireNibble(map, "Settings map")

        method.addInstructions(
            mapResult.index + 1,
            """
                invoke-static { v$root, v$map }, $SETTINGS_HOOK->registerFontItem(Ljava/lang/Object;Ljava/util/Map;)Ljava/util/Map;
                move-result-object v$map
            """,
        )
    }

    // endregion

    // region Title, subtitle and click of the reused row.

    AboutSettingsItemFingerprint.match().let { match ->
        val method = match.method
        val itemRegister = method.implementation!!.registerCount - method.parameters.size - 1
        requireNibble(itemRegister, "Settings row")

        // Subtitle: the String captured by the subtitle lambda, computed before the title.
        val subtitleConstructor = method.instructions.withIndex().firstOrNull { (_, instruction) ->
            if (instruction.opcode != Opcode.INVOKE_DIRECT) return@firstOrNull false
            val reference = (instruction as ReferenceInstruction).reference as MethodReference
            reference.name == "<init>" && reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/String;")
        } ?: throw PatchException("Subtitle lambda not found in ${method.definingClass}")
        val subtitle = (subtitleConstructor.value as FiveRegisterInstruction).registerD
        requireNibble(subtitle, "Settings row subtitle")

        // Title: result of the string resource lookup.
        val titleResult = match.instructionMatches[2]
        val title = titleResult.getInstruction<OneRegisterInstruction>().registerA
        requireNibble(title, "Settings row title")

        // Click lambda: the synthetic class constructed with the row itself.
        val clickLambdaType = method.instructions.firstNotNullOfOrNull { instruction ->
            if (instruction.opcode != Opcode.INVOKE_DIRECT) return@firstNotNullOfOrNull null
            val reference = (instruction as ReferenceInstruction).reference as MethodReference
            reference.definingClass.takeIf {
                reference.name == "<init>" &&
                    reference.parameterTypes.map { it.toString() } == listOf(method.definingClass)
            }
        } ?: throw PatchException("Click lambda not found in ${method.definingClass}")

        method.addInstructions(
            titleResult.index + 1,
            """
                invoke-static { v$itemRegister, v$title }, $SETTINGS_HOOK->title(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$title
            """,
        )
        // The subtitle register already holds the version text when the title resource is loaded.
        method.addInstructions(
            match.instructionMatches[0].index,
            """
                invoke-static { v$itemRegister, v$subtitle }, $SETTINGS_HOOK->subtitle(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$subtitle
            """,
        )

        val clickClass = context.mutableClassDefBy(clickLambdaType)
        val rowField = clickClass.fields.firstOrNull { it.type == method.definingClass }
            ?: throw PatchException("Row field not found in $clickLambdaType")
        val invoke = clickClass.methods.firstOrNull { candidate ->
            candidate.name != "<init>" && candidate.returnType == "Ljava/lang/Object;" &&
                candidate.implementation?.instructions?.any { it.opcode == Opcode.RETURN_OBJECT } == true
        } ?: throw PatchException("Click handler not found in $clickLambdaType")

        if (invoke.implementation!!.registerCount - invoke.parameters.size - 1 < 1) {
            throw PatchException("No free register in $clickLambdaType")
        }
        // The handler returns Kotlin's Unit singleton; reuse its reference.
        val unitField = invoke.instructions.last { it.opcode == Opcode.SGET_OBJECT }
            .let { ((it as ReferenceInstruction).reference as FieldReference) }

        invoke.addInstructionsWithLabels(
            0,
            """
                iget-object v0, p0, $clickLambdaType->${rowField.name}:${rowField.type}
                invoke-static { v0 }, $SETTINGS_HOOK->onClick(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :original
                sget-object v0, ${unitField.definingClass}->${unitField.name}:${unitField.type}
                return-object v0
            """,
            ExternalLabel("original", invoke.getInstruction(0)),
        )
    }

    // endregion
}
