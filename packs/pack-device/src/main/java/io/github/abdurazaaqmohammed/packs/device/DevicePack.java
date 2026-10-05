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
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new DeviceHubTool(),
                new ClockTool(),
                new MeasureTool(),
                new FlashlightTool(),
                new MagnifierTool(),
                new VolumeTool(),
                new VibrationTool(),
                new LevelTool(),
                new CompassTool(),
                new GpsTool(),
                new RingtoneTool(),
                new WallpaperTool(),
                new DeviceHubTool());
    }
}
