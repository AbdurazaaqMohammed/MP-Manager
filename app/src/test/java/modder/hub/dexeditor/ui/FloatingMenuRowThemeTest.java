package modder.hub.dexeditor.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regression guard for the dark-theme white boxes on the "Edit floating menus"
 * screen. The row layout (floating_menu_items) used to paint its content with a
 * ripple drawable whose solid color was hardcoded to @android:color/white, and
 * the icon/title colors were hardcoded black, so in dark mode every row stayed
 * a white box with invisible icons. Colors must resolve through the Material3
 * DayNight theme attributes instead, which these file-level checks enforce.
 */
public class FloatingMenuRowThemeTest {

    private static final String ROW_DRAWABLE = "drawable/rounded_corner_ripple.xml";

    /** Locates app/src/main/res whether tests run from the module or repo root. */
    private static Path resDir() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path[] candidates = {
                cwd, cwd.resolve("app"),
                cwd.getParent() == null ? null : cwd.getParent().resolve("app")
        };
        for (Path candidate : candidates) {
            if (candidate != null && candidate.resolve("src/main/res/layout-v21/floating_menu_items.xml")
                    .toFile().isFile()) {
                return candidate.resolve("src/main/res");
            }
        }
        throw new AssertionError("floating_menu_items.xml not found relative to " + cwd);
    }

    private static String read(Path res, String relative) throws IOException {
        return new String(Files.readAllBytes(res.resolve(relative)), StandardCharsets.UTF_8);
    }

    private static String elementBlock(String xml, String element) {
        Matcher m = Pattern.compile("(?s)<" + element + "\\b.*?(/>|</" + element + ">)")
                .matcher(xml);
        assertTrue("No <" + element + "> found in " + xml, m.find());
        return m.group();
    }

    @Test
    public void rowBackgroundFollowsThemeSurfaceColor() throws IOException {
        String drawable = read(resDir(), ROW_DRAWABLE);
        assertTrue("rounded_corner_ripple must use ?attr/colorSurface so rows adapt to "
                        + "dark/black themes (was hardcoded white)",
                drawable.contains("?attr/colorSurface"));
        assertFalse("rounded_corner_ripple must not hardcode a white solid",
                drawable.contains("@android:color/white")
                        || Pattern.compile("solid[^>]*#FFFFFF", Pattern.CASE_INSENSITIVE)
                                .matcher(drawable).find());
    }

    @Test
    public void rowLayoutVariantsUseThemeAwareIconTint() throws IOException {
        for (String variant : new String[]{"layout-v21/floating_menu_items.xml",
                "layout/floating_menu_items.xml"}) {
            String xml = read(resDir(), variant);
            String imageView = elementBlock(xml, "ImageView");
            assertTrue(variant + " drag/icon view must tint through a theme attribute",
                    imageView.contains("app:tint=\"?attr/"));
            assertFalse(variant + " icon tint must not be a literal color",
                    Pattern.compile("app:tint=\"[^\"]*#[0-9a-fA-F]").matcher(imageView).find());
        }
    }

    @Test
    public void rowLayoutVariantsUseThemeAwareTextColor() throws IOException {
        for (String variant : new String[]{"layout-v21/floating_menu_items.xml",
                "layout/floating_menu_items.xml"}) {
            String xml = read(resDir(), variant);
            String title = elementBlock(xml, "TextView");
            assertTrue(variant + " row title must resolve its color from the theme",
                    title.contains("android:textColor=\"?android:attr/textColorPrimary\"")
                            || title.contains("android:textColor=\"?attr/"));
            assertFalse(variant + " title color must not be a literal color",
                    Pattern.compile("android:textColor=\"[^\"]*#[0-9a-fA-F]")
                            .matcher(title).find());
        }
    }
}
