package io.github.abdurazaaqmohammed.feature.arm64.decoder;

public class UndefinedInstructionException extends IllegalArgumentException {
    public UndefinedInstructionException(String s) {
        super(s);
    }
}
