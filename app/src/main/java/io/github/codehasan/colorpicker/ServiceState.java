/*
 * Copyright (c) 2026 Ratul Hasan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */
package io.github.codehasan.colorpicker;

import android.content.Context;
import android.content.Intent;

import io.github.codehasan.colorpicker.services.ColorPickerService;
import kotlin.jvm.Synchronized;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ServiceState {

    public interface Observer {
        void onStateChanged(boolean isRunning);
    }

    private static final ServiceState INSTANCE = new ServiceState();

    private final Set<Observer> observers = new HashSet<>();
    private boolean running = false;

    private ServiceState() {
    }

    public static ServiceState getInstance() {
        return INSTANCE;
    }

    public synchronized boolean isRunning() {
        return running;
    }

    @Synchronized
    public void setColorPickerRunning(boolean isRunning) {
        if (running != isRunning) {
            running = isRunning;
            notifyObservers();
        }
    }

    @Synchronized
    public void stopColorPickerService(Context context) {
        // Reset state immediately to prevent race conditions
        running = false;
        context.stopService(new Intent(context, ColorPickerService.class));
        notifyObservers();
    }

    @Synchronized
    public void addObserver(Observer observer) {
        observers.add(observer);
        observer.onStateChanged(running);
    }

    @Synchronized
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        List<Observer> snapshot = new ArrayList<>(observers);
        for (Observer observer : snapshot) {
            observer.onStateChanged(running);
        }
    }
}
