package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.PacketCaptureApi;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction.DataHighlightInstructionBuilder;
import com.hamusuke.packetcap.highlight.instruction.BufInstruction;
import com.hamusuke.packetcap.highlight.instruction.ClientboundPayloadInstruction;
import com.hamusuke.packetcap.highlight.instruction.ServerboundPayloadInstruction;
import com.hamusuke.packetcap.invoker.LoginHelloS2CPacketAccessor;
import com.hamusuke.packetcap.invoker.LoginKeyC2SPacketAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.message.ArgumentSignatureDataMap;
import net.minecraft.network.message.ArgumentSignatureDataMap.Entry;
import net.minecraft.network.message.ChatVisibility;
import net.minecraft.network.message.LastSeenMessageList.Acknowledgment;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.c2s.play.AdvancementTabC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.common.*;
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket;
import net.minecraft.network.packet.s2c.config.FeaturesS2CPacket;
import net.minecraft.network.packet.s2c.config.SelectKnownPacksS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestPayload;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.book.RecipeBookType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SerializableRegistries.SerializedRegistryEntry;
import net.minecraft.registry.VersionedIdentifier;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagPacketSerializer.Serialized;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text.Serialization;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.*;

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
            .field(STRING, Identifier::toString));

    public static final DataHighlightInstruction<ByteBuf, RegistryKey> REGISTRY_KEY = register(RegistryKey.class, builder -> builder
            .compoundField(IDENTIFIER, Identifier::toString, RegistryKey::getValue));

    public static final DataHighlightInstruction<ByteBuf, Property> PROPERTY = register(Property.class, builder -> builder
            .compoundField(STRING, s -> "Property Name: " + s, Property::name)
            .compoundField(STRING, s -> "Property Value: " + s, Property::value)
            .field(nullable(s -> "Signature", STRING), Property::signature));

    public static final DataHighlightInstruction<ByteBuf, PropertyMap> PROPERTY_MAP = register(PropertyMap.class, builder -> builder
            .listWithSize(PROPERTY, ForwardingMultimap::values));

    public static final DataHighlightInstruction<ByteBuf, GameProfile> GAME_PROFILE = register(GameProfile.class, builder -> builder
            .field(UUID.withDescription(uuid -> "UUID: " + uuid), GameProfile::getId)
            .compoundField(STRING, s -> "Name: " + s, GameProfile::getName)
            .field(PROPERTY_MAP, GameProfile::getProperties));

    public static final DataHighlightInstruction<ByteBuf, byte[]> BYTE_ARRAY_WITH_LEN = register(byte[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Byte Array Length: " + length), bytes -> bytes.length)
            .field(BYTE_ARRAY.withDescription(bytes -> "Data"), Function.identity()));

    public static final DataHighlightInstruction<RegistryByteBuf, ItemStack> ITEM_STACK = register(ItemStack.class, builder -> builder
            .field(VAR_INT.withDescription(count -> count <= 0 ? "Empty" : "Count: " + count), ItemStack::getCount, count -> count > 0)
            .packetCodec(PacketCodecs.registryEntry(RegistryKeys.ITEM), e -> "ID: " + e.getIdAsString(), ItemStack::getRegistryEntry)
            .packetCodec(ComponentChanges.PACKET_CODEC, componentChanges -> "ComponentChanges", stack -> stack.getComponents() instanceof MergedComponentMap m ? m.getChanges() : ComponentChanges.EMPTY));

    public static final DataHighlightInstruction<ByteBuf, VersionedIdentifier> VERSIONED_IDENTIFIER = register(VersionedIdentifier.class, builder -> builder
            .compoundField(STRING, id -> "namespace: " + id, VersionedIdentifier::namespace)
            .compoundField(STRING, id -> "id: " + id, VersionedIdentifier::id)
            .compoundField(STRING, id -> "version: " + id, VersionedIdentifier::version));

    public static final DataHighlightInstruction<ByteBuf, SerializedRegistryEntry> SERIALIZED_REGISTRY_ENTRY = register(SerializedRegistryEntry.class, builder -> builder
            .field(IDENTIFIER, SerializedRegistryEntry::id)
            .packetCodec(PacketCodecs.NBT_ELEMENT.collect(PacketCodecs::optional), nbt -> nbt.isEmpty() ? "Empty" : "NBT Element", SerializedRegistryEntry::data));

    public static final DataHighlightInstruction<PacketByteBuf, IntList> INT_LIST = packet(IntList.class, builder -> builder
            .listWithSize(VAR_INT, Function.identity()));

    public static final DataHighlightInstruction<PacketByteBuf, Serialized> TAG_SERIALIZED = packet(Serialized.class, builder -> builder
            .mapWithSize(IDENTIFIER, INT_LIST, m -> "Contents", Either.right(buf -> {
                return buf.readMap(Maps::newLinkedHashMapWithExpectedSize, PacketByteBuf::readIdentifier, PacketByteBuf::readIntList);
            })));

    public static final DataHighlightInstruction<ByteBuf, BlockPos> BLOCK_POS = register(BlockPos.class, builder -> builder
            .field(LONG.withDescription(l -> "Position(x, y, z) = (" + BlockPos.fromLong(l).toShortString() + ")"), BlockPos::asLong));

    public static final DataHighlightInstruction<PacketByteBuf, GlobalPos> GLOBAL_POS = packet(GlobalPos.class, builder -> builder
            .compoundField(REGISTRY_KEY, e -> "Dimension: " + e.toString(), GlobalPos::dimension)
            .field(BLOCK_POS, GlobalPos::pos));

    public static final DataHighlightInstruction<RegistryByteBuf, CommonPlayerSpawnInfo> COMMON_PLAYER_SPAWN_INFO = registry(CommonPlayerSpawnInfo.class, builder -> builder
            .packetCodec(DimensionType.PACKET_CODEC, e -> "Dimension Type: " + e.getIdAsString(), CommonPlayerSpawnInfo::dimensionType)
            .compoundField(REGISTRY_KEY, e -> "Dimension: " + e.toString(), CommonPlayerSpawnInfo::dimension)
            .field(LONG.withDescription(l -> "Seed: " + l), CommonPlayerSpawnInfo::seed)
            .field(BYTE.withDescription(b -> "GameMode: " + GameMode.byId(b)), i -> (byte) i.gameMode().getId())
            .field(BYTE.withDescription(b -> "Previous GameMode: " + GameMode.byId(b)), i -> (byte) GameMode.getId(i.prevGameMode()))
            .field(BOOL.withDescription(prefixed("Is Debug")), CommonPlayerSpawnInfo::isDebug)
            .field(BOOL.withDescription(prefixed("Is Flat")), CommonPlayerSpawnInfo::isFlat)
            .compoundField(optional(pos -> "Pos: " + pos, GLOBAL_POS), i -> "Last Death Location:", CommonPlayerSpawnInfo::lastDeathLocation)
            .field(VAR_INT.withDescription(cd -> "Portal Cooldown: " + cd), CommonPlayerSpawnInfo::portalCooldown)
            .field(VAR_INT.withDescription(sl -> "Sea Level: " + sl), CommonPlayerSpawnInfo::seaLevel));

    public static final DataHighlightInstruction<PacketByteBuf, SyncedClientOptions> SYNCED_CLIENT_OPTIONS = packet(SyncedClientOptions.class, builder -> builder
            .compoundField(STRING, l -> "Language: " + l, SyncedClientOptions::language)
            .field(BYTE.withDescription(d -> "View Distance: " + d), p -> (byte) p.viewDistance())
            .field(VAR_INT.withDescription(o -> "Chat Visibility: " + ChatVisibility.values()[o]), p -> p.chatVisibility().ordinal())
            .field(BOOL.withDescription(prefixed("Chat Colors Enabled")), SyncedClientOptions::chatColorsEnabled)
            .field(BYTE.withDescription(b -> "Player Model Parts: " + b), p -> (byte) p.playerModelParts())
            .field(VAR_INT.withDescription(o -> "Main Arm: " + Arm.values()[o]), p -> p.mainArm().ordinal())
            .field(BOOL.withDescription(prefixed("Filters Text")), SyncedClientOptions::filtersText)
            .field(BOOL.withDescription(prefixed("Allows Server Listing")), SyncedClientOptions::allowsServerListing)
            .field(VAR_INT.withDescription(o -> "Particle Status: " + ParticlesMode.values()[o]), p -> p.particleStatus().ordinal()));

    public static final DataHighlightInstruction<ByteBuf, Instant> INSTANT = register(Instant.class, builder -> builder
            .constant(LONG));

    public static final Function<Integer, DataHighlightInstruction<PacketByteBuf, BitSet>> SIZED_BIT_SET = size -> make(builder -> builder
            .packetEncoder((buf, value) -> buf.writeBitSet(value, size), Function.identity()));

    public static final DataHighlightInstruction<ByteBuf, MessageSignatureData> MESSAGE_SIGNATURE_DATA = register(MessageSignatureData.class, builder -> builder
            .field(BYTE_ARRAY, MessageSignatureData::data));

    public static final DataHighlightInstruction<PacketByteBuf, Entry> ARG_ENTRY = packet(Entry.class, builder -> builder
            .compoundField(STRING, s -> "name", Entry::name)
            .compoundField(MESSAGE_SIGNATURE_DATA, s -> "signature", Entry::signature));

    public static final DataHighlightInstruction<PacketByteBuf, ArgumentSignatureDataMap> ARGUMENT_SIGNATURE_DATA_MAP = packet(ArgumentSignatureDataMap.class, builder -> builder
            .listWithSize(ARG_ENTRY, list -> "entries", ArgumentSignatureDataMap::entries));

    public static final DataHighlightInstruction<PacketByteBuf, Acknowledgment> LSM_ACK = packet(Acknowledgment.class, builder -> builder
            .field(VAR_INT.withDescription(o -> "offset"), Acknowledgment::offset)
            .compoundField(SIZED_BIT_SET.apply(20), o -> "acknowledged", Acknowledgment::acknowledged));

    public static final DataHighlightInstruction<ByteBuf, NetworkRecipeId> NETWORK_RECIPE_ID = register(NetworkRecipeId.class, builder -> builder
            .field(VAR_INT, NetworkRecipeId::index));

    public static final DataHighlightInstruction<PacketByteBuf, LoginQueryRequestPayload> LOGIN_QUERY_REQUEST_PAYLOAD = packet(LoginQueryRequestPayload.class, builder -> builder
            .field(IDENTIFIER, LoginQueryRequestPayload::id)
            .packetEncoderV(LoginQueryRequestPayload::write, Function.identity()));

    public static final DataHighlightInstruction<ByteBuf, BlockHitResult> BLOCK_HIT_RESULT = register(BlockHitResult.class, builder -> builder
            .field(BLOCK_POS, BlockHitResult::getBlockPos)
            .field(VAR_INT.withDescription(o -> "Side: " + Direction.values()[o]).xmap(Direction::ordinal), BlockHitResult::getSide)
            .constant(FLOAT)
            .constant(FLOAT)
            .constant(FLOAT)
            .field(BOOL.withDescription(prefixed("Is Inside Block")), BlockHitResult::isInsideBlock)
            .field(BOOL.withDescription(prefixed("Is Against World Border")), BlockHitResult::isAgainstWorldBorder));

    public static final DataHighlightInstruction<PacketByteBuf, PlayerInteractEntityC2SPacket.InteractTypeHandler> INTERACT_TYPE_HANDLER = register(PlayerInteractEntityC2SPacket.InteractTypeHandler.class, builder -> builder
            .field(VAR_INT.withDescription(o -> "Type: " + PlayerInteractEntityC2SPacket.InteractType.values()[o]).xmap(Enum::ordinal), PlayerInteractEntityC2SPacket.InteractTypeHandler::getType)
            .field((curWriterIndex, receivedByteBuf, buf, value) -> {
                if (value == PlayerInteractEntityC2SPacket.ATTACK) {
                    return List.of();
                }

                if (value instanceof PlayerInteractEntityC2SPacket.InteractHandler h) {
                    return VAR_INT.withDescription(o -> "Hand: " + Hand.values()[o]).write(curWriterIndex, receivedByteBuf, buf, h.hand.ordinal());
                }

                if (value instanceof PlayerInteractEntityC2SPacket.InteractAtHandler h) {
                    var handlerHighlighter = DataHighlightInstructionBuilder.<ByteBuf, PlayerInteractEntityC2SPacket.InteractAtHandler>builder()
                            .field(FLOAT.withDescription(x -> "x: " + x), p -> (float) p.pos.x)
                            .field(FLOAT.withDescription(x -> "y: " + x), p -> (float) p.pos.y)
                            .field(FLOAT.withDescription(x -> "z: " + x), p -> (float) p.pos.z)
                            .field(VAR_INT.withDescription(o -> "Hand: " + Hand.values()[o]), p -> p.hand.ordinal())
                            .build();
                    return handlerHighlighter.write(curWriterIndex, receivedByteBuf, buf, h);
                }

                return BufInstruction
                        .<PacketByteBuf, PlayerInteractEntityC2SPacket.InteractTypeHandler>writeAndGuess((byteBuf, o) -> o.write(byteBuf), s -> "")
                        .write(curWriterIndex, receivedByteBuf, buf, value);
            }, Function.identity()));

    public static final DataHighlightInstruction<ByteBuf, Vec3d> VEC3D = register(Vec3d.class, builder -> builder
            .field(DOUBLE.withDescription(x -> "x: " + x), Vec3d::getX)
            .field(DOUBLE.withDescription(y -> "y: " + y), Vec3d::getY)
            .field(DOUBLE.withDescription(z -> "z: " + z), Vec3d::getZ));

    static {
        // HANDSHAKE
        registerHandshakePackets();

        // LOGIN
        registerLoginC2SPackets();
        registerLoginS2CPackets();

        // COMMON
        registerCommonC2SPackets();
        registerCommonS2CPackets();

        // CONFIG
        registerConfigC2SPackets();
        registerConfigS2CPackets();

        // PLAY
        registerPlayC2SPackets();
        registerPlayS2CPackets();

        // Fire event
        PacketCapture.getInstance().getApis().forEach(PacketCaptureApi::onRegisterHighlightInstructions);
    }

    private static void registerHandshakePackets() {
        packet(HandshakeC2SPacket.class, builder -> builder
                .field(VAR_INT, HandshakeC2SPacket::protocolVersion)
                .compoundField(STRING, HandshakeC2SPacket::address)
                .field(SHORT, p -> (short) p.port())
                .field(VAR_INT, p -> p.intendedState().getId()));
    }

    private static void registerLoginC2SPackets() {
        packet(LoginHelloC2SPacket.class, builder -> builder
                .compoundField(STRING, s -> "Player Name: " + s, LoginHelloC2SPacket::name)
                .field(UUID.withDescription(uuid -> "Player UUID: " + uuid), LoginHelloC2SPacket::profileId));
        packet(LoginKeyC2SPacket.class, builder -> builder
                .compoundField(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getEncryptedSecretKey)
                .compoundField(BYTE_ARRAY_WITH_LEN, LoginKeyC2SPacketAccessor::getNonce));
        packet(LoginQueryResponseC2SPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryResponseC2SPacket::queryId)
                .compoundField(nullable(p -> "Payload Data", (buf, value) -> value.write(buf)), LoginQueryResponseC2SPacket::response));
    }

    private static void registerLoginS2CPackets() {
        packet(LoginDisconnectS2CPacket.class, builder -> builder
                .field(STRING, p -> Serialization.toJsonString(p.getReason(), DynamicRegistryManager.EMPTY)));
        packet(LoginHelloS2CPacket.class, builder -> builder
                .compoundField(STRING, LoginHelloS2CPacket::getServerId)
                .compoundField(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacketAccessor::getPublicKeyBytes)
                .compoundField(BYTE_ARRAY_WITH_LEN, LoginHelloS2CPacket::getNonce)
                .field(BOOL, LoginHelloS2CPacket::needsAuthentication));
        packet(LoginQueryRequestS2CPacket.class, builder -> builder
                .field(VAR_INT, LoginQueryRequestS2CPacket::queryId)
                .compoundField(LOGIN_QUERY_REQUEST_PAYLOAD, LoginQueryRequestS2CPacket::payload));
    }

    private static void registerCommonC2SPackets() {
        packet(ClientOptionsC2SPacket.class, builder -> builder
                .compoundField(SYNCED_CLIENT_OPTIONS, ClientOptionsC2SPacket::options));
        packet(CustomPayloadC2SPacket.class, builder -> builder
                .compoundField(DataHighlightInstructionBuilder.<PacketByteBuf, CustomPayload>builder()
                        .compoundField(IDENTIFIER, id -> "Payload ID: " + id.toString(), payload -> payload.getId().id())
                        .compoundField(new ServerboundPayloadInstruction<>(), p -> "Payload Data", Function.identity()).build(), CustomPayloadC2SPacket::payload));
        packet(ResourcePackStatusC2SPacket.class, builder -> builder
                .constant(UUID)
                .field(VAR_INT, p -> p.status().ordinal()));
    }

    private static void registerCommonS2CPackets() {
        packet(CookieRequestS2CPacket.class, builder -> builder
                .compoundField(IDENTIFIER, CookieRequestS2CPacket::key));
        registry(CustomPayloadS2CPacket.class, builder -> builder
                .compoundField(DataHighlightInstructionBuilder.<RegistryByteBuf, CustomPayload>builder()
                        .compoundField(IDENTIFIER, id -> "Payload ID: " + id.toString(), payload -> payload.getId().id())
                        .compoundField(new ClientboundPayloadInstruction<>(), p -> "Payload Data", Function.identity()).build(), CustomPayloadS2CPacket::payload));
        packet(CustomReportDetailsS2CPacket.class, builder -> builder
                .mapWithSize(STRING, STRING, m -> "", Either.right(buf -> {
                    return buf.readMap(Maps::newLinkedHashMapWithExpectedSize, PacketCodecs.string(128), PacketCodecs.string(4096));
                })));
        packet(ResourcePackRemoveS2CPacket.class, builder -> builder
                .compoundField(optional(t -> "", (buf, value) -> buf.writeUuid(value)), ResourcePackRemoveS2CPacket::id));
        packet(ResourcePackSendS2CPacket.class, builder -> builder
                .field(UUID, ResourcePackSendS2CPacket::id)
                .compoundField(STRING, ResourcePackSendS2CPacket::url)
                .compoundField(STRING, ResourcePackSendS2CPacket::hash)
                .field(BOOL, ResourcePackSendS2CPacket::required)
                .compoundField(optional(t -> "", TextCodecs.PACKET_CODEC), ResourcePackSendS2CPacket::prompt));
        packet(ServerTransferS2CPacket.class, builder -> builder
                .compoundField(STRING, ServerTransferS2CPacket::host)
                .field(VAR_INT, ServerTransferS2CPacket::port));
        packet(StoreCookieS2CPacket.class, builder -> builder
                .compoundField(IDENTIFIER, StoreCookieS2CPacket::key)
                .compoundField(BYTE_ARRAY_WITH_LEN, StoreCookieS2CPacket::payload));
        packet(SynchronizeTagsS2CPacket.class, builder -> builder
                .mapWithSize(REGISTRY_KEY, TAG_SERIALIZED, m -> "", Either.right(buf -> {
                    return buf.readMap(Maps::newLinkedHashMapWithExpectedSize, PacketByteBuf::readRegistryRefKey, Serialized::fromBuf);
                })));
    }

    private static void registerConfigC2SPackets() {
        register(SelectKnownPacksC2SPacket.class, builder -> builder
                .listWithSize(VERSIONED_IDENTIFIER, SelectKnownPacksC2SPacket::knownPacks));
    }

    private static void registerConfigS2CPackets() {
        packet(DynamicRegistriesS2CPacket.class, builder -> builder
                .compoundField(REGISTRY_KEY, DynamicRegistriesS2CPacket::registry)
                .listWithSize(SERIALIZED_REGISTRY_ENTRY, DynamicRegistriesS2CPacket::entries));
        packet(FeaturesS2CPacket.class, builder -> builder
                .listWithSize(IDENTIFIER, c -> "", Either.right(buf -> {
                    return buf.readCollection(Lists::newArrayListWithExpectedSize, PacketByteBuf::readIdentifier);
                })));
        register(SelectKnownPacksS2CPacket.class, builder -> builder
                .listWithSize(VERSIONED_IDENTIFIER, SelectKnownPacksS2CPacket::knownPacks));
    }

    private static void registerPlayC2SPackets() {
        packet(AdvancementTabC2SPacket.class, builder -> builder
                .field(VAR_INT.withDescription(o -> "Action: " + Action.values()[o]), p -> p.getAction().ordinal(), o -> Action.values()[o] == Action.OPENED_TAB)
                .compoundField(IDENTIFIER, AdvancementTabC2SPacket::getTabToOpen));
        packet(BoatPaddleStateC2SPacket.class, builder -> builder
                .constant(BOOL)
                .constant(BOOL));
        packet(BookUpdateC2SPacket.class, builder -> builder
                .field(VAR_INT, BookUpdateC2SPacket::slot)
                .listWithSize(STRING, BookUpdateC2SPacket::pages)
                .compoundField(optional(s -> "", STRING), BookUpdateC2SPacket::title));
        packet(BundleItemSelectedC2SPacket.class, builder -> builder
                .field(VAR_INT, BundleItemSelectedC2SPacket::slotId)
                .field(VAR_INT, BundleItemSelectedC2SPacket::selectedItemIndex));
        packet(ButtonClickC2SPacket.class, builder -> builder
                .field(VAR_INT, ButtonClickC2SPacket::syncId)
                .field(VAR_INT, ButtonClickC2SPacket::buttonId));
        packet(ChatCommandSignedC2SPacket.class, builder -> builder
                .compoundField(STRING, ChatCommandSignedC2SPacket::command)
                .field(INSTANT, ChatCommandSignedC2SPacket::timestamp)
                .constant(LONG)
                .compoundField(ARGUMENT_SIGNATURE_DATA_MAP, ChatCommandSignedC2SPacket::argumentSignatures)
                .compoundField(LSM_ACK, ChatCommandSignedC2SPacket::lastSeenMessages));
        packet(ChatMessageC2SPacket.class, builder -> builder
                .compoundField(STRING, ChatMessageC2SPacket::chatMessage)
                .field(INSTANT, ChatMessageC2SPacket::timestamp)
                .constant(LONG)
                .compoundField(nullable(d -> "", MESSAGE_SIGNATURE_DATA), ChatMessageC2SPacket::signature)
                .compoundField(LSM_ACK, ChatMessageC2SPacket::acknowledgment));

        registry(ClickSlotC2SPacket.class, builder -> builder
                .field(VAR_INT, ClickSlotC2SPacket::getSyncId)
                .field(VAR_INT, ClickSlotC2SPacket::getRevision)
                .constant(SHORT)
                .constant(BYTE)
                .field(VAR_INT.withDescription(o -> "Slot Action Type: " + SlotActionType.values()[o]), p -> p.getActionType().ordinal())
                .indexed(6).mapWithSize(
                        SHORT.withDescription(s -> "")
                                .xmap(Integer::shortValue), ITEM_STACK,
                        m -> "",
                        Either.left(ClickSlotC2SPacket::getModifiedStacks))
                .indexed(5).compoundField(ITEM_STACK, ClickSlotC2SPacket::getStack));

        packet(ClientCommandC2SPacket.class, builder -> builder
                .field(VAR_INT, ClientCommandC2SPacket::getEntityId)
                .field(VAR_INT.withDescription(o -> "Mode: " + Mode.values()[o]), p -> p.getMode().ordinal())
                .field(VAR_INT, ClientCommandC2SPacket::getMountJumpHeight));

        packet(ClientStatusC2SPacket.class, builder -> builder
                .field(VAR_INT.withDescription(o -> "Mode: " + ClientStatusC2SPacket.Mode.values()[o]), p -> p.getMode().ordinal()));

        packet(CraftRequestC2SPacket.class, builder -> builder
                .field(VAR_INT, CraftRequestC2SPacket::syncId)
                .field(NETWORK_RECIPE_ID, CraftRequestC2SPacket::recipeId)
                .field(BOOL, CraftRequestC2SPacket::craftAll));

        registry(CreativeInventoryActionC2SPacket.class, builder -> builder
                .constant(SHORT)
                .compoundField(ITEM_STACK, CreativeInventoryActionC2SPacket::stack));

        packet(JigsawGeneratingC2SPacket.class, builder -> builder
                .field(BLOCK_POS, JigsawGeneratingC2SPacket::getPos)
                .field(VAR_INT, JigsawGeneratingC2SPacket::getMaxDepth)
                .field(BOOL, JigsawGeneratingC2SPacket::shouldKeepJigsaws));

        register(PickItemFromBlockC2SPacket.class, builder -> builder
                .field(BLOCK_POS, PickItemFromBlockC2SPacket::pos)
                .field(BOOL, PickItemFromBlockC2SPacket::includeData));

        register(PickItemFromEntityC2SPacket.class, builder -> builder
                .field(VAR_INT, PickItemFromEntityC2SPacket::id)
                .field(BOOL, PickItemFromEntityC2SPacket::includeData));

        packet(PlayerActionC2SPacket.class, builder -> builder
                .indexed(2).field(VAR_INT.withDescription(o -> "Action: " + PlayerActionC2SPacket.Action.values()[o]), p -> p.getAction().ordinal())
                .indexed(0).field(BLOCK_POS, PlayerActionC2SPacket::getPos)
                .indexed(1).field(BYTE.withDescription(b -> "Direction: " + Direction.byId(b)), p -> (byte) p.getDirection().getId())
                .indexed(3).field(VAR_INT, PlayerActionC2SPacket::getSequence));

        packet(PlayerInteractBlockC2SPacket.class, builder -> builder
                .indexed(1).field(VAR_INT.withDescription(o -> "Hand: " + Hand.values()[o]), p -> p.getHand().ordinal())
                .indexed(0).compoundField(BLOCK_HIT_RESULT, PlayerInteractBlockC2SPacket::getBlockHitResult)
                .indexed(2).field(VAR_INT, PlayerInteractBlockC2SPacket::getSequence));

        packet(PlayerInteractEntityC2SPacket.class, builder -> builder
                .field(VAR_INT, p -> p.entityId)
                .compoundField(INTERACT_TYPE_HANDLER, p -> p.type)
                .constant(BOOL));

        packet(PlayerInteractItemC2SPacket.class, builder -> builder
                .field(VAR_INT, p -> p.getHand().ordinal())
                .field(VAR_INT, PlayerInteractItemC2SPacket::getSequence)
                .field(FLOAT, PlayerInteractItemC2SPacket::getYaw)
                .field(FLOAT, PlayerInteractItemC2SPacket::getPitch));

        packet(PlayerMoveC2SPacket.Full.class, builder -> builder
                .constant(DOUBLE)
                .constant(DOUBLE)
                .constant(DOUBLE)
                .constant(FLOAT)
                .constant(FLOAT)
                .constant(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());

        packet(PlayerMoveC2SPacket.PositionAndOnGround.class, builder -> builder
                .constant(DOUBLE)
                .constant(DOUBLE)
                .constant(DOUBLE)
                .notBeWritten()
                .notBeWritten()
                .constant(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());

        packet(PlayerMoveC2SPacket.LookAndOnGround.class, builder -> builder
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .constant(FLOAT)
                .constant(FLOAT)
                .constant(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());

        packet(PlayerMoveC2SPacket.OnGroundOnly.class, builder -> builder
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .constant(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());

        // TODO: PlayerSession

        packet(QueryBlockNbtC2SPacket.class, builder -> builder
                .field(VAR_INT, QueryBlockNbtC2SPacket::getTransactionId)
                .compoundField(BLOCK_POS, QueryBlockNbtC2SPacket::getPos));

        packet(QueryEntityNbtC2SPacket.class, builder -> builder
                .field(VAR_INT, QueryEntityNbtC2SPacket::getTransactionId)
                .field(VAR_INT, QueryEntityNbtC2SPacket::getEntityId));

        packet(RecipeCategoryOptionsC2SPacket.class, builder -> builder
                .field(VAR_INT.withDescription(o -> "Recipe Book Type: " + RecipeBookType.values()[o]).xmap(Enum::ordinal), RecipeCategoryOptionsC2SPacket::getCategory)
                .constant(BOOL)
                .constant(BOOL));

        packet(RequestCommandCompletionsC2SPacket.class, builder -> builder
                .field(VAR_INT, RequestCommandCompletionsC2SPacket::getCompletionId)
                .compoundField(STRING, RequestCommandCompletionsC2SPacket::getPartialCommand));

        packet(SlotChangedStateC2SPacket.class, builder -> builder
                .field(VAR_INT, SlotChangedStateC2SPacket::slotId)
                .field(VAR_INT, SlotChangedStateC2SPacket::screenHandlerId)
                .constant(BOOL));

        registry(UpdateBeaconC2SPacket.class, builder -> builder
                .compoundField(
                        optional(t -> "Status Effect: " + t.getIdAsString(), DataHighlightInstructionBuilder.<RegistryByteBuf, RegistryEntry<StatusEffect>>builder()
                                .packetEncoder(PacketCodecs.registryEntry(RegistryKeys.STATUS_EFFECT), Function.identity()).build()), UpdateBeaconC2SPacket::primary)
                .compoundField(
                        optional(t -> "Status Effect: " + t.getIdAsString(), DataHighlightInstructionBuilder.<RegistryByteBuf, RegistryEntry<StatusEffect>>builder()
                                .packetEncoder(PacketCodecs.registryEntry(RegistryKeys.STATUS_EFFECT), Function.identity()).build()), UpdateBeaconC2SPacket::secondary));

        packet(UpdateCommandBlockC2SPacket.class, builder -> builder
                .compoundField(BLOCK_POS, UpdateCommandBlockC2SPacket::getPos)
                .compoundField(STRING, UpdateCommandBlockC2SPacket::getCommand)
                .indexed(5).field(VAR_INT.withDescription(o -> "Type: " + CommandBlockBlockEntity.Type.values()[o]).xmap(Enum::ordinal), UpdateCommandBlockC2SPacket::getType)
                .indexed(2).constant(BYTE)
                .indexed(3).sameAs(3)
                .indexed(4).sameAs(3));

        packet(UpdateCommandBlockMinecartC2SPacket.class, builder -> builder
                .field(VAR_INT, p -> p.entityId)
                .compoundField(STRING, UpdateCommandBlockMinecartC2SPacket::getCommand)
                .constant(BOOL));

        packet(UpdateDifficultyC2SPacket.class, builder -> builder
                .field(BYTE.withDescription(o -> "Difficulty: " + Difficulty.byId(o)).xmap(Integer::byteValue).xmap(Difficulty::getId), UpdateDifficultyC2SPacket::getDifficulty));

        packet(UpdateJigsawC2SPacket.class, builder -> builder
                .compoundField(BLOCK_POS, UpdateJigsawC2SPacket::getPos)
                .compoundField(IDENTIFIER, UpdateJigsawC2SPacket::getName)
                .compoundField(IDENTIFIER, UpdateJigsawC2SPacket::getTarget)
                .compoundField(IDENTIFIER, UpdateJigsawC2SPacket::getPool)
                .compoundField(STRING, UpdateJigsawC2SPacket::getFinalState)
                .compoundField(STRING.xmap(JigsawBlockEntity.Joint::asString), UpdateJigsawC2SPacket::getJointType)
                .field(VAR_INT, UpdateJigsawC2SPacket::getSelectionPriority)
                .field(VAR_INT, UpdateJigsawC2SPacket::getPlacementPriority));

        packet(UpdateSignC2SPacket.class, builder -> builder
                .compoundField(BLOCK_POS, UpdateSignC2SPacket::getPos)
                .indexed(2).constant(BOOL)
                .indexed(1).list(STRING, c -> "", p -> Lists.newArrayList(p.getText())));

        // TODO: UpdateStructureBlock

        packet(VehicleMoveC2SPacket.class, builder -> builder
                .compoundField(VEC3D, VehicleMoveC2SPacket::position)
                .constant(FLOAT)
                .constant(FLOAT)
                .constant(BOOL));
    }

    private static void registerPlayS2CPackets() {
        registry(EntitiesDestroyS2CPacket.class, builder -> builder
                .compoundField(INT_LIST, EntitiesDestroyS2CPacket::getEntityIds));

        registry(EntityEquipmentUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityEquipmentUpdateS2CPacket::getEntityId)
                .list(DataHighlightInstructionBuilder.<RegistryByteBuf, Pair<EquipmentSlot, ItemStack>>builder()
                        .field(BYTE.withDescription(slot -> {
                            if (EquipmentSlot.values().length <= slot) {
                                return "";
                            }

                            return "Equipment Slot: " + EquipmentSlot.values()[slot];
                        }).xmap(Integer::byteValue).xmap(Enum::ordinal), Pair::getFirst)
                        .compoundField(ITEM_STACK, i -> "Item Stack", Pair::getSecond).build(), c -> "", EntityEquipmentUpdateS2CPacket::getEquipmentList));

        registry(GameJoinS2CPacket.class, builder -> builder
                .constant(INT)
                .constant(BOOL)
                .listWithSize(REGISTRY_KEY, c -> "", Either.right(buf -> {
                    return buf.readCollection(Lists::newArrayListWithExpectedSize, (b) -> b.readRegistryKey(RegistryKeys.WORLD));
                }))
                .field(VAR_INT, GameJoinS2CPacket::maxPlayers)
                .field(VAR_INT, GameJoinS2CPacket::viewDistance)
                .field(VAR_INT, GameJoinS2CPacket::simulationDistance)
                .constant(BOOL)
                .constant(BOOL)
                .constant(BOOL)
                .compoundField(COMMON_PLAYER_SPAWN_INFO, GameJoinS2CPacket::commonPlayerSpawnInfo)
                .constant(BOOL));

        registry(GameMessageS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, GameMessageS2CPacket::content)
                .constant(BOOL));

        registry(InventoryS2CPacket.class, builder -> builder
                .field(VAR_INT, InventoryS2CPacket::getSyncId)
                .field(VAR_INT, InventoryS2CPacket::getRevision)
                .listWithSize(ITEM_STACK, InventoryS2CPacket::getContents)
                .compoundField(ITEM_STACK, InventoryS2CPacket::getCursorStack));

        registry(ScreenHandlerSlotUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getSyncId)
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getRevision)
                .field(SHORT, p -> (short) p.getSlot())
                .compoundField(ITEM_STACK, ScreenHandlerSlotUpdateS2CPacket::getStack));

        registry(WorldTimeUpdateS2CPacket.class, builder -> builder
                .constant(LONG)
                .constant(LONG)
                .constant(BOOL));
    }

    public static <B extends PacketByteBuf, T> DataHighlightInstruction<B, T> packet(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        return register(clazz, consumer);
    }

    public static <B extends RegistryByteBuf, T> DataHighlightInstruction<B, T> registry(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        return register(clazz, consumer);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        var builder = DataHighlightInstructionBuilder.<B, T>builder();
        consumer.accept(builder);
        var built = builder.build();
        HIGHLIGHTERS.put(clazz, built);
        return built;
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(Function<T, String> descriptor, PacketEncoder<B, T> encoder) {
        return make(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Present" : "Empty"), Optional::isPresent, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Optional::get));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(Function<T, String> descriptor, DataHighlightInstruction<B, T> sub) {
        return make(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Present" : "Empty"), Optional::isPresent, Boolean::booleanValue)
                .compoundField(sub, descriptor, Optional::get));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(PacketEncoder<B, T> encoder) {
        return nullable(t -> "", encoder);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(Function<T, String> descriptor, PacketEncoder<B, T> encoder) {
        return make(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Function.identity()));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(Function<T, String> descriptor, DataHighlightInstruction<B, T> sub) {
        return make(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Not Null" : "Null"), Objects::nonNull, Boolean::booleanValue)
                .compoundField(sub, descriptor, Function.identity()));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> make(Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        var builder = DataHighlightInstructionBuilder.<B, T>builder();
        consumer.accept(builder);
        return builder.build();
    }

    @Nullable
    public static DataHighlightInstruction<? extends ByteBuf, ?> getFrom(Class<?> clazz) {
        return HIGHLIGHTERS.get(clazz);
    }
}
