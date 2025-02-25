package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.PacketCaptureApi;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction.DataHighlighterBuilder;
import com.hamusuke.packetcap.invoker.LoginHelloS2CPacketAccessor;
import com.hamusuke.packetcap.invoker.LoginKeyC2SPacketAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestPayload;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text.Serialization;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.*;
import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.guessing;

public class DataHighlightInstructions {
    private static final Map<Class<?>, DataHighlightInstruction<? extends ByteBuf, ?>> HIGHLIGHTERS = Maps.newHashMap();

    public static final DataHighlightInstruction<ByteBuf, String> STRING = register(String.class, builder -> builder
            .field(VAR_INT.withDescription(len -> "String Length: " + len), s -> {
                var byteBuf = Unpooled.buffer();

                try {
                    return ByteBufUtil.writeUtf8(byteBuf, s);
                } finally {
                    byteBuf.release();
                }
            })
            .field(BYTE_BUF.withDescription(byteBuf -> "String Data"), s -> {
                var byteBuf = Unpooled.buffer();

                try {
                    ByteBufUtil.writeUtf8(byteBuf, s);
                    return byteBuf;
                } finally {
                    byteBuf.release();
                }
            }));

    public static final DataHighlightInstruction<ByteBuf, Identifier> IDENTIFIER = register(Identifier.class, builder -> builder
            .sub(STRING, identifier -> "ID (String)", Identifier::toString));

    public static final DataHighlightInstruction<RegistryByteBuf, RegistryKey> REGISTRY_KEY = register(RegistryKey.class, builder -> builder
            .sub(IDENTIFIER, Identifier::toString, RegistryKey::getValue));

    public static final DataHighlightInstruction<ByteBuf, Property> PROPERTY = register(Property.class, builder -> builder
            .sub(STRING, s -> "Property Name: " + s, Property::name)
            .sub(STRING, s -> "Property Value: " + s, Property::value)
            .sub(nullable(s -> "Signature", STRING), Property::signature));

    public static final DataHighlightInstruction<ByteBuf, PropertyMap> PROPERTY_MAP = register(PropertyMap.class, builder -> builder
            .listWithSize(PROPERTY, ForwardingMultimap::values));

    public static final DataHighlightInstruction<ByteBuf, GameProfile> GAME_PROFILE = register(GameProfile.class, builder -> builder
            .field(UUID.withDescription(uuid -> "UUID: " + uuid), GameProfile::getId)
            .sub(STRING, s -> "Name: " + s, GameProfile::getName)
            .sub(PROPERTY_MAP, GameProfile::getProperties));

    public static final DataHighlightInstruction<ByteBuf, byte[]> BYTE_ARRAY_WITH_LEN = register(byte[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Byte Array Length: " + length), bytes -> bytes.length)
            .field(BYTE_ARRAY.withDescription(bytes -> "Data"), Function.identity()));

    public static final DataHighlightInstruction<RegistryByteBuf, ItemStack> ITEM_STACK = register(ItemStack.class, builder -> builder
            .field(VAR_INT.withDescription(count -> count <= 0 ? "Empty" : "Count: " + count), ItemStack::getCount, count -> count > 0)
            .packetCodec(PacketCodecs.registryEntry(RegistryKeys.ITEM), e -> "ID: " + e.getIdAsString(), ItemStack::getRegistryEntry)
            .packetCodec(ComponentChanges.PACKET_CODEC, componentChanges -> "ComponentChanges", stack -> stack.getComponents() instanceof MergedComponentMap m ? m.getChanges() : ComponentChanges.EMPTY));

    static {
        // HANDSHAKE
        packet(HandshakeC2SPacket.class, builder -> builder
                .field(VAR_INT, HandshakeC2SPacket::protocolVersion)
                .sub(STRING, HandshakeC2SPacket::address)
                .field(SHORT, p -> (short) p.port())
                .field(VAR_INT, p -> p.intendedState().getId()));

        // LOGIN
        packet(LoginHelloC2SPacket.class, builder -> builder
                .sub(STRING, s -> "Player Name: " + s, LoginHelloC2SPacket::name)
                .field(UUID.withDescription(uuid -> "Player UUID: " + uuid), LoginHelloC2SPacket::profileId));
        packet(LoginKeyC2SPacket.class, builder -> builder
                .sub(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getEncryptedSecretKey)
                .sub(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getNonce));
        packet(LoginQueryResponseC2SPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryResponseC2SPacket::queryId)
                .sub(nullable(p -> "Payload Data", (buf, value) -> value.write(buf)), LoginQueryResponseC2SPacket::response));
        packet(LoginDisconnectS2CPacket.class, builder -> builder
                .sub(STRING, p -> Serialization.toJsonString(p.getReason(), DynamicRegistryManager.EMPTY)));
        packet(LoginHelloS2CPacket.class, builder -> builder
                .sub(STRING, LoginHelloS2CPacket::getServerId)
                .sub(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacketAccessor::getPublicKeyBytes)
                .sub(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacket::getNonce)
                .field(BOOL, LoginHelloS2CPacket::needsAuthentication));
        packet(LoginQueryRequestS2CPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryRequestS2CPacket::queryId)
                .sub(IDENTIFIER, p -> p.payload().id())
                .packetEncoderV(LoginQueryRequestPayload::write, LoginQueryRequestS2CPacket::payload));

        // PLAY
        registry(CreativeInventoryActionC2SPacket.class, builder -> builder
                .constant(SHORT)
                .sub(ITEM_STACK, CreativeInventoryActionC2SPacket::stack));

        packet(CustomPayloadC2SPacket.class, builder -> builder
                .sub(DataHighlighterBuilder.<PacketByteBuf, CustomPayload>builder()
                        .sub(IDENTIFIER, id -> "Payload ID: " + id.toString(), payload -> payload.getId().id())
                        .field(guessing((b, payload) ->
                                                b.writeIdentifier(payload.getId().id()),
                                        (b, payload) ->
                                                CustomPayloadC2SPacket.CODEC.encode(b, new CustomPayloadC2SPacket(payload)),
                                        packet -> "Payload Data"),
                                Function.identity()).build(), CustomPayloadC2SPacket::payload));

        registry(CustomPayloadS2CPacket.class, builder -> builder
                .sub(DataHighlighterBuilder.<RegistryByteBuf, CustomPayload>builder()
                        .sub(IDENTIFIER, id -> "Payload ID: " + id.toString(), payload -> payload.getId().id())
                        .field(guessing((b, payload) ->
                                                b.writeIdentifier(payload.getId().id()),
                                        (b, payload) -> {
                                            // Either
                                            int start = b.writerIndex();
                                            CustomPayloadS2CPacket.PLAY_CODEC.encode(b, new CustomPayloadS2CPacket(payload));
                                            if (start != b.writerIndex()) { // If succeeded writing, abort.
                                                return;
                                            }

                                            CustomPayloadS2CPacket.CONFIGURATION_CODEC.encode(b, new CustomPayloadS2CPacket(payload));
                                        },
                                        packet -> "Payload Data"),
                                Function.identity()).build(), CustomPayloadS2CPacket::payload));

        registry(EntityEquipmentUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityEquipmentUpdateS2CPacket::getEntityId)
                .list(DataHighlighterBuilder.<RegistryByteBuf, Pair<EquipmentSlot, ItemStack>>builder()
                        .field(BYTE.withDescription(slot -> {
                            if (EquipmentSlot.values().length <= slot) {
                                return "";
                            }

                            return "Equipment Slot: " + EquipmentSlot.values()[slot];
                        }), p -> (byte) (p.getFirst().ordinal()))
                        .sub(ITEM_STACK, Pair::getSecond).build(), c -> "", EntityEquipmentUpdateS2CPacket::getEquipmentList));

        registry(GameJoinS2CPacket.class, builder -> builder
                .constant(INT)
                .constant(BOOL)
                .listWithSize(REGISTRY_KEY, p -> List.copyOf(p.dimensionIds()))
                .field(VAR_INT, GameJoinS2CPacket::maxPlayers)
                .field(VAR_INT, GameJoinS2CPacket::viewDistance)
                .field(VAR_INT, GameJoinS2CPacket::simulationDistance)
                .constant(BOOL)
                .constant(BOOL)
                .constant(BOOL)
                .packetEncoderV(CommonPlayerSpawnInfo::write, GameJoinS2CPacket::commonPlayerSpawnInfo)
                .constant(BOOL));

        registry(WorldTimeUpdateS2CPacket.class, builder -> builder
                .constant(LONG)
                .constant(LONG)
                .constant(BOOL));

        registry(EntitiesDestroyS2CPacket.class, builder -> builder
                .listWithSize(VAR_INT, EntitiesDestroyS2CPacket::getEntityIds));

        registry(GameMessageS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, GameMessageS2CPacket::content)
                .constant(BOOL));

        registry(ScreenHandlerSlotUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getSyncId)
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getRevision)
                .field(SHORT, p -> (short) p.getSlot())
                .sub(ITEM_STACK, ScreenHandlerSlotUpdateS2CPacket::getStack));

        // Fire event
        PacketCapture.getInstance().getApis().forEach(PacketCaptureApi::onRegisterHighlightInstructions);
    }

    public static <T> void packet(Class<T> clazz, Consumer<DataHighlighterBuilder<PacketByteBuf, T>> consumer) {
        register(clazz, consumer);
    }

    public static <T> void registry(Class<T> clazz, Consumer<DataHighlighterBuilder<RegistryByteBuf, T>> consumer) {
        register(clazz, consumer);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, Consumer<DataHighlighterBuilder<B, T>> consumer) {
        var builder = DataHighlighterBuilder.<B, T>builder();
        consumer.accept(builder);
        var built = builder.build();
        HIGHLIGHTERS.put(clazz, built);
        return built;
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(PacketEncoder<B, T> encoder) {
        return nullable(t -> "", encoder);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(Function<T, String> descriptor, PacketEncoder<B, T> encoder) {
        return dontRegister(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Function.identity()));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(Function<T, String> descriptor, DataHighlightInstruction<B, T> sub) {
        return dontRegister(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .sub(sub, descriptor, Function.identity()));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> dontRegister(Consumer<DataHighlighterBuilder<B, T>> consumer) {
        var builder = DataHighlighterBuilder.<B, T>builder();
        consumer.accept(builder);
        return builder.build();
    }

    @Nullable
    public static DataHighlightInstruction<? extends ByteBuf, ?> getFrom(Class<?> clazz) {
        return HIGHLIGHTERS.get(clazz);
    }
}
