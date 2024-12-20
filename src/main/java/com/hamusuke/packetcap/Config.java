package com.hamusuke.packetcap;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import static com.hamusuke.packetcap.PacketCapture.MOD_ID;

@Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue SHOW_PACKET_FLOW = BUILDER
            .comment("In overlay, whether to show packet flow: e.g. Serverbound~ or Clientbound~")
            .define("showPacketFlow", false);
    private static final ForgeConfigSpec.BooleanValue SHOW_PACKET_NAME_POSTFIX = BUILDER
            .comment("In overlay, whether to show postfix of packet name: e.g. Clientbound...Packet")
            .define("showPacketNamePostfix", false);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean showPacketFlow;
    public static boolean showPacketNamePostfix;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        showPacketFlow = SHOW_PACKET_FLOW.get();
        showPacketNamePostfix = SHOW_PACKET_NAME_POSTFIX.get();
    }

    public static void save() {
        SHOW_PACKET_FLOW.set(showPacketFlow);
        SHOW_PACKET_NAME_POSTFIX.set(showPacketNamePostfix);
        SPEC.save();
    }
}
