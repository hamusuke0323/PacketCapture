package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.event.RegisterHighlightInstructionEvent;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction.PacketDataHighlighterBuilder;
import com.hamusuke.packetcap.invoker.LoginHelloS2CPacketAccessor;
import com.hamusuke.packetcap.invoker.LoginKeyC2SPacketAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.encoding.StringEncoding;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponsePayload;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.s2c.login.*;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.network.packet.s2c.query.PingResultS2CPacket;
import net.minecraft.network.packet.s2c.query.QueryResponseS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.ServerMetadata;
import net.minecraft.text.Text.Serialization;
import net.minecraft.text.TextCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.*;

public class DataHighlightInstructions {
    private static final Map<Class<?>, DataHighlightInstruction<? extends ByteBuf, ?>> HIGHLIGHTERS = Maps.newHashMap();

    public static final DataHighlightInstruction<ByteBuf, Property> PROPERTY = register(Property.class, builder -> builder
            .field(STRING.withDescription(s -> "Property Name: " + s), Property::name)
            .field(STRING.withDescription(s -> "Property Value: " + s), Property::value)
            .sub(bufNullable(String.class, s -> "Signature", (buf, value) -> StringEncoding.encode(buf, value, 1024)), Property::signature));

    public static final DataHighlightInstruction<ByteBuf, PropertyMap> PROPERTY_MAP = register(PropertyMap.class, builder -> builder
            .list(PROPERTY, ForwardingMultimap::values));

    public static final DataHighlightInstruction<ByteBuf, GameProfile> GAME_PROFILE = register(GameProfile.class, builder -> builder
            .field(UUID.withDescription(uuid -> "UUID: " + uuid), GameProfile::getId)
            .field(STRING.withDescription(s -> "Name: " + s), GameProfile::getName)
            .sub(PROPERTY_MAP, GameProfile::getProperties));

    public static final DataHighlightInstruction<RegistryByteBuf, RegistryKey> REGISTRY_KEY = registry(RegistryKey.class, builder -> builder
            .packetEncoder(PacketByteBuf::writeRegistryKey, RegistryKey::toString, Function.identity()));

    public static final DataHighlightInstruction<PacketByteBuf, byte[]> BYTE_ARRAY_WITH_LEN = packet(byte[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Byte Array Length: " + length), bytes -> bytes.length)
            .field(BYTE_ARRAY.withDescription(bytes -> "Data"), Function.identity()));

    public static final DataHighlightInstruction<RegistryByteBuf, ItemStack> ITEM_STACK = registry(ItemStack.class, builder -> builder
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

        // HANDSHAKE
        packet(HandshakeC2SPacket.class, builder -> builder
                .field(VAR_INT, HandshakeC2SPacket::protocolVersion)
                .field(STRING, HandshakeC2SPacket::address)
                .field(SHORT, p -> (short) p.port())
                .field(VAR_INT, p -> p.intendedState().getId()));

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
        packet(LoginCompressionS2CPacket.class, builder -> builder
                .field(VAR_INT, LoginCompressionS2CPacket::getCompressionThreshold));
        packet(LoginDisconnectS2CPacket.class, builder -> builder
                .field(STRING, p -> Serialization.toJsonString(p.getReason(), DynamicRegistryManager.EMPTY)));
        packet(LoginHelloS2CPacket.class, builder -> builder
                .field(STRING, LoginHelloS2CPacket::getServerId)
                .sub(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacketAccessor::getPublicKeyBytes)
                .sub(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacket::getNonce)
                .field(BOOL, LoginHelloS2CPacket::needsAuthentication));
        packet(LoginQueryRequestS2CPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryRequestS2CPacket::queryId)
                .field(IDENTIFIER, p -> p.payload().id())
                .packetEncoder((buf, value) -> value.write(buf), LoginQueryRequestS2CPacket::payload));
        packet(LoginSuccessS2CPacket.class, builder -> builder
                .sub(GAME_PROFILE, LoginSuccessS2CPacket::profile));

        // PLAY
        registry(CreativeInventoryActionC2SPacket.class, builder -> builder
                .constant(SHORT)
                .sub(ITEM_STACK, CreativeInventoryActionC2SPacket::stack));

        registry(GameJoinS2CPacket.class, builder -> builder
                .constant(INT)
                .constant(BOOL)
                .list(REGISTRY_KEY, p -> List.copyOf(p.dimensionIds()))
                .field(VAR_INT, GameJoinS2CPacket::maxPlayers)
                .field(VAR_INT, GameJoinS2CPacket::viewDistance)
                .field(VAR_INT, GameJoinS2CPacket::simulationDistance)
                .constant(BOOL)
                .constant(BOOL)
                .constant(BOOL)
                .packetEncoder((buf, value) -> value.write(buf), GameJoinS2CPacket::commonPlayerSpawnInfo)
                .constant(BOOL));

        registry(WorldTimeUpdateS2CPacket.class, builder -> builder
                .constant(LONG)
                .constant(LONG)
                .constant(BOOL));

        registry(EntitiesDestroyS2CPacket.class, builder -> builder
                .list(VAR_INT, EntitiesDestroyS2CPacket::getEntityIds));

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

        // Fire event
        RegisterHighlightInstructionEvent.EVENT.invoker().onRegister();
    }

    public static <T> DataHighlightInstruction<PacketByteBuf, T> nullable(Class<T> clazz, PacketEncoder<PacketByteBuf, T> encoder) {
        return nullable(clazz, t -> "", encoder);
    }

    public static <T> DataHighlightInstruction<PacketByteBuf, T> nullable(Class<T> clazz, Function<T, String> descriptor, PacketEncoder<PacketByteBuf, T> encoder) {
        return packet(clazz, b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Function.identity()));
    }

    public static <T> DataHighlightInstruction<ByteBuf, T> bufNullable(Class<T> clazz, PacketEncoder<ByteBuf, T> encoder) {
        return bufNullable(clazz, t -> "", encoder);
    }

    public static <T> DataHighlightInstruction<ByteBuf, T> bufNullable(Class<T> clazz, Function<T, String> descriptor, PacketEncoder<ByteBuf, T> encoder) {
        return register(clazz, b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Function.identity()));
    }

    public static <T> DataHighlightInstruction<PacketByteBuf, T> packet(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<PacketByteBuf, T>> consumer) {
        return register(clazz, consumer);
    }

    public static <T> DataHighlightInstruction<RegistryByteBuf, T> registry(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<RegistryByteBuf, T>> consumer) {
        return register(clazz, consumer);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, Consumer<PacketDataHighlighterBuilder<B, T>> consumer) {
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
