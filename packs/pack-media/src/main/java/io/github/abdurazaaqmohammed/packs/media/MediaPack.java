package io.github.abdurazaaqmohammed.packs.media;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Downloadable media & sound tools pack.
 */
public class MediaPack implements ToolPack {

    @Override
    public String packId() {
        return "media";
    }

    @Override
    public int version() {
        return 2;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new ToneTool(),
                new MetronomeTool(),
                new RecorderTool(),
                new TtsTool());
    }
}
