package com.github.shk0da.tcpipproxy.actor;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.io.Tcp;
import akka.io.TcpMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Slf4j
@Scope("prototype")
@Component("ServerActor")
public class ServerActor extends UntypedAbstractActor {

    private final InetSocketAddress socketAddress;

    private ActorRef tcpManager;

    @Autowired
    @Qualifier("clientActorSystem")
    private ActorSystem clientActorSystem;

    public ServerActor(InetSocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    @Override
    public void preStart() throws Exception {
        tcpManager = Tcp.get(getContext().system()).manager();
        tcpManager.tell(TcpMessage.bind(getSelf(), socketAddress, 100), getSelf());
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Tcp.Bound) {
            tcpManager.tell(message, getSelf());
        } else if (message instanceof Tcp.CommandFailed) {
            getContext().stop(getSelf());
        } else if (message instanceof Tcp.Connected) {
            final Tcp.Connected conn = (Tcp.Connected) message;
            tcpManager.tell(conn, getSelf());
            ActorRef handler = getContext().actorOf(Props.create(ProxyHandler.class, clientActorSystem));
            getSender().tell(TcpMessage.register(handler, true, true), getSelf());
        } else {
            // Unknown message
            unhandled(message);
        }
    }
}
