package org.asamk.signal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import sun.misc.Signal;

public class Shutdown {

    private static final Logger logger = LoggerFactory.getLogger(Shutdown.class);
    private static final CompletableFuture<Void> shutdown = new CompletableFuture<>();
    private static final CompletableFuture<Void> shutdownComplete = new CompletableFuture<>();
    private static boolean initialized = false;

    public static void installHandler() {
        if (initialized) {
            return;
        }
        initialized = true;
        Signal.handle(new Signal("INT"), Shutdown::handleSignal);
        Signal.handle(new Signal("TERM"), Shutdown::handleSignal);
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            logger.debug("JVM is shutting down");
            if (!shutdown.isDone()) {
                triggerShutdown();
            }

            try {
                logger.debug("Waiting for app to shut down");
                shutdownComplete.get();
                logger.debug("Exiting");
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    public static void triggerShutdown() {
        logger.debug("Triggering shutdown.");
        shutdown.complete(null);
    }

    public static void waitForShutdown() throws InterruptedException {
        try {
            shutdown.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static void registerShutdownListener(Runnable action) {
        shutdown.thenRun(action);
    }

    static void shutdownComplete() {
        shutdownComplete.complete(null);
    }

    private static void handleSignal(Signal signal) {
        logger.info("Received {} signal, shutting down ...", signal);
        triggerShutdown();
    }
}
