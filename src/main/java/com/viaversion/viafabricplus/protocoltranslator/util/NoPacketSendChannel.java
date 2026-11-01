package com.viaversion.viafabricplus.protocoltranslator.util;

import io.netty.channel.local.LocalChannel;

public final class NoPacketSendChannel extends LocalChannel {

    public static final NoPacketSendChannel INSTANCE = new NoPacketSendChannel();

    private NoPacketSendChannel() {
    }

}
