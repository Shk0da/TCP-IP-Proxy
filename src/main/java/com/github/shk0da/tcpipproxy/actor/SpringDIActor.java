package com.github.shk0da.tcpipproxy.actor;

import akka.actor.Actor;
import akka.actor.IndirectActorProducer;
import com.github.shk0da.tcpipproxy.provider.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;

@Slf4j
public class SpringDIActor implements IndirectActorProducer {

    private Actor actorInstance;
    private Class<? extends Actor> type;

    private InetSocketAddress socketAddress;

    public SpringDIActor(Class<? extends Actor> type, InetSocketAddress socketAddress) {
        this.type = type;
        this.socketAddress = socketAddress;
    }

    @Override
    public Class<? extends Actor> actorClass() {
        return type;
    }

    @Override
    public Actor produce() {
        Actor newActor = actorInstance;
        actorInstance = null;
        if (newActor == null) {
            try {
                newActor = type.getConstructor(InetSocketAddress.class).newInstance(socketAddress);
            } catch (InvocationTargetException | NoSuchMethodException | InstantiationException
                    | IllegalAccessException | IllegalArgumentException | SecurityException e) {
                log.error("Unable to create actor of type:{}", type, e);
            }
        }

        ApplicationContextProvider.getApplicationContext().getAutowireCapableBeanFactory().autowireBean(newActor);
        return newActor;
    }
}