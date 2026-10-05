package io.github.abdurazaaqmohammed.domain.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextLogicTest {

    @Test
    public void textStats() {
        assertEquals(11, TextStats.chars("hello world"));
        assertEquals(2, TextStats.words("hello world"));
        assertEquals(3, TextStats.lines("a\nb\nc"));
        assertEquals(2, TextStats.sentences("Hi. Bye!"));
    }

    @Test
    public void codecs() throws Exception {
        assertEquals("aGVsbG8=", java.util.Base64.getEncoder().encodeToString("hello".getBytes()));
        assertEquals("hello world", TextCodecs.binaryDecode(TextCodecs.binaryEncode("hello world")));
        assertEquals("Khoor", TextCodecs.caesarShift("Hello", 3));
        assertEquals("abc", TextCodecs.caesarShift("xyz", 3));
        assertEquals("Lorem Ipsum", TextCodecs.toTitleCase("loreM ipsum"));
        assertEquals("abc", TextCodecs.reversed("cba"));
    }

    @Test
    public void hashing() throws Exception {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Hashing.md5(""));
        assertEquals("5d41402abc4b2a76b9719d911017c592", Hashing.md5("hello"));
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Hashing.sha1(""));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Hashing.sha256(""));
    }

    @Test
    public void passwordSecurity() {
        String p = Passwords.generate(32, true, true, true, true);
        assertEquals(32, p.length());
        double bits = Passwords.entropyBits("aaaaaaaaaaaaaaaa");
        assertEquals(16 * (Math.log(26) / Math.log(2)), bits, 1e-9);
        assertEquals("Very weak", Passwords.strengthLabel(Passwords.entropyBits("abc")));
    }

    @Test
    public void loremGenerates() {
        String out = Lorem.generate(2, new java.util.Random(0));
        assertTrue(out.length() > 20);
    }
}
