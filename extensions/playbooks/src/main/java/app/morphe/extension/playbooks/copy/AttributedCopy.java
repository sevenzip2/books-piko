package app.morphe.extension.playbooks.copy;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Book metadata only; selected text is never retained. */
public final class AttributedCopy {
    private static final Map<String, String> SOURCES = new LinkedHashMap<String, String>(32, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 128;
        }
    };

    private AttributedCopy() {}

    public static boolean shouldAttribute(long remaining, long allowed, int limitType) {
        return limitType != 2 && !(limitType == 1 && remaining > 0 && allowed > 0);
    }

    public static String copyText(String text, String id, boolean attributed) {
        return attributed ? format(text, id) : text;
    }

    public static synchronized void remember(String id, String title, List<?> authors, String detailUrl) {
        if (id == null || id.isEmpty()) return;
        StringBuilder source = new StringBuilder("- <").append(title == null || title.isEmpty() ? id : title)
                .append('>');
        if (authors != null && !authors.isEmpty()) {
            String separator = ", ";
            for (Object author : authors) {
                if (author == null || author.toString().isEmpty()) continue;
                source.append(separator).append(author);
                separator = " · ";
            }
        }
        source.append(" - Google Play 북\n");
        source.append(detailUrl == null || detailUrl.trim().isEmpty() ? readerUrl(id) : detailUrl);
        SOURCES.put(id, source.toString());
    }

    public static synchronized String format(String text, String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Missing book ID for attribution");
        String source = SOURCES.get(id);
        if (source == null) source = "- Google Play 북\n" + readerUrl(id);
        return text + "\n\n" + source;
    }

    private static String readerUrl(String id) {
        try {
            return "https://play.google.com/books/reader?id=" + URLEncoder.encode(id, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
