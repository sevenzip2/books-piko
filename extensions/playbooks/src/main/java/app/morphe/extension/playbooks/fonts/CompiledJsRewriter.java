package app.morphe.extension.playbooks.fonts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adjusts the CSS the reader engine (assets/compiled.js) injects into book pages.
 *
 * <ul>
 * <li>Removes the bold/italic @font-face rules of replaced families when no matching custom
 * face exists. The browser then synthesizes bold and italic from the regular custom font,
 * instead of drawing "bold" text with a regular face declared as bold.</li>
 * <li>Optionally turns the default {@code body { font-family: literata; }} rule into a forced
 * rule, so the custom font also wins over publisher fonts when no font is chosen in the reader.</li>
 * <li>Optionally keeps code and preformatted text out of forced font rules.</li>
 * </ul>
 *
 * Every step is a plain text match; if a future reader engine no longer contains a pattern the
 * step does nothing and the fonts are still replaced by the request interceptor.
 */
public final class CompiledJsRewriter {
    private static final Pattern FONT_FACE = Pattern.compile("@font-face\\s*\\{[^{}]*\\}");
    private static final Pattern FONT_URL = Pattern.compile("url\\(\\s*[\"']?/fonts/([^\"')\\s]+)");

    static final String DEFAULT_BODY_RULE = "body { font-family: literata; }";
    static final String CHOSEN_FONT_RULE_START = "'* { font-family: \"'";

    static final String MONOSPACE_SAFE_SELECTOR =
            "*:not(pre):not(code):not(kbd):not(samp):not(tt):not(pre *):not(code *)";

    private CompiledJsRewriter() {
    }

    public static String rewrite(String js, FontConfig config) {
        StringBuffer result = new StringBuffer(js.length());
        Matcher face = FONT_FACE.matcher(js);
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
                }
            }

            face.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        face.appendTail(result);

        String rewritten = result.toString();
        String selector = config.keepMonospace ? MONOSPACE_SAFE_SELECTOR : "*";

        if (config.keepMonospace) {
            rewritten = rewritten.replace(CHOSEN_FONT_RULE_START,
                    "'" + MONOSPACE_SAFE_SELECTOR + " { font-family: \"'");
        }

        if (config.forceOverPublisherFonts) {
            rewritten = rewritten.replace(DEFAULT_BODY_RULE,
                    selector + " { font-family: literata !important; }");
        }

        return rewritten;
    }
}
