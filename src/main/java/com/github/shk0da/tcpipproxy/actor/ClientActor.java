package com.github.shk0da.tcpipproxy.actor;

import akka.actor.ActorRef;
import akka.actor.UntypedAbstractActor;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.github.shk0da.tcpipproxy.provider.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import com.github.shk0da.tcpipproxy.actor.client.TcpBalancer;
import com.github.shk0da.tcpipproxy.actor.client.TcpClient;

@Slf4j
@Scope("prototype")
@Component("ClientActor")
public class ClientActor extends UntypedAbstractActor {

    @Override
    public void onReceive(Object msg) throws Throwable {
        if (msg instanceof Messages.Packet) {
            TcpClient tcpClient = ApplicationContextProvider.getApplicationContext()
                    .getBean(TcpBalancer.class)
                    .getClientInstance();

            if (tcpClient == null) {
                log.error("It was not possible to find working connection");
                return;
            }

            try {
                final ActorRef sender = ((Messages.Packet) msg).getSender();
                final ByteString message = ((Messages.Packet) msg).getMessage();

                sender.tell(TcpMessage.resumeWriting(), getSender());
                sender.tell(TcpMessage.write(tcpClient.send(message)), getSender());
                sender.tell(TcpMessage.confirmedClose(), getSender());
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }
}
