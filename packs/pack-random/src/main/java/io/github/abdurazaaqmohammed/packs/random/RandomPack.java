package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Downloadable randomizer tools pack.
 */
public class RandomPack implements ToolPack {

    @Override
    public String packId() {
        return "random";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Collections.<ToolPlugin>singletonList(new RandomSuiteTool());
    }
}
