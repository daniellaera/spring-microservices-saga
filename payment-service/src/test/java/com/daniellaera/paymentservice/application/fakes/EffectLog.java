package com.daniellaera.paymentservice.application.fakes;

import java.util.ArrayList;
import java.util.List;

/** Shared by the fakes to record the order in which side effects happen. */
public class EffectLog {

    private final List<String> effects = new ArrayList<>();

    void add(String effect) {
        effects.add(effect);
    }

    public List<String> effects() {
        return List.copyOf(effects);
    }
}
