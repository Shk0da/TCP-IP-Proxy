package com.github.shk0da.tcpipproxy.actor;

import akka.actor.ActorRef;
import akka.util.ByteString;
import lombok.AllArgsConstructor;
import lombok.Data;

public enum Messages {

    NOTHING;

    @Data
    @AllArgsConstructor
    public static class Packet {
        ActorRef sender;
        ByteString message;
    }
}
