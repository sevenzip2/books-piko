import app.morphe.extension.playbooks.copy.AttributedCopy;
import java.util.Arrays;
import java.util.Collections;

public final class AttributedCopyTest {
    private static void equal(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected: " + expected + "\nActual: " + actual);
    }

    public static void main(String[] args) {
        String text = "www.sallmanart.org에서\n두 번째 문장 🌱";
        if (AttributedCopy.shouldAttribute(10, 100, 1) || AttributedCopy.shouldAttribute(0, 0, 2)) {
            throw new AssertionError("Permitted copies must remain plain text");
        }
        if (!AttributedCopy.shouldAttribute(0, 0, 1) || !AttributedCopy.shouldAttribute(0, 100, 1) ||
                !AttributedCopy.shouldAttribute(-1, 100, 1) || !AttributedCopy.shouldAttribute(1, 1, 0)) {
            throw new AssertionError("Blocked, exhausted and unknown quotas require attribution");
        }
        equal(text, AttributedCopy.copyText(text, null, false));
        AttributedCopy.remember("book-a", "지금 우리에게 예수는 누구인가?", Arrays.asList("정경일", "공저자"),
                "https://play.google.com/store/books/details?id=book-a");
        equal(text + "\n\n- <지금 우리에게 예수는 누구인가?>, 정경일 · 공저자 - Google Play 북\n"
                + "https://play.google.com/store/books/details?id=book-a", AttributedCopy.format(text, "book-a"));
        equal(AttributedCopy.format(text, "book-a"), AttributedCopy.copyText(text, "book-a", true));

        // Changing books must never reuse the previous book's attribution.
        equal("문장\n\n- Google Play 북\nhttps://play.google.com/books/reader?id=uploaded%3Aa+b",
                AttributedCopy.format("문장", "uploaded:a b"));
        AttributedCopy.remember("book-b", "다른 책", Collections.emptyList(), "");
        equal("문장\n\n- <다른 책> - Google Play 북\nhttps://play.google.com/books/reader?id=book-b",
                AttributedCopy.format("문장", "book-b"));
        AttributedCopy.remember("book-b", "수정된 제목", null, "https://example.org/book-b");
        equal("문장\n\n- <수정된 제목> - Google Play 북\nhttps://example.org/book-b",
                AttributedCopy.format("문장", "book-b"));
        for (int i = 0; i < 129; i++) AttributedCopy.remember("extra-" + i, "제목", null, null);
        equal("문장\n\n- Google Play 북\nhttps://play.google.com/books/reader?id=book-a",
                AttributedCopy.format("문장", "book-a"));
        try {
            AttributedCopy.format(text, "");
            throw new AssertionError("Missing IDs must not silently produce an unattributed copy");
        } catch (IllegalArgumentException expected) {
            // No clipboard write can happen with this result.
        }
        System.out.println("Attributed copy tests passed");
    }
}
