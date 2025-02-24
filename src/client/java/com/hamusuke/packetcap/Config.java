package com.hamusuke.packetcap;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue SHOW_PACKET_FLOW = BUILDER
            .comment("In overlay, whether to show packet flow: e.g. C2S or S2C")
            .define("showPacketFlow", false);
    private static final ForgeConfigSpec.BooleanValue SHOW_PACKET_NAME_POSTFIX = BUILDER
            .comment("In overlay, whether to show postfix of packet name: e.g. ...Packet")
            .define("showPacketNamePostfix", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean showPacketFlow;
    public static boolean showPacketNamePostfix;

    public static void save() {
        SHOW_PACKET_FLOW.set(showPacketFlow);
        SHOW_PACKET_NAME_POSTFIX.set(showPacketNamePostfix);
        SPEC.save();
    }
}
