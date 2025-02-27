package com.hamusuke.packetcap.highlight.instruction;

import com.hamusuke.packetcap.highlight.DataHighlightInstruction;
import com.hamusuke.packetcap.highlight.DataHighlightInstructions;
import com.hamusuke.packetcap.highlight.Highlight;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.readAndGuess;

public class ClientboundPayloadInstruction<B extends RegistryByteBuf, P extends CustomPayload> implements BufInstruction<B, P> {
    protected static final BufInstruction<? super RegistryByteBuf, ? super CustomPayload> READ_AND_GUESS = readAndGuess(b -> b.readerIndex(b.readerIndex() + b.readableBytes()), p -> "");

    @Override
    public List<Highlight<?>> write(int curWriterIndex, @Nullable B receivedByteBuf, B buf, P value) {
        DataHighlightInstruction instruction = DataHighlightInstructions.getFrom(value.getClass());
        if (instruction == null) {
            return READ_AND_GUESS.write(curWriterIndex, receivedByteBuf, buf, value);
        }

        return instruction.write(curWriterIndex, receivedByteBuf, buf, value);
    }
}
