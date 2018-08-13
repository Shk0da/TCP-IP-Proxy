package com.github.shk0da.tcpipproxy.actor.client;

import akka.util.ByteString;
import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;
import lombok.Getter;
import lombok.Synchronized;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.shk0da.tcpipproxy.actor.client.TcpLogger.Level.*;
import static com.github.shk0da.tcpipproxy.actor.client.TcpLogger.log;

public class TcpClient {

    private static final long SEND_ATTEMPTS = 5;

    private final String host;
    private final int port;
    private final int maxConnectAttempts;
    private final int reconnectInterval;

    @Getter
    private final int instance;

    @Getter
    private volatile Socket socket;

    @Getter
    private volatile boolean enable = true;

    private AtomicLong sendAttempts = new AtomicLong(1);
    private AtomicLong connectAttempts = new AtomicLong(1);
    private AtomicLong lastReconnectTime = new AtomicLong(System.currentTimeMillis());
    private ConcurrentTaskScheduler taskScheduler = new ConcurrentTaskScheduler(
            new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors())
    );

    public TcpClient(InetSocketAddress socketAddress, int maxConnectAttempts, int reconnectInterval, int instance) {
        this.host = socketAddress.getHostName();
        this.port = socketAddress.getPort();
        this.maxConnectAttempts = maxConnectAttempts;
        this.reconnectInterval = reconnectInterval;
        this.instance = instance;
    }

    @Synchronized
    public ByteString send(ByteString message) {
        if (!checkSocket()) return ByteString.empty();

        byte[] response = new byte[1024];
        long start = System.currentTimeMillis();
        try {
            try (OutputStream os = socket.getOutputStream(); InputStream is = socket.getInputStream()) {
                byte[] data = message.toArray();
                os.write(data, 0, data.length);

                int bytesRead;
                int position = 0;
                int msgsize = 0;
                int iteration = 0;
                do {
                    bytesRead = is.read(response, position, response.length - position);
                    log(DEBUG, instance, "Bytes read: ", bytesRead);
                    if (bytesRead > 0) {
                        position += bytesRead;
                    }
                    if (position >= 2) {
                        msgsize = (int) response[0] * 256 + (response[1] < 0 ? (int) response[1] + 256 : response[1]);
                    }
                    iteration++;
                } while ((msgsize + 2 < position || position == 0) && iteration < 5);

                log(DEBUG, instance, "Request: {} :: {}", new String(data), HexBin.encode(data));
                log(DEBUG, instance, "Response: {} :: {}", new String(response), HexBin.encode(response));

                os.close();
                is.close();
            }
        } catch (IOException e) {
            if (sendAttempts.get() <= SEND_ATTEMPTS) {
                log(ERROR, instance, "ATTEMPT: {}. Error sending request to/processing response from: [{}]",
                        sendAttempts.getAndIncrement(), e.getMessage());
                send(message);
            }

            sendAttempts.set(0);
            return null;
        }
        long end = System.currentTimeMillis();

        return ByteString.fromArray(response);
    }

    @Synchronized
    public Boolean checkSocket() {
        if (isClosed()) {
            if (getSocket() != null) {
                infoReconnect();
            }
            connectAttempts.set(1);
            sendAttempts.set(1);
            openSocketConnection();
        }

        return isEnable();
    }

    private void infoReconnect() {
        long currentTime = System.currentTimeMillis();
        long workTime = currentTime - lastReconnectTime.get();
        lastReconnectTime.set(currentTime);
        log(INFO, instance, "Reconnect [{}:{}], work time: {} ms", host, port, workTime);
    }

    private void openSocketConnection() {
        if (connectAttempts.get() > maxConnectAttempts) return;
        try {
            socket = new Socket(host, port);
            socket.setKeepAlive(false);
            connectAttempts.set(1);
            enable = true;
        } catch (SocketException e) {
            enable = false;
            connectAttempts.incrementAndGet();
            log(ERROR, instance, "Connection to [{}:{}] lost: [{}]", host, port, e.getMessage());
            reconnect();
        } catch (IOException e) {
            enable = false;
            connectAttempts.incrementAndGet();
            log(ERROR, instance, "Error opening connection to: [{}:{}], [{}]", host, port, e.getMessage());
            reconnect();
        }
    }

    private void reconnect() {
        taskScheduler.execute(() -> {
            closeSocketConnection();
            try {
                TimeUnit.SECONDS.sleep(reconnectInterval);
            } catch (InterruptedException nothing) {
                log(ERROR, instance, nothing.getMessage());
            }
            openSocketConnection();
        });
    }

    private void closeSocketConnection() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.getInputStream().close();
                socket.getOutputStream().close();
                socket.close();
            }
            socket = null;
        } catch (IOException e) {
            log(ERROR, instance, "Error closing connection to: [{}]", e.getMessage());
        }
    }

    public Boolean isClosed() {
        return socket == null || socket.isClosed();
    }

    @Override
    @PreDestroy
    public void finalize() {
        connectAttempts.set(maxConnectAttempts);
        try {
            Thread.currentThread().interrupt();
            super.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
