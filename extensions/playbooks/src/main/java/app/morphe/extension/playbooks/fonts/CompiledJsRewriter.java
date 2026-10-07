package app.morphe.extension.playbooks.fonts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adjusts the reader engine (assets/compiled.js).
 *
 * <p>The engine has its own font override: when a font family is chosen in the reader, it injects
 * {@code * { font-family: "<family>" !important; }}, puts the family first in every publisher rule
 * and deletes every other @font-face rule. The custom font is served for the bundled "literata"
 * files, so pinning the override to "literata" shows the custom font through that same mechanism,
 * for layout and display alike.
 *
 * <ul>
 * <li>Force over publisher fonts: the override is always "literata", even when no font is chosen.</li>
 * <li>Replace every reader font: any font chosen in the reader becomes "literata".</li>
 * <li>Removes the bold/italic @font-face rules of replaced families when no matching custom face
 * exists, so the browser synthesizes them from the regular custom font.</li>
 * <li>Optionally keeps code and preformatted text out of the injected override rule.</li>
 * </ul>
 *
 * Every step is a text match. If a future engine no longer contains a pattern the step does
 * nothing and the reader's bundled font files are still replaced by the request interceptor.
 */
public final class CompiledJsRewriter {
    private static final Pattern FONT_FACE = Pattern.compile("@font-face\\s*\\{[^{}]*\\}");
    private static final Pattern FONT_URL = Pattern.compile("url\\(\\s*[\"']?/fonts/([^\"')\\s]+)");

    /**
     * Font settings constructor: {@code b&&(b=Ag(b,"\"'"),b=b=="serif"?"literata":b);this.Z=b;}
     * Group 1 is the family parameter, group 2 the field holding the override.
     */
    private static final Pattern OVERRIDE_ASSIGNMENT = Pattern.compile(
            "(\\w+)&&\\(\\1=\\w+\\(\\1,\"\\\\\"'\"\\),\\1=\\1==\"serif\"\\?\"literata\":\\1\\);this\\.(\\w+)=\\1;");

    /**
     * Builder of the @font-face rules the app passes in at runtime (e.g. "gpb-literata" bold and
     * italic faces, used with the publisher default font): {@code J(c,function(d){b.push(["@font-face {",}
     * Group 1 is the font descriptor, group 2 the rule list.
     */
    private static final Pattern DYNAMIC_FACE_BUILDER = Pattern.compile(
            "function\\((\\w+)\\)\\{(\\w+)\\.push\\(\\[\"@font-face \\{\",");

    static final String DEFAULT_BODY_RULE = "body { font-family: literata; }";
    static final String CHOSEN_FONT_RULE_START = "'* { font-family: \"'";

    static final String MONOSPACE_SAFE_SELECTOR =
            "*:not(pre):not(code):not(kbd):not(samp):not(tt):not(pre *):not(code *)";

    private CompiledJsRewriter() {
    }

    public static final class Result {
        public final String js;
        public final String summary;

        Result(String js, String summary) {
            this.js = js;
            this.summary = summary;
        }
    }

    public static Result rewrite(String js, FontConfig config) {
        StringBuilder summary = new StringBuilder();

        // Bold/italic faces without a custom file.
        StringBuffer result = new StringBuffer(js.length());
        Matcher face = FONT_FACE.matcher(js);
        int removedFaces = 0;
        while (face.find()) {
            String block = face.group();
            String replacement = block;

            Matcher url = FONT_URL.matcher(block);
            if (url.find()) {
                String file = url.group(1);
                FontVariant variant = FontVariant.ofFileName(file);
                if (config.replaces(file) && variant != FontVariant.REGULAR
                        && !config.availableVariants.contains(variant)) {
                    replacement = "";
                    removedFaces++;
                }
            }

            face.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        face.appendTail(result);
        String rewritten = result.toString();
        summary.append("removedFaces=").append(removedFaces);

        // Same rule for the faces built at runtime.
        Matcher builder = DYNAMIC_FACE_BUILDER.matcher(rewritten);
        if (builder.find()) {
            String descriptor = builder.group(1);
            String rules = builder.group(2);
            String replacement = "function(" + descriptor + "){" + skipFaceExpression(config, descriptor + ".url")
                    + "||" + rules + ".push([\"@font-face {\",";
            rewritten = rewritten.substring(0, builder.start()) + replacement + rewritten.substring(builder.end());
            summary.append(" dynamicFaces=filtered");
        } else {
            summary.append(" dynamicFaces=NOT_FOUND");
        }

        // Pin the engine's font override to the replaced default family.
        if (config.forceOverPublisherFonts || config.replaceAllFamilies) {
            Matcher override = OVERRIDE_ASSIGNMENT.matcher(rewritten);
            if (override.find()) {
                String family = override.group(1);
                String field = override.group(2);
                String replacement = config.forceOverPublisherFonts
                        ? "this." + field + "=\"literata\";"
                        : family + "&&(" + family + "=\"literata\");this." + field + "=" + family + ";";
                rewritten = rewritten.substring(0, override.start()) + replacement + rewritten.substring(override.end());
                summary.append(" override=").append(config.forceOverPublisherFonts ? "always" : "chosen");
            } else {
                summary.append(" override=NOT_FOUND");
            }
        }

        if (config.keepMonospace && rewritten.contains(CHOSEN_FONT_RULE_START)) {
            rewritten = rewritten.replace(CHOSEN_FONT_RULE_START, "'" + MONOSPACE_SAFE_SELECTOR + " { font-family: \"'");
            summary.append(" monospace=kept");
        }

        // Fallback for engines where the override assignment was not found.
        if (config.forceOverPublisherFonts && rewritten.contains(DEFAULT_BODY_RULE)) {
            String selector = config.keepMonospace ? MONOSPACE_SAFE_SELECTOR : "*";
            rewritten = rewritten.replace(DEFAULT_BODY_RULE, selector + " { font-family: literata !important; }");
            summary.append(" bodyRule=forced");
        }

        return new Result(rewritten, summary.toString());
    }

    /**
     * JavaScript expression that is true for a bold/italic face of a replaced family without a
     * custom file. Mirrors {@link FontVariant#ofFileName} and {@link FontConfig#replaces}.
     */
    static String skipFaceExpression(FontConfig config, String urlExpression) {
        return "(function(u){var m=/\\/fonts\\/([^\\/?#]+)/.exec(u||\"\");if(!m)return!1;"
                + "var f=m[1].toLowerCase(),b=f.indexOf(\"bold\")>=0,i=f.indexOf(\"italic\")>=0;"
                + "if(!(b||i))return!1;"
                + (config.replaceAllFamilies ? "" : "if(f.indexOf(\"" + FontConfig.DEFAULT_FAMILY_FILE_PREFIX + "\")!=0)return!1;")
                + "return b&&i?" + js(!config.availableVariants.contains(FontVariant.BOLD_ITALIC))
                + ":b?" + js(!config.availableVariants.contains(FontVariant.BOLD))
                + ":" + js(!config.availableVariants.contains(FontVariant.ITALIC))
                + "})(" + urlExpression + ")";
    }

    private static String js(boolean value) {
        return value ? "!0" : "!1";
    }
}
