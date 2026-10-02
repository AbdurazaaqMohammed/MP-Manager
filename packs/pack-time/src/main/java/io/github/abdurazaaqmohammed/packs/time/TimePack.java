package io.github.abdurazaaqmohammed.packs.time;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Downloadable time & productivity tools pack.
 */
public class TimePack implements ToolPack {

    @Override
    public String packId() {
        return "time";
    }

    @Override
    public int version() {
        return 3;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new NotesTool(),
                new TallyTool(),
                new DateDiffTool(),
                new AgeCalcTool(),
                new DateAddTool(),
                new TimeCalcTool(),
                new EventCountTool(),
                new BmiTool(),
                new BmrTool(),
                new BodyFatTool(),
                new WaterTool(),
                new SleepTool());
    }
}
