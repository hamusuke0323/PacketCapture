package com.hamusuke.packetcap.gui.hud;

import com.hamusuke.packetcap.Config;
import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.utils.ByteConversion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class PacketCaptureHud {
    private static final int INDEX_OFFSET = 4;
    private final MinecraftClient mc;
    private final PacketCapture capture;

    public PacketCaptureHud(MinecraftClient mc, PacketCapture capture) {
        this.mc = mc;
        this.capture = capture;
    }

    public void render(DrawContext gui) {
        this.drawSentPackets(gui);
        this.drawReceivedPackets(gui);
    }

    private static String applyConfig(String name) {
        if (!Config.showPacketFlow) {
            name = name.replace("C2S", "").replace("S2C", "");
        }
        if (!Config.showPacketNamePostfix) {
            name = name.replace("Packet", "");
        }

        return name;
    }

    private void drawSentPackets(DrawContext gui) {
        var list = this.capture.getSentPackets();
        int size = list.size();
        long sent = this.capture.getSentPacketNum();
        long bytes = this.capture.getSentBytes();
        var component =
                Text.translatable(PacketCapture.MOD_ID + ".sent.detail.dedicated",
                        Text.literal("" + sent)
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Text.literal("" + bytes)
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Text.literal(ByteConversion.convertBytes(bytes))
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)));

        gui.fill(1, 2 - 1, 2 + this.mc.textRenderer.getWidth(component) + 1, 2 + 9 - 1, -1873784752);
        gui.drawText(this.mc.textRenderer, component, 2, 2, 14737632, false);

        if (size == 0) {
            return;
        }

        for (int i = MathHelper.clamp(size - 1 - this.mc.getWindow().getScaledHeight() / 9 + INDEX_OFFSET, 0, size - 1); i < size; ++i) {
            var s = Text.literal(applyConfig(list.get(i).getPacketClassName())).styled(style -> style.withFont(PacketCapture.MONO_FONT));
            int k = this.mc.textRenderer.getWidth(s);
            if (k > 0) {
                int i1 = 2 + 9 + 9 * (size - 1 - i);
                gui.fill(1, i1 - 1, 2 + k + 1, i1 + 9 - 1, -1873784752);
                gui.drawText(this.mc.textRenderer, s, 2, i1, 14737632, false);
            }
        }
    }

    private void drawReceivedPackets(DrawContext gui) {
        var list = this.capture.getReceivedPackets();
        int size = list.size();
        long received = this.capture.getReceivedPacketNum();
        long bytes = this.capture.getReceivedBytes();
        var component =
                Text.translatable(PacketCapture.MOD_ID + ".received.detail.dedicated",
                        Text.literal("" + received)
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Text.literal("" + bytes)
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)),
                        Text.literal(ByteConversion.convertBytes(bytes))
                                .styled(style -> style.withFont(PacketCapture.MONO_FONT)));

        int k2 = this.mc.textRenderer.getWidth(component);
        int l2 = this.mc.getWindow().getScaledWidth() - 2 - k2;
        gui.fill(l2 - 1, 2 - 1, l2 + k2 + 1, 2 + 9 - 1, -1873784752);
        gui.drawText(this.mc.textRenderer, component, l2, 2, 14737632, false);

        if (size == 0) {
            return;
        }

        for (int i = MathHelper.clamp(size - 1 - this.mc.getWindow().getScaledHeight() / 9 + INDEX_OFFSET, 0, size - 1); i < size; ++i) {
            var s = Text.literal(applyConfig(list.get(i).getPacketClassName())).styled(style -> style.withFont(PacketCapture.MONO_FONT));
            int k = this.mc.textRenderer.getWidth(s);
            if (k > 0) {
                int l = this.mc.getWindow().getScaledWidth() - 2 - k;
                int i1 = 2 + 9 + 9 * (size - 1 - i);
                gui.fill(l - 1, i1 - 1, l + k + 1, i1 + 9 - 1, -1873784752);
                gui.drawText(this.mc.textRenderer, s, l, i1, 14737632, false);
            }
        }
    }
}
