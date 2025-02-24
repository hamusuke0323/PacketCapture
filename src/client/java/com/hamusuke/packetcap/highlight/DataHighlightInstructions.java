package com.hamusuke.packetcap.highlight;

import com.google.common.collect.Maps;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction.PacketDataHighlighterBuilder;
import com.hamusuke.packetcap.invoker.LoginKeyC2SPacketAccessor;
import io.netty.buffer.ByteBuf;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponsePayload;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.TextCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.*;

public class DataHighlightInstructions {
    private static final Map<Class<?>, DataHighlightInstruction<? extends ByteBuf, ?>> HIGHLIGHTERS = Maps.newHashMap();

    private static final DataHighlightInstruction<PacketByteBuf, byte[]> BYTE_ARRAY_WITH_LEN = packet(byte[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Byte Array Length: " + length), bytes -> bytes.length)
            .field(BYTE_ARRAY.withDescription(bytes -> "Data"), Function.identity()));
    private static final DataHighlightInstruction<RegistryByteBuf, ItemStack> ITEM_STACK = registry(ItemStack.class, builder -> builder
            .field(VAR_INT.withDescription(count -> count <= 0 ? "Empty" : "Count: " + count), ItemStack::getCount, count -> count > 0)
            .packetCodec(PacketCodecs.registryEntry(RegistryKeys.ITEM), e -> "ID: " + e.getIdAsString(), ItemStack::getRegistryEntry)
            .packetCodec(ComponentChanges.PACKET_CODEC, componentChanges -> "ComponentChanges", stack -> stack.getComponents() instanceof MergedComponentMap m ? m.getChanges() : ComponentChanges.EMPTY));

    static {
        // QUERY
        register(QueryPingC2SPacket.class, builder -> builder
                .constant(LONG));
        register(PingResultS2CPacket.class, builder -> builder
                .constant(LONG));
        packet(QueryResponseS2CPacket.class, builder -> builder
                .jsonCodec(ServerMetadata.CODEC, s -> "ServerMetadata: Json format", QueryResponseS2CPacket::metadata));

        // LOGIN
        packet(LoginHelloC2SPacket.class, builder -> builder
                .field(STRING.withDescription(s -> "Player Name: " + s), LoginHelloC2SPacket::name)
                .field(UUID.withDescription(uuid -> "Player UUID: " + uuid), LoginHelloC2SPacket::profileId));
        packet(LoginKeyC2SPacket.class, builder -> builder
                .sub(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getEncryptedSecretKey)
                .sub(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getNonce));
        packet(LoginQueryResponseC2SPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryResponseC2SPacket::queryId)
                .sub(nullable(LoginQueryResponsePayload.class, (buf, value) -> value.write(buf)), LoginQueryResponseC2SPacket::response));

        packet(HandshakeC2SPacket.class, builder -> builder
                .field(VAR_INT, HandshakeC2SPacket::protocolVersion)
                .field(STRING, HandshakeC2SPacket::address)
                .field(SHORT, p -> (short) p.port())
                .field(VAR_INT, p -> p.intendedState().getId()));


        packet(UpdatePlayerAbilitiesC2SPacket.class, builder -> builder
                .constant(BYTE));

        registry(GameMessageS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, GameMessageS2CPacket::content)
                .constant(BOOL));

        registry(SetCursorItemS2CPacket.class, builder -> builder
                .sub(ITEM_STACK, SetCursorItemS2CPacket::contents));

        registry(ScreenHandlerSlotUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getSyncId)
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getRevision)
                .field(SHORT, p -> (short) p.getSlot())
                .sub(ITEM_STACK, ScreenHandlerSlotUpdateS2CPacket::getStack));
    }

    private static <T> DataHighlightInstruction<PacketByteBuf, T> nullable(Class<T> clazz, PacketEncoder<PacketByteBuf, T> encoder) {
        return nullable(clazz, t -> "", encoder);
    }

    private static <T> DataHighlightInstruction<PacketByteBuf, T> nullable(Class<T> clazz, Function<T, String> descriptor, PacketEncoder<PacketByteBuf, T> encoder) {
        return packet(clazz, b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Function.identity()));
    }

    private static <T> DataHighlightInstruction<PacketByteBuf, T> packet(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<PacketByteBuf, T>> consumer) {
        return register(clazz, consumer);
    }

    private static <T> DataHighlightInstruction<RegistryByteBuf, T> registry(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<RegistryByteBuf, T>> consumer) {
        return register(clazz, consumer);
    }

    private static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<B, T>> consumer) {
        var builder = PacketDataHighlighterBuilder.<B, T>builder();
        consumer.accept(builder);
        var built = builder.build();
        HIGHLIGHTERS.put(clazz, built);
        return built;
    }

    @Nullable
    public static DataHighlightInstruction<? extends ByteBuf, ?> getFrom(Class<?> clazz) {
        return HIGHLIGHTERS.get(clazz);
    }
}
