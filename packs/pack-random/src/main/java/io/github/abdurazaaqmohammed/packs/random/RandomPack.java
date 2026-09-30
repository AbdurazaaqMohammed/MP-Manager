package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
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
        return 2;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new RandomTool(),
                new PasswordTool(),
                new StrengthTool(),
                new UuidTool());
    }
}
