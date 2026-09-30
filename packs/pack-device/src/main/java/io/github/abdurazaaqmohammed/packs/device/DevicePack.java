package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Downloadable device & hardware tools pack.
 */
public class DevicePack implements ToolPack {

    @Override
    public String packId() {
        return "device";
    }

    @Override
    public int version() {
        return 3;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new StopwatchTool(),
                new TimerTool(),
                new FlashlightTool(),
                new MagnifierTool(),
                new VolumeTool(),
                new VibrationTool(),
                new CompassTool(),
                new LevelTool(),
                new RulerTool(),
                new ProtractorTool(),
                new GpsTool(),
                new RingtoneTool(),
                new WallpaperTool(),
                new DeviceHubTool());
    }
}
