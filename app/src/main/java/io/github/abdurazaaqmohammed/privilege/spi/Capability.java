/*
 * Privilege layer — service provider interface.
 */
package io.github.abdurazaaqmohammed.privilege.spi;

/**
 * Marker for what a backend can do. Backends implement only the
 * capabilities they support — no UnsupportedOperationException stubs, no
 * paired isXxxReady() methods.
 */
public interface Capability {
}
