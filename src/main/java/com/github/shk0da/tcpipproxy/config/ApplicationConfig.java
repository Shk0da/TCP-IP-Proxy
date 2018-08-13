package com.github.shk0da.tcpipproxy.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

@Slf4j
@Configuration
public class ApplicationConfig {

    public static final String SHUTDOWN_MESSAGE = "Shutdown ProxyApplication";

    @Autowired
    private ApplicationContext applicationContext;

    @EventListener
    public void onStartup(ApplicationReadyEvent event) {
        shutdownHook();
    }

    @EventListener
    public void onShutdown(ContextStoppedEvent event) {
        log.info(SHUTDOWN_MESSAGE);
    }

    /**
     * Хук на корректное завершене приложения (SIGTERM(15))
     */
    @Async
    protected void shutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            onShutdown(new ContextStoppedEvent(applicationContext));
            SpringApplication.exit(applicationContext);
        }, "shutdown-hook"));
    }
}
