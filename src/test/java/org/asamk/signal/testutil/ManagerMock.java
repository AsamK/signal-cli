package org.asamk.signal.testutil;

import org.asamk.signal.manager.Manager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class ManagerMock {

    private static final String DEFAULT_ACI = "00000000-0000-0000-0000-000000000000";

    private ManagerMock() {
    }

    public static final class State {

        public final List<Manager.CallEventListener> callEventListeners = new ArrayList<>();
        public final AtomicInteger addCallEventListenerCount = new AtomicInteger(0);
        public final AtomicInteger removeCallEventListenerCount = new AtomicInteger(0);

        public final List<Manager.ReceiveMessageHandler> receiveHandlers = new ArrayList<>();
        public final AtomicInteger addReceiveHandlerCount = new AtomicInteger(0);
        public final AtomicInteger removeReceiveHandlerCount = new AtomicInteger(0);
    }

    public static Manager create(final String selfNumber) {
        return create(selfNumber, new State());
    }

    public static Manager create(final String selfNumber, final State state) {
        return (Manager) Proxy.newProxyInstance(
                Manager.class.getClassLoader(),
                new Class<?>[]{Manager.class},
                (proxy, method, args) -> {
                    final var methodName = method.getName();
                    switch (methodName) {
                        case "getSelfNumber":
                            return selfNumber;
                        case "getSelfACI":
                            return DEFAULT_ACI;
                        case "addCallEventListener":
                            state.addCallEventListenerCount.incrementAndGet();
                            state.callEventListeners.add((Manager.CallEventListener) args[0]);
                            return null;
                        case "removeCallEventListener":
                            state.removeCallEventListenerCount.incrementAndGet();
                            state.callEventListeners.remove((Manager.CallEventListener) args[0]);
                            return null;
                        case "addReceiveHandler":
                            state.addReceiveHandlerCount.incrementAndGet();
                            state.receiveHandlers.add((Manager.ReceiveMessageHandler) args[0]);
                            return null;
                        case "removeReceiveHandler":
                            state.removeReceiveHandlerCount.incrementAndGet();
                            state.receiveHandlers.remove((Manager.ReceiveMessageHandler) args[0]);
                            return null;
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "ManagerMock{" + selfNumber + "}";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
        );
    }

    private static Object defaultValue(final Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Optional.class) {
            return Optional.empty();
        }
        if (returnType == List.class) {
            return List.of();
        }
        if (returnType == Set.class) {
            return Set.of();
        }
        if (returnType == Collection.class) {
            return List.of();
        }
        if (returnType == Map.class) {
            return Map.of();
        }

        return null;
    }
}
