package com.github.shk0da.tcpipproxy.actor;

import akka.actor.ActorSystem;
import akka.actor.UntypedAbstractActor;
import akka.io.Tcp;
import lombok.extern.slf4j.Slf4j;
import com.github.shk0da.tcpipproxy.config.ActorConfig;

@Slf4j
public class ProxyHandler extends UntypedAbstractActor {

    private final ActorSystem clientActorSystem;

    public ProxyHandler(ActorSystem clientActorSystem) {
        this.clientActorSystem = clientActorSystem;
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof Tcp.Received) {
            Messages.Packet packet = new Messages.Packet(getSender(), ((Tcp.Received) msg).data());
            clientActorSystem.actorSelection(ActorConfig.CLIENT_ACTOR_PATH_HEAD).tell(packet, getSender());
        } else if (msg instanceof Tcp.ConnectionClosed) {
            getContext().stop(getSelf());
        }
    }
}