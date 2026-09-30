package io.github.abdurazaaqmohammed.domain.math;

/**
 * Unit conversion tables and math. Pure logic extracted from
 * ToolRunnerActivity; no Android dependencies.
 */
public final class UnitConverter {

    private UnitConverter() {
    }

    public static String[] categories() {
        return new String[]{"Length", "Weight", "Temperature", "Data", "Speed", "Time", "Area", "Volume"};
    }

    public static String[] unitsForCategory(String cat) {
        if (cat == null) return new String[0];
        switch (cat) {
            case "Length":
                return new String[]{"mm", "cm", "m", "km", "inch", "ft", "yd", "mile"};
            case "Weight":
                return new String[]{"mg", "g", "kg", "ton", "oz", "lb"};
            case "Temperature":
                return new String[]{"C", "F", "K"};
            case "Data":
                return new String[]{"B", "KB", "MB", "GB", "TB", "Kb", "Mb", "Gb"};
            case "Speed":
                return new String[]{"m/s", "km/h", "mph", "knot", "ft/s"};
            case "Time":
                return new String[]{"ms", "s", "min", "h", "day", "week"};
            case "Area":
                return new String[]{"mm2", "cm2", "m2", "ha", "km2", "ft2", "acre"};
            default:
                return new String[]{"mL", "L", "m3", "tsp", "tbsp", "cup", "floz", "gal"};
        }
    }

    public static double convert(String cat, double value, String from, String to) {
        if (cat == null) return value;
        if ("Temperature".equals(cat)) {
            return convertTemp(value, from, to);
        }
        return fromBase(toBase(value, from, cat), to, cat);
    }

    public static double toBase(double v, String unit, String cat) {
        if (cat == null || unit == null) return v;
        switch (cat) {
            case "Length":
                switch (unit) {
                    case "mm": return v / 1000.0;
                    case "cm": return v / 100.0;
                    case "m": return v;
                    case "km": return v * 1000.0;
                    case "inch": return v * 0.0254;
                    case "ft": return v * 0.3048;
                    case "yd": return v * 0.9144;
                    case "mile": return v * 1609.344;
                }
                break;
            case "Weight":
                switch (unit) {
                    case "mg": return v / 1000000.0;
                    case "g": return v / 1000.0;
                    case "kg": return v;
                    case "ton": return v * 1000.0;
                    case "oz": return v * 0.028349523125;
                    case "lb": return v * 0.45359237;
                }
                break;
            case "Data":
                switch (unit) {
                    case "B": return v;
                    case "KB": return v * 1024.0;
                    case "MB": return v * 1048576.0;
                    case "GB": return v * 1073741824.0;
                    case "TB": return v * 1099511627776.0;
                    case "Kb": return v * 128.0;
                    case "Mb": return v * 131072.0;
                    case "Gb": return v * 134217728.0;
                }
                break;
            case "Speed":
                switch (unit) {
                    case "m/s": return v;
                    case "km/h": return v / 3.6;
                    case "mph": return v * 0.44704;
                    case "knot": return v * 0.514444;
                    case "ft/s": return v * 0.3048;
                }
                break;
            case "Time":
                switch (unit) {
                    case "ms": return v / 1000.0;
                    case "s": return v;
                    case "min": return v * 60.0;
                    case "h": return v * 3600.0;
                    case "day": return v * 86400.0;
                    case "week": return v * 604800.0;
                }
                break;
            case "Area":
                switch (unit) {
                    case "mm2": return v / 1000000.0;
                    case "cm2": return v / 10000.0;
                    case "m2": return v;
                    case "ha": return v * 10000.0;
                    case "km2": return v * 1000000.0;
                    case "ft2": return v * 0.09290304;
                    case "acre": return v * 4046.8564224;
                }
                break;
            case "Volume":
                switch (unit) {
                    case "mL": return v / 1000.0;
                    case "L": return v;
                    case "m3": return v * 1000.0;
                    case "tsp": return v * 0.00492892159375;
                    case "tbsp": return v * 0.01478676478125;
                    case "cup": return v * 0.2365882365;
                    case "floz": return v * 0.0295735295625;
                    case "gal": return v * 3.785411784;
                }
                break;
            default:
                break;
        }
        return v;
    }

    public static double fromBase(double base, String unit, String cat) {
        if (cat == null || unit == null) return base;
        switch (cat) {
            case "Length":
                switch (unit) {
                    case "mm": return base * 1000.0;
                    case "cm": return base * 100.0;
                    case "m": return base;
                    case "km": return base / 1000.0;
                    case "inch": return base / 0.0254;
                    case "ft": return base / 0.3048;
                    case "yd": return base / 0.9144;
                    case "mile": return base / 1609.344;
                }
                break;
            case "Weight":
                switch (unit) {
                    case "mg": return base * 1000000.0;
                    case "g": return base * 1000.0;
                    case "kg": return base;
                    case "ton": return base / 1000.0;
                    case "oz": return base / 0.028349523125;
                    case "lb": return base / 0.45359237;
                }
                break;
            case "Data":
                switch (unit) {
                    case "B": return base;
                    case "KB": return base / 1024.0;
                    case "MB": return base / 1048576.0;
                    case "GB": return base / 1073741824.0;
                    case "TB": return base / 1099511627776.0;
                    case "Kb": return base / 128.0;
                    case "Mb": return base / 131072.0;
                    case "Gb": return base / 134217728.0;
                }
                break;
            case "Speed":
                switch (unit) {
                    case "m/s": return base;
                    case "km/h": return base * 3.6;
                    case "mph": return base / 0.44704;
                    case "knot": return base / 0.514444;
                    case "ft/s": return base / 0.3048;
                }
                break;
            case "Time":
                switch (unit) {
                    case "ms": return base * 1000.0;
                    case "s": return base;
                    case "min": return base / 60.0;
                    case "h": return base / 3600.0;
                    case "day": return base / 86400.0;
                    case "week": return base / 604800.0;
                }
                break;
            case "Area":
                switch (unit) {
                    case "mm2": return base * 1000000.0;
                    case "cm2": return base * 10000.0;
                    case "m2": return base;
                    case "ha": return base / 10000.0;
                    case "km2": return base / 1000000.0;
                    case "ft2": return base / 0.09290304;
                    case "acre": return base / 4046.8564224;
                }
                break;
            case "Volume":
                switch (unit) {
                    case "mL": return base * 1000.0;
                    case "L": return base;
                    case "m3": return base / 1000.0;
                    case "tsp": return base / 0.00492892159375;
                    case "tbsp": return base / 0.01478676478125;
                    case "cup": return base / 0.2365882365;
                    case "floz": return base / 0.0295735295625;
                    case "gal": return base / 3.785411784;
                }
                break;
            default:
                break;
        }
        return base;
    }

    public static double convertTemp(double v, String f, String t) {
        double c;
        if ("C".equals(f)) {
            c = v;
        } else if ("F".equals(f)) {
            c = (v - 32.0) * 5.0 / 9.0;
        } else {
            c = v - 273.15;
        }
        if ("C".equals(t)) {
            return c;
        } else if ("F".equals(t)) {
            return c * 9.0 / 5.0 + 32.0;
        } else {
            return c + 273.15;
        }
    }
}
