package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.api.ToolPack;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * Downloadable math tools pack. Loaded by the host via DexClassLoader;
 * see the pack catalog for the download location.
 */
public class MathPack implements ToolPack {

    @Override
    public String packId() {
        return "math";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public List<ToolPlugin> tools() {
        return Arrays.<ToolPlugin>asList(
                new CalculatorTool(),
                new ConverterTool(),
                new BaseConvTool(),
                new DiscountTool(),
                new TipTool(),
                new PercentTool(),
                new GstTool(),
                new UnitPriceTool(),
                new EmiTool(),
                new CompoundTool(),
                new SavingsTool(),
                new PrimeTool(),
                new QuadraticTool(),
                new MatrixTool(),
                new TriangleTool(),
                new GeometryTool(),
                new FractionTool(),
                new GpaTool(),
                new CurrencyTool(),
                new CookingTool(),
                new FuelTool(),
                new PaceTool(),
                new OhmTool(),
                new ResistorTool(),
                new DateDiffTool(),
                new AgeCalcTool(),
                new DateAddTool(),
                new TimeCalcTool(),
                new EventCountTool(),
                new TallyTool(),
                new BmiTool(),
                new BmrTool(),
                new BodyFatTool(),
                new WaterTool(),
                new SleepTool());
    }
}
