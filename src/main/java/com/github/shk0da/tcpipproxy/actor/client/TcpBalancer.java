package com.github.shk0da.tcpipproxy.actor.client;

import com.github.shk0da.tcpipproxy.config.PropertiesConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component("TcpBalancer")
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
public class TcpBalancer {

    private static final Integer FIRST_INSTANCE = 0;
    private static final Set<TcpClient> clientInstances = Sets.newConcurrentHashSet();
    private static final List<ScheduledFuture> scheduledFutures = Lists.newArrayList();
    private static final ConcurrentTaskScheduler scheduler = new ConcurrentTaskScheduler(
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    );

    private final Object updateMonitor = new Object();
    private final AtomicInteger currentInstance = new AtomicInteger(FIRST_INSTANCE);
    private final AtomicLong lastTrafficTime = new AtomicLong(System.currentTimeMillis());

    @Getter
    private volatile boolean isUsage = false;

    public TcpBalancer() {
        String proxyCluster = PropertiesConfig.getProperty("proxy.cluster");
        String reconnectInterval = PropertiesConfig.getProperty("proxy.reconnect.interval");
        String maxAttempts = PropertiesConfig.getProperty("proxy.reconnect.attempts");
        String trafficAbsentTime = PropertiesConfig.getProperty("proxy.reconnect.trafficabsenttime");

        makingTcpClients(proxyCluster, reconnectInterval, maxAttempts);

        // проверям подключения у активных устройств
        ScheduledFuture<?> checkActive = scheduler.scheduleWithFixedDelay(
                () -> {
                    synchronized (updateMonitor) {
                        clientInstances.stream()
                                .filter(TcpClient::isEnable)
                                .forEach(TcpClient::checkSocket);
                    }
                }, Long.valueOf(reconnectInterval) * 1000 // sec to ms
        );
        scheduledFutures.add(checkActive);

        // тестирование NC в случае отсутствия трафика
        long trafficAbsentTimeInMs = Long.valueOf(trafficAbsentTime) * 1000; // sec to ms
        ScheduledFuture<?> checkNC = scheduler.scheduleWithFixedDelay(
                () -> {
                    synchronized (updateMonitor) {
                        if (lastTrafficTime.get() <= System.currentTimeMillis() - trafficAbsentTimeInMs) {
                            clientInstances.stream()
                                    .filter(TcpClient::isEnable);
                        }
                    }
                }, trafficAbsentTimeInMs
        );
        scheduledFutures.add(checkNC);
    }

    private void makingTcpClients(String proxyCluster, String reconnectInterval, String maxAttempts) {
        AtomicInteger instanceNum = new AtomicInteger(1);
        Set<TcpClient> tcpInstances = Sets.newHashSet();
        Lists.newArrayList(proxyCluster.split(";")).forEach(connection -> {
            try {
                String[] hostAndPort = connection.split(":");
                InetSocketAddress socketAddress = new InetSocketAddress(hostAndPort[0], Integer.valueOf(hostAndPort[1]));
                tcpInstances.add(new TcpClient(
                        socketAddress,
                        "-1".equals(maxAttempts) ? Integer.MAX_VALUE : Integer.valueOf(maxAttempts),
                        Integer.valueOf(reconnectInterval),
                        instanceNum.getAndIncrement()));
            } catch (Exception ex) {
                log.error("Can't create TcpClient [{}]: {}", connection, ex.getMessage());
            }
        });

        synchronized (clientInstances) {
            int waitCounter = 100;
            while (isUsage() && waitCounter-- > 0) {
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException ex) {
                    break;
                }
            }
            clientInstances.forEach(TcpClient::finalize);
            clientInstances.clear();
            clientInstances.addAll(tcpInstances);
        }
    }

    @Synchronized
    public TcpClient getClientInstance() {
        if (clientInstances.isEmpty()) return null;
        isUsage = true;
        lastTrafficTime.set(System.currentTimeMillis());

        int attempt = 0;
        TcpClient tcpClient = null;
        while (attempt++ <= clientInstances.size()) {
            if (currentInstance.get() >= clientInstances.size()) {
                currentInstance.set(FIRST_INSTANCE);
            }

            TcpClient nextTcpClient = (TcpClient) clientInstances.toArray()[currentInstance.getAndIncrement()];
            if (nextTcpClient.getSocket() != null && nextTcpClient.isEnable()) {
                tcpClient = nextTcpClient;
                break;
            }
        }
        isUsage = false;

        return tcpClient;
    }

    @PreDestroy
    private void finalizeScheduledFutures() {
        scheduledFutures.forEach(scheduledFuture -> scheduledFuture.cancel(true));
        scheduledFutures.clear();
    }
}