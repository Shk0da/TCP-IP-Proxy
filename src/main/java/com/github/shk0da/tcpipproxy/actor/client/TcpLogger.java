package com.github.shk0da.tcpipproxy.actor.client;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
public final class TcpLogger {

    private static final String DISCRIMINATOR_KEY = "DESCRIPTION";

    public enum Level {INFO, DEBUG, ERROR, WARN, TRACE}

    private TcpLogger() {
    }

    public static void log(Level level, Integer instance, String message, Object... args) {
        MDC.put(DISCRIMINATOR_KEY, "Connection-" + String.valueOf(instance));
        switch (level) {
            case INFO:
                log.info(message, args);
                break;
            case DEBUG:
                log.debug(message, args);
                break;
            case ERROR:
                log.error(message, args);
                break;
            case WARN:
                log.warn(message, args);
                break;
            case TRACE:
                log.trace(message, args);
                break;
            default:
                break;
        }
        MDC.remove(DISCRIMINATOR_KEY);
    }
}
