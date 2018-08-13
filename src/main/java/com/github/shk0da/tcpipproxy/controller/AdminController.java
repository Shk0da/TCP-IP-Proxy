package com.github.shk0da.tcpipproxy.controller;

import com.github.shk0da.tcpipproxy.actor.client.TcpBalancer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/")
public class AdminController {

    private static final AtomicLong reloadAntiSpam = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DefaultListableBeanFactory beanFactory;

    /**
     * Перезагрузка конфигурации:
     * Инициализация новых подключений «на лету»
     * Удаление более неиспользуемых подключений
     *
     * @return статус
     */
    @GetMapping("reload")
    public ResponseEntity<String> reload() {
        long currentTime = System.currentTimeMillis();
        long nextReloadTime = currentTime > reloadAntiSpam.get() ? currentTime : reloadAntiSpam.get();
        reloadAntiSpam.set(nextReloadTime + TimeUnit.SECONDS.toMillis(10));
        try {
            // отложенное выполнение
            context.getBean(TaskScheduler.class).schedule(() -> {
                beanFactory.destroyBean(beanFactory.getBean(TcpBalancer.class));
                context.getAutowireCapableBeanFactory().autowireBean(new TcpBalancer());
            }, new Date(nextReloadTime));
        } catch (Exception ex) {
            return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>("done (next reload date: " + new Date(nextReloadTime) + ")", HttpStatus.OK);
    }
}
