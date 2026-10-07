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

    /** Start of the per-rule style processing: {@code a=a.style;if(!a)return f;} */
    private static final Pattern RULE_STYLE = Pattern.compile("(\\w+)=\\1\\.style;if\\(!\\1\\)return \\w+;");

    /** First check of the same function: {@code if(b.Z&&a.constructor.name==="CSSFontFaceRule"} */
    private static final Pattern RULE_OVERRIDE_FIELD = Pattern.compile(
            "if\\((\\w+)\\.(\\w+)&&(\\w+)\\.constructor\\.name===\"CSSFontFaceRule\"");

    /**
     * Helpers prepended to the engine. Publishers often make text bold with a separate bold font
     * family (e.g. "XxxBold") instead of font-weight. The override replaces that family with the
     * custom font, so the weight is lost. These helpers remember which publisher families are bold
     * and log the fonts a book uses (logcat tag BooksJS, enable with
     * {@code adb shell setprop log.tag.BooksJS DEBUG}).
     */
    static final String BOLD_HELPERS = "window.__bp=window.__bp||{seen:{},fam:{},"
            + "w:function(v){v=String(v||\"\").trim();return v===\"bold\"||v===\"bolder\"?700:"
            + "v===\"normal\"||v===\"lighter\"?400:parseInt(v,10)||0},"
            + "nb:function(s){return/(semi|demi|extra|ultra)?bold|black|heavy|(^|[\\s_-])(b|bd|eb|sb|xb)($|[\\s_.-])/i.test(s||\"\")},"
            + "log:function(m){if(this.seen[m])return;this.seen[m]=1;try{bridge.logD(\"BooksPiko \"+m)}catch(e){}},"
            + "clean:function(f){return String(f||\"\").replace(/[\"']/g,\"\").trim().toLowerCase()},"
            + "face:function(r){var s=r.style,f=this.clean(s.getPropertyValue(\"font-family\")),"
            + "w=s.getPropertyValue(\"font-weight\"),src=s.getPropertyValue(\"src\")||\"\",e=this.fam[f]||(this.fam[f]={n:0,b:0});"
            + "this.w(w)>=600||this.nb(src)?e.b=1:e.n=1;"
            + "this.log(\"face family=\"+f+\" weight=\"+(w||\"-\")+\" style=\"+(s.getPropertyValue(\"font-style\")||\"-\")+\" src=\"+src.slice(0,160))},"
            + "bold:function(s){var ff=s.fontFamily;if(!ff)return!1;this.log(\"rule family=\"+ff+\" weight=\"+(s.fontWeight||\"-\"));"
            + "var t=this;return ff.split(\",\").some(function(x){x=t.clean(x);var e=t.fam[x];return t.nb(x)||(e&&e.b&&!e.n)})}};\n";

    /** The injected override rule: {@code '* { font-family: "'+a+'" !important; }'} */
    private static final Pattern CHOSEN_FONT_RULE = Pattern.compile("'\\* \\{ font-family: \"'\\+(\\w+)\\+'\"");

    /** Matches Chromium's synthetic bold for typical text (measured with RIDIBatang). */
    static final String BOLD_STROKE_WIDTH = "0.025em";

    /** Bold by default in the user agent style sheet, or bold inline styles. Zero specificity. */
    static final String DEFAULT_BOLD_RULE = "* { font-synthesis: style; } "
            + ":where(b, strong, h1, h2, h3, h4, h5, h6, th, dt, [style*=\"font-weight: bold\" i], "
            + "[style*=\"font-weight:bold\" i], [style*=\"font-weight: 700\"], [style*=\"font-weight:700\"]) "
            + "{ -webkit-text-stroke-width: " + BOLD_STROKE_WIDTH + "; } ";

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

        // Some WebViews do not synthesize bold for web fonts. Draw bold text with a stroke instead,
        // only while the custom font is the override ("literata").
        if (config.strokeBold && !config.availableVariants.contains(FontVariant.BOLD)) {
            Matcher field = RULE_OVERRIDE_FIELD.matcher(rewritten);
            Matcher style = RULE_STYLE.matcher(rewritten);
            Matcher chosen = CHOSEN_FONT_RULE.matcher(rewritten);
            if (field.find() && style.find(field.end()) && chosen.find()) {
                String settings = field.group(1) + "." + field.group(2);
                String declaration = style.group(1);
                String hook = settings + "===\"literata\"&&(function(s){var n=__bp.w(s.fontWeight);"
                        + "n>=600||__bp.bold(s)?s.setProperty(\"-webkit-text-stroke-width\",\"" + BOLD_STROKE_WIDTH + "\"):"
                        + "n>0&&s.setProperty(\"-webkit-text-stroke-width\",\"0\")})(" + declaration + ");";
                String faceHook = "window.__bp&&" + field.group(3) + ".constructor.name===\"CSSFontFaceRule\"&&__bp.face("
                        + field.group(3) + ");";
                String family = chosen.group(1);
                String rule = "(" + family + "==\"literata\"?'" + DEFAULT_BOLD_RULE + "':'')+" + chosen.group();

                // Edit from the end so earlier offsets stay valid.
                int[][] edits = {
                        {field.start(), field.start()},
                        {style.end(), style.end()},
                        {chosen.start(), chosen.end()},
                };
                String[] texts = {faceHook, hook, rule};
                Integer[] order = {0, 1, 2};
                java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
                    @Override
                    public int compare(Integer a, Integer b) {
                        return edits[b][0] - edits[a][0];
                    }
                });
                StringBuilder edited = new StringBuilder(rewritten);
                for (int index : order) {
                    edited.replace(edits[index][0], edits[index][1], texts[index]);
                }
                rewritten = BOLD_HELPERS + edited;
                summary.append(" strokeBold=on");
            } else {
                summary.append(" strokeBold=NOT_FOUND");
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
