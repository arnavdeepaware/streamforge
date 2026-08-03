package io.streamforge.ticksimulator;

/** Selects whether a simulated stream has a bounded or unbounded number of events. */
public sealed interface SimulationMode permits FiniteSimulation, ContinuousSimulation {}
