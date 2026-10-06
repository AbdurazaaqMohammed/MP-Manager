package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Downloadable text & security tools pack.
 */
public class TextPack implements ToolPack {

    @Override
    public String packId() {
        return "text";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Collections.<ToolPlugin>singletonList(new TextSuiteTool());
    }
}
