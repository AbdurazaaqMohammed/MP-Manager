package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
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
        return 3;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new TextCounterTool(),
                new HashTool(),
                new Base64Tool(),
                new UrlCodecTool(),
                new BinaryTool(),
                new CaesarTool(),
                new CaseConvTool(),
                new MorseTool(),
                new JsonTool(),
                new LoremTool(),
                new RegexTool(),
                new ColorConvTool());
    }
}
