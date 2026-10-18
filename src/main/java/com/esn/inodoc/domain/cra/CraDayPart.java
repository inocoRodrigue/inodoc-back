package com.esn.inodoc.domain.cra;

public enum CraDayPart {
    FULL_DAY, MORNING, AFTERNOON;

    public double units() { return this == FULL_DAY ? 1.0 : 0.5; }
}