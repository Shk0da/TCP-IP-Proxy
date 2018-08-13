package com.github.shk0da.tcpipproxy.config;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.routing.RoundRobinPool;
import com.github.shk0da.tcpipproxy.actor.ServerActor;
import com.github.shk0da.tcpipproxy.actor.SpringDIActor;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.github.shk0da.tcpipproxy.actor.ClientActor;

import java.net.InetSocketAddress;

@Slf4j
@Configuration
public class ActorConfig {

    public static final String CLIENT_ACTOR_PATH_HEAD = "akka://tcp-proxy-client/user/ClientActor";
    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    @Autowired
    private Environment env;

    @Bean(name = "serverActorSystem")
    public ActorSystem serverActorSystem() {
        ActorSystem serverActorSystem = ActorSystem.create("tcp-proxy-server");
        Lists.newArrayList(env.getProperty("proxy.ports").split(";")).forEach(port -> {
            try {
                InetSocketAddress socketAddress = new InetSocketAddress(Integer.valueOf(port));
                serverActorSystem.actorOf(
                        Props.create(SpringDIActor.class, ServerActor.class, socketAddress), "ServerActor_" + port
                );
            } catch (Exception ex) {
                log.error("Can't open port [{}]: {}", port, ex.getMessage());
            }
        });

        return serverActorSystem;
    }

    @Bean(name = "clientActorSystem")
    public ActorSystem clientActorSystem() {
        ActorSystem clientActorSystem = ActorSystem.create("tcp-proxy-client");
        clientActorSystem.actorOf(
                Props.create(ClientActor.class).withRouter(new RoundRobinPool(AVAILABLE_PROCESSORS)), "ClientActor"
        );

        return clientActorSystem;
    }
}
