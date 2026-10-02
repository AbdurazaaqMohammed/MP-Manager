package io.github.abdurazaaqmohammed.packs.notes;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Collections;
import java.util.List;

public class NotesPack implements ToolPack {

    @Override
    public String packId() {
        return "notes";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Collections.<ToolPlugin>singletonList(new NotesTool());
    }
}
