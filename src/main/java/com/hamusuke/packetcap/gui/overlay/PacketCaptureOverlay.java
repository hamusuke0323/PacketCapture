package com.hamusuke.packetcap.gui.overlay;

import com.hamusuke.packetcap.Config;
import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.utils.ByteConversion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PacketCaptureOverlay {
    private static final int INDEX_OFFSET = 4;
    private final Minecraft mc;
    private final PacketCapture capture;

    public PacketCaptureOverlay(Minecraft mc, PacketCapture capture) {
        this.mc = mc;
        this.capture = capture;
    }

    public void render(GuiGraphics gui) {
        this.drawSentPackets(gui);
        this.drawReceivedPackets(gui);
    }

    private static String applyConfig(String name) {
        if (!Config.showPacketFlow) {
            name = name.replace("Serverbound", "").replace("Clientbound", "");
        }
        if (!Config.showPacketNamePostfix) {
            name = name.replace("Packet", "");
        }

        return name;
    }

    private void drawSentPackets(GuiGraphics gui) {
        var list = this.capture.getSentPackets();
        int size = list.size();
        long sent = this.capture.getSentPacketNum();
        long bytes = this.capture.getSentBytes();
        var component = this.mc.isLocalServer() ?
                Component.translatable(PacketCapture.MOD_ID + ".sent.detail",
                        Component.literal("" + sent)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT))) :
                Component.translatable(PacketCapture.MOD_ID + ".sent.detail.dedicated",
                        Component.literal("" + sent)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Component.literal("" + bytes)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Component.literal(ByteConversion.convertBytes(bytes))
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));

        gui.fill(1, 2 - 1, 2 + this.mc.font.width(component) + 1, 2 + 9 - 1, -1873784752);
        gui.drawString(this.mc.font, component, 2, 2, 14737632, false);

        if (size == 0) {
            return;
        }

        for (int i = Mth.clamp(size - 1 - this.mc.getWindow().getGuiScaledHeight() / 9 + INDEX_OFFSET, 0, size - 1); i < size; ++i) {
            var s = Component.literal(applyConfig(list.get(i).getPacketClassName())).withStyle(style -> style.withFont(PacketCapture.MONO_FONT));
            int k = this.mc.font.width(s);
            if (k > 0) {
                int i1 = 2 + 9 + 9 * (size - 1 - i);
                gui.fill(1, i1 - 1, 2 + k + 1, i1 + 9 - 1, -1873784752);
                gui.drawString(this.mc.font, s, 2, i1, 14737632, false);
            }
        }
    }

    private void drawReceivedPackets(GuiGraphics gui) {
        var list = this.capture.getReceivedPackets();
        int size = list.size();
        long received = this.capture.getReceivedPacketNum();
        long bytes = this.capture.getReceivedBytes();
        var component = this.mc.isLocalServer() ?
                Component.translatable(PacketCapture.MOD_ID + ".received.detail",
                        Component.literal("" + received)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT))) :
                Component.translatable(PacketCapture.MOD_ID + ".received.detail.dedicated",
                        Component.literal("" + received)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Component.literal("" + bytes)
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Component.literal(ByteConversion.convertBytes(bytes))
                                .withStyle(style -> style.withFont(PacketCapture.MONO_FONT)));

        int k2 = this.mc.font.width(component);
        int l2 = this.mc.getWindow().getGuiScaledWidth() - 2 - k2;
        gui.fill(l2 - 1, 2 - 1, l2 + k2 + 1, 2 + 9 - 1, -1873784752);
        gui.drawString(this.mc.font, component, l2, 2, 14737632, false);

        if (size == 0) {
            return;
        }

        for (int i = Mth.clamp(size - 1 - this.mc.getWindow().getGuiScaledHeight() / 9 + INDEX_OFFSET, 0, size - 1); i < size; ++i) {
            var s = Component.literal(applyConfig(list.get(i).getPacketClassName())).withStyle(style -> style.withFont(PacketCapture.MONO_FONT));
            int k = this.mc.font.width(s);
            if (k > 0) {
                int l = this.mc.getWindow().getGuiScaledWidth() - 2 - k;
                int i1 = 2 + 9 + 9 * (size - 1 - i);
                gui.fill(l - 1, i1 - 1, l + k + 1, i1 + 9 - 1, -1873784752);
                gui.drawString(this.mc.font, s, l, i1, 14737632, false);
            }
        }
    }
}
