package modder.hub.dexeditor.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;

/**
 * The dex search/replace dialogs used to compile the raw query inside a dialog
 * button listener, so an unbalanced bracket took the whole activity down. These
 * cover the guards that now stand in front of {@link java.util.regex.Pattern}.
 */
public class RegexGuardTest {

    @Test
    public void rejectsUnbalancedGroups() {
        assertNotNull(RegexGuard.validate("(", true));
        assertNotNull(RegexGuard.validate("a[", true));
        assertNotNull(RegexGuard.validate("*abc", true));
        assertNotNull(RegexGuard.validate("a{2,1}", true));
        assertNotNull(RegexGuard.validate("(?<", true));
    }

    @Test
    public void acceptsOrdinaryPatterns() {
        assertNull(RegexGuard.validate("Lcom/.*;", true));
        assertNull(RegexGuard.validate("\\d+\\.\\d+", true));
        assertNull(RegexGuard.validate("(?i)const-string", true));
        assertNull(RegexGuard.validate("a|b|c", true));
        assertNull(RegexGuard.validate("[a-z0-9_]+", false));
    }

    @Test
    public void rejectsEmptyPattern() {
        assertNotNull(RegexGuard.validate("", true));
        assertNotNull(RegexGuard.validate(null, true));
    }

    @Test
    public void describesSyntaxErrorWithPosition() throws Exception {
        try {
            RegexGuard.compile("a(", true);
            throw new AssertionError("expected PatternSyntaxException");
        } catch (PatternSyntaxException e) {
            String message = RegexGuard.describe(e);
            assertTrue(message, message.contains("position"));
        }
    }

    @Test
    public void anchorWholeKeepsAlternationInsideTheAnchors() {
        String anchored = RegexGuard.anchorWhole("a|b");
        Matcher whole = RegexGuard.compile("^" + anchored + "$", true).matcher("a");
        Matcher alsoWhole = RegexGuard.compile("^" + anchored + "$", true).matcher("b");
        Matcher rejectsMiddle = RegexGuard.compile("^" + anchored + "$", true).matcher("ab");
        assertTrue(whole.find());
        assertTrue(alsoWhole.find());
        assertFalse(rejectsMiddle.find());
    }

    @Test
    public void anchorWholeFixesTheNaiveConcatenation() {
        assertTrue(RegexGuard.compile("^" + RegexGuard.anchorWhole("a|b") + "$", true).matcher("b").find());
        assertFalse(RegexGuard.compile("^a|b$", true).matcher("xbx").find());
    }

    @Test
    public void flagsNestedQuantifiers() {
        assertTrue(RegexGuard.mayBacktrackCatastrophically("(a+)+"));
        assertTrue(RegexGuard.mayBacktrackCatastrophically("(a*)*"));
        assertTrue(RegexGuard.mayBacktrackCatastrophically("(x+x+)+y"));
        assertTrue(RegexGuard.mayBacktrackCatastrophically("(?:[a-zA-Z]+)*"));
        assertTrue(RegexGuard.mayBacktrackCatastrophically("(a{2,})+"));
        assertTrue(RegexGuard.mayBacktrackCatastrophically("((a|b)+)+"));
    }

    @Test
    public void leavesHarmlessPatternsAlone() {
        assertFalse(RegexGuard.mayBacktrackCatastrophically("Lcom/example/Foo;"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("\\d+\\.\\d+"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("[a-z0-9_]+"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("invoke-.*"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("(?:a|b)+"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("[+*]+"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically(""));
    }

    @Test
    public void doesNotTreatEscapedQuantifiersAsRepetition() {
        assertFalse(RegexGuard.mayBacktrackCatastrophically("(a\\+)+"));
        assertFalse(RegexGuard.mayBacktrackCatastrophically("\\(\\(\\)+"));
    }

    @Test
    public void boundedMatcherCapsTheSubject() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < (1 << 20) - "needle".length(); i++) sb.append('a');
        String withinCap = sb + "needle";
        assertTrue(RegexGuard.boundedMatcher(RegexGuard.compile("needle", true), withinCap).find());

        StringBuilder past = new StringBuilder();
        for (int i = 0; i < (1 << 20) + 16; i++) past.append('a');
        assertFalse(RegexGuard.boundedMatcher(RegexGuard.compile("needle", true), past + "needle").find());

        String shortSubject = "nothing to see";
        assertFalse(RegexGuard.boundedMatcher(RegexGuard.compile("needle", true), shortSubject).find());
    }

    @Test
    public void compileHonoursMatchCase() {
        assertEquals("ABC", RegexGuard.compile("abc", true).matcher("ABC").replaceAll("xyz"));
        assertEquals("xyz", RegexGuard.compile("abc", false).matcher("ABC").replaceAll("xyz"));
    }
}