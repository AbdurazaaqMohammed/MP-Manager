package io.github.abdurazaaqmohammed.feature.apk;

/** A reusable set of AAPT2 filter values. Values are stored as comma-separated lists. */
final class Aapt2Profile {
    private String name;
    private String densities;
    private String configurations;
    private String abis;
    private String minSdk;
    private String targetSdk;
    private boolean deviceDefault;

    Aapt2Profile() {
    }

    Aapt2Profile(String name, String densities, String configurations, String abis,
                 String minSdk, String targetSdk, boolean deviceDefault) {
        this.name = name;
        this.densities = nullToEmpty(densities);
        this.configurations = nullToEmpty(configurations);
        this.abis = nullToEmpty(abis);
        this.minSdk = nullToEmpty(minSdk);
        this.targetSdk = nullToEmpty(targetSdk);
        this.deviceDefault = deviceDefault;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    String getName() { return name; }
    void setName(String name) { this.name = name; }
    String getDensities() { return densities; }
    void setDensities(String densities) { this.densities = nullToEmpty(densities); }
    String getConfigurations() { return configurations; }
    void setConfigurations(String configurations) { this.configurations = nullToEmpty(configurations); }
    String getAbis() { return abis; }
    void setAbis(String abis) { this.abis = nullToEmpty(abis); }
    String getMinSdk() { return minSdk; }
    void setMinSdk(String minSdk) { this.minSdk = nullToEmpty(minSdk); }
    String getTargetSdk() { return targetSdk; }
    void setTargetSdk(String targetSdk) { this.targetSdk = nullToEmpty(targetSdk); }
    boolean isDeviceDefault() { return deviceDefault; }
    void setDeviceDefault(boolean deviceDefault) { this.deviceDefault = deviceDefault; }

    Aapt2Profile copyValues() {
        return new Aapt2Profile(name, densities, configurations, abis, minSdk, targetSdk, deviceDefault);
    }

    /** True when every filter matches; names and the device-default flag are ignored. */
    boolean sameValues(Aapt2Profile other) {
        return other != null
                && densities.equals(other.densities)
                && configurations.equals(other.configurations)
                && abis.equals(other.abis)
                && minSdk.equals(other.minSdk)
                && targetSdk.equals(other.targetSdk);
    }
}
