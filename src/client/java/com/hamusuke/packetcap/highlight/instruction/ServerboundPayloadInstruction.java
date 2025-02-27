package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.DataHighlightInstruction;
import com.hamusuke.packetcap.highlight.DataHighlightInstructions;
import com.hamusuke.packetcap.highlight.Highlight;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.writeAndGuess;

public class ServerboundPayloadInstruction<B extends PacketByteBuf, P extends CustomPayload> implements BufInstruction<B, P> {
    protected static final BufInstruction<? super PacketByteBuf, ? super CustomPayload> WRITE_AND_GUESS = writeAndGuess((b, payload) ->
                    b.writeIdentifier(payload.getId().id()),
            (b, payload) ->
                    CustomPayloadC2SPacket.CODEC.encode(b, new CustomPayloadC2SPacket(payload)), p -> "");

    @Override
    public List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, P value) {
        DataHighlightInstruction instruction = DataHighlightInstructions.getFrom(value.getClass());
        if (instruction == null) {
            return WRITE_AND_GUESS.write(curWriterIndex, receivedByteBuf, buf, value);
        }

        return instruction.write(curWriterIndex, receivedByteBuf, buf, value);
    }
}
