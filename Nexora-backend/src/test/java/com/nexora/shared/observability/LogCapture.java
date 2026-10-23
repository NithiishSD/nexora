package com.nexora.shared.observability;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// Collects the events of one logger in memory (whatever the console level), with their MDC and key-value pairs.
final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previous;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    LogCapture(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        previous = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previous);
    }
}
