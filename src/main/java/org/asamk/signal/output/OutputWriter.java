package org.asamk.signal.output;

public sealed interface OutputWriter permits JsonWriter, PlainTextWriter {}
