package com.hamusuke.packetcap.highlight;

import com.google.common.collect.ForwardingMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hamusuke.packetcap.PacketCapture;
import com.hamusuke.packetcap.PacketCaptureApi;
import com.hamusuke.packetcap.highlight.DataHighlightInstruction.DataHighlightInstructionBuilder;
import com.hamusuke.packetcap.highlight.instruction.BufInstruction;
import com.hamusuke.packetcap.highlight.instruction.ClientboundPayloadInstruction;
import com.hamusuke.packetcap.highlight.instruction.RecursiveInstruction;
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
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.fabricmc.fabric.impl.networking.CommonRegisterPayload;
import net.fabricmc.fabric.impl.networking.CommonVersionPayload;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.advancement.*;
import net.minecraft.advancement.criterion.CriterionProgress;
import net.minecraft.block.Block;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.MergedComponentMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketEncoder;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.network.encryption.PublicPlayerSession;
import net.minecraft.network.message.*;
import net.minecraft.network.message.ArgumentSignatureDataMap.Entry;
import net.minecraft.network.message.LastSeenMessageList.Acknowledgment;
import net.minecraft.network.packet.BrandCustomPayload;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.predicate.ComponentPredicate;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.NetworkRecipeId;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipePropertySet;
import net.minecraft.recipe.book.RecipeBookOptions;
import net.minecraft.recipe.book.RecipeBookType;
import net.minecraft.recipe.display.CuttingRecipeDisplay;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.registry.*;
import net.minecraft.registry.SerializableRegistries.SerializedRegistryEntry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagPacketSerializer.Serialized;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.NumberFormatTypes;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.stat.Stat;
import net.minecraft.state.State;
import net.minecraft.text.Text.Serialization;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hamusuke.packetcap.highlight.Highlight.NO_HIGHLIGHT;
import static com.hamusuke.packetcap.highlight.Highlight.getWrittenByteLen;
import static com.hamusuke.packetcap.highlight.instruction.BasicInstructions.*;
import static com.hamusuke.packetcap.highlight.instruction.BufInstruction.writeAndGuess;

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
            .compoundField(STRING, s -> "Property Value", Property::value)
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

    public static final DataHighlightInstruction<ByteBuf, long[]> LONG_ARRAY_WITH_LEN = register(long[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Long Array Length: " + length), longs -> longs.length)
            .field(LONG_ARRAY.withDescription(longs -> "Data"), Function.identity()));

    public static final DataHighlightInstruction<ByteBuf, int[]> INT_ARRAY_WITH_LEN = register(int[].class, builder -> builder
            .field(VAR_INT.withDescription(length -> "Integer Array Length: " + length), ints -> ints.length)
            .list(VAR_INT.noDesc(), c -> "Integers", ints -> Arrays.stream(ints).boxed().toList()));

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
            .field(BOOL.withDescription(trueOfFalse("Is Debug")), CommonPlayerSpawnInfo::isDebug)
            .field(BOOL.withDescription(trueOfFalse("Is Flat")), CommonPlayerSpawnInfo::isFlat)
            .compoundField(optional(pos -> "Pos: " + pos, GLOBAL_POS), i -> "Last Death Location:", CommonPlayerSpawnInfo::lastDeathLocation)
            .field(VAR_INT.withDescription(cd -> "Portal Cooldown: " + cd), CommonPlayerSpawnInfo::portalCooldown)
            .field(VAR_INT.withDescription(sl -> "Sea Level: " + sl), CommonPlayerSpawnInfo::seaLevel));

    public static final DataHighlightInstruction<PacketByteBuf, SyncedClientOptions> SYNCED_CLIENT_OPTIONS = packet(SyncedClientOptions.class, builder -> builder
            .compoundField(STRING, l -> "Language: " + l, SyncedClientOptions::language)
            .field(BYTE.withDescription(d -> "View Distance: " + d), p -> (byte) p.viewDistance())
            .field(VAR_INT.withDescription(o -> "Chat Visibility: " + ChatVisibility.values()[o]), p -> p.chatVisibility().ordinal())
            .field(BOOL.withDescription(trueOfFalse("Chat Colors Enabled")), SyncedClientOptions::chatColorsEnabled)
            .field(BYTE.withDescription(b -> "Player Model Parts: " + b), p -> (byte) p.playerModelParts())
            .field(VAR_INT.withDescription(o -> "Main Arm: " + Arm.values()[o]), p -> p.mainArm().ordinal())
            .field(BOOL.withDescription(trueOfFalse("Filters Text")), SyncedClientOptions::filtersText)
            .field(BOOL.withDescription(trueOfFalse("Allows Server Listing")), SyncedClientOptions::allowsServerListing)
            .field(VAR_INT.withDescription(o -> "Particle Status: " + ParticlesMode.values()[o]), p -> p.particleStatus().ordinal()));

    public static final DataHighlightInstruction<ByteBuf, Instant> INSTANT = register(Instant.class, builder -> builder
            .constantSizeOf(LONG));

    public static final Function<Integer, DataHighlightInstruction<PacketByteBuf, BitSet>> SIZED_BIT_SET = size -> make(builder -> builder
            .packetEncoder((buf, value) -> buf.writeBitSet(value, size), Function.identity()));

    public static final DataHighlightInstruction<ByteBuf, MessageSignatureData> MESSAGE_SIGNATURE_DATA = register(MessageSignatureData.class, builder -> builder
            .field(BYTE_ARRAY, MessageSignatureData::data));

    public static final DataHighlightInstruction<ByteBuf, MessageSignatureData.Indexed> MSD_INDEXED = register(MessageSignatureData.Indexed.class, builder -> builder
            .field(VAR_INT.xmap(i -> i.id() + 1), Function.identity(), i -> i.fullSignature() != null)
            .compoundField(MESSAGE_SIGNATURE_DATA, MessageSignatureData.Indexed::fullSignature));

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
            .constantSizeOf(FLOAT)
            .constantSizeOf(FLOAT)
            .constantSizeOf(FLOAT)
            .field(BOOL.withDescription(trueOfFalse("Is Inside Block")), BlockHitResult::isInsideBlock)
            .field(BOOL.withDescription(trueOfFalse("Is Against World Border")), BlockHitResult::isAgainstWorldBorder));

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

    public static final DataHighlightInstruction<ByteBuf, PublicKey> PUBLIC_KEY = register(PublicKey.class, builder -> builder
            .field(BYTE_ARRAY_WITH_LEN, Key::getEncoded));

    public static final DataHighlightInstruction<PacketByteBuf, PlayerPublicKey.PublicKeyData> PUBLIC_KEY_DATA = packet(PlayerPublicKey.PublicKeyData.class, builder -> builder
            .compoundField(INSTANT, i -> "Expires at: " + i.toString(), PlayerPublicKey.PublicKeyData::expiresAt)
            .compoundField(PUBLIC_KEY, k -> "Key", PlayerPublicKey.PublicKeyData::key)
            .compoundField(BYTE_ARRAY_WITH_LEN, ks -> "Key signature", PlayerPublicKey.PublicKeyData::keySignature));

    public static final DataHighlightInstruction<PacketByteBuf, PublicPlayerSession.Serialized> PPS_SERIALIZED = packet(PublicPlayerSession.Serialized.class, builder -> builder
            .field(UUID.withDescription(id -> "Session id: " + id), PublicPlayerSession.Serialized::sessionId)
            .compoundField(PUBLIC_KEY_DATA, k -> "Public key data", PublicPlayerSession.Serialized::publicKeyData));

    public static final DataHighlightInstruction<PacketByteBuf, CriterionProgress> CRITERION_PROGRESS = packet(CriterionProgress.class, builder -> builder
            .compoundField(nullable(i -> "Obtained time: " + i, INSTANT), CriterionProgress::getObtainedTime));

    public static final DataHighlightInstruction<PacketByteBuf, AdvancementProgress> ADVANCEMENT_PROGRESS = packet(AdvancementProgress.class, builder -> builder
            .mapWithSize(STRING, CRITERION_PROGRESS, m -> "Criteria Progresses", Either.right(buf -> {
                return buf.readMap(Maps::newLinkedHashMapWithExpectedSize, PacketByteBuf::readString, CriterionProgress::fromPacket);
            })));

    public static final DataHighlightInstruction<PacketByteBuf, AdvancementRequirements> ADVANCEMENT_REQUIREMENTS = packet(AdvancementRequirements.class, builder -> builder
            .listWithSize(DataHighlightInstructionBuilder.<PacketByteBuf, List<String>>builder()
                    .listWithSize(STRING, Function.identity()).build(), AdvancementRequirements::requirements));

    public static final DataHighlightInstruction<RegistryByteBuf, AdvancementDisplay> ADVANCEMENT_DISPLAY = registry(AdvancementDisplay.class, builder -> builder
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, AdvancementDisplay::getTitle)
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, AdvancementDisplay::getDescription)
            .compoundField(ITEM_STACK, i -> "Icon", AdvancementDisplay::getIcon)
            .field(VAR_INT.withDescription(i -> "Frame").xmap(Enum::ordinal), AdvancementDisplay::getFrame)
            .field(INT.withDescription(nil -> "is background present\nshowToast\nhidden").xmap(p -> 0), Function.identity(), d -> d.getBackground().isPresent())
            .compoundField(IDENTIFIER.xmap(Optional::get), t -> "Background: " + t.get(), AdvancementDisplay::getBackground)
            .restart()
            .field(FLOAT.withDescription(f -> "x: " + f), AdvancementDisplay::getX)
            .field(FLOAT.withDescription(f -> "y: " + f), AdvancementDisplay::getY));

    public static final DataHighlightInstruction<RegistryByteBuf, Advancement> ADVANCEMENT = registry(Advancement.class, builder -> builder
            .compoundField(optional(id -> "Parent: " + id, IDENTIFIER), Advancement::parent)
            .compoundField(optional(d -> "Advancement Display", ADVANCEMENT_DISPLAY), Advancement::display)
            .compoundField(ADVANCEMENT_REQUIREMENTS, r -> "Advancement Requirements", Advancement::requirements)
            .field(BOOL.withDescription(trueOfFalse("Sends Telemetry Event")), Advancement::sendsTelemetryEvent));

    public static final DataHighlightInstruction<RegistryByteBuf, AdvancementEntry> ADVANCEMENT_ENTRY = registry(AdvancementEntry.class, builder -> builder
            .compoundField(IDENTIFIER, id -> "ID: " + id, AdvancementEntry::id)
            .compoundField(ADVANCEMENT, a -> "Advancement", AdvancementEntry::value));

    public static final DataHighlightInstruction<ByteBuf, ChunkPos> CHUNK_POS = register(ChunkPos.class, builder -> builder
            .field(LONG.withDescription(l -> new ChunkPos(l).toString()), ChunkPos::toLong));

    public static final DataHighlightInstruction<ByteBuf, ChunkSectionPos> CHUNK_SECTION_POS = register(ChunkSectionPos.class, builder -> builder
            .field(LONG.withDescription(l -> "(x, y, z) = (" + ChunkSectionPos.from(l).toShortString() + ")"), ChunkSectionPos::asLong));

    public static final DataHighlightInstruction<PacketByteBuf, ChunkBiomeDataS2CPacket.Serialized> CBD_SERIALIZED = packet(ChunkBiomeDataS2CPacket.Serialized.class, builder -> builder
            .compoundField(CHUNK_POS, p -> "Chunk Pos", ChunkBiomeDataS2CPacket.Serialized::pos)
            .compoundField(BYTE_ARRAY_WITH_LEN, b -> "Chunk Sections", ChunkBiomeDataS2CPacket.Serialized::buffer));

    public static final DataHighlightInstruction<RegistryByteBuf, CommandSuggestionsS2CPacket.Suggestion> COMMAND_SUGGESTION = registry(CommandSuggestionsS2CPacket.Suggestion.class, builder -> builder
            .compoundField(STRING, s -> "Text", CommandSuggestionsS2CPacket.Suggestion::text)
            .compoundField(optional(t -> "Tooltip", TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC), CommandSuggestionsS2CPacket.Suggestion::tooltip));

    public static final DataHighlightInstruction<ByteBuf, EntityAttributeModifier> ATTRIBUTE_MODIFIER = register(EntityAttributeModifier.class, builder -> builder
            .compoundField(IDENTIFIER, id -> "ID: " + id, EntityAttributeModifier::id)
            .constantSizeOf(DOUBLE.withDescription(nil -> "Value"))
            .field(VAR_INT.withDescription(o -> "Operation").xmap(EntityAttributeModifier.Operation::getId), EntityAttributeModifier::operation));

    public static final DataHighlightInstruction<RegistryByteBuf, EntityAttributesS2CPacket.Entry> ATTRIBUTE_ENTRY = registry(EntityAttributesS2CPacket.Entry.class, builder -> builder
            .packetCodec(EntityAttribute.PACKET_CODEC, t -> "Attribute: " + t.getIdAsString(), EntityAttributesS2CPacket.Entry::attribute)
            .constantSizeOf(DOUBLE.withDescription(nil -> "Base value"))
            .listWithSize(ATTRIBUTE_MODIFIER, c -> "Attribute Modifiers", EntityAttributesS2CPacket.Entry::modifiers));

    public static final DataHighlightInstruction<PacketByteBuf, PlayerPosition> PLAYER_POSITION = packet(PlayerPosition.class, builder -> builder
            .compoundField(VEC3D, p -> "Position", PlayerPosition::position)
            .compoundField(VEC3D, p -> "Delta Movement", PlayerPosition::deltaMovement)
            .constantSizeOf(FLOAT.withDescription(nil -> "Yaw"))
            .constantSizeOf(FLOAT.withDescription(nil -> "Pitch")));

    public static final DataHighlightInstruction<RegistryByteBuf, TeamS2CPacket.SerializableTeam> TEAM = registry(TeamS2CPacket.SerializableTeam.class, builder -> builder
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, d -> "Display Name", TeamS2CPacket.SerializableTeam::getDisplayName)
            .constantSizeOf(BYTE.withDescription(nil -> "Flags"))
            .compoundField(STRING, s -> "Name Tag Visibility Rule: " + s, TeamS2CPacket.SerializableTeam::getNameTagVisibilityRule)
            .compoundField(STRING, s -> "Collision Rule: " + s, TeamS2CPacket.SerializableTeam::getCollisionRule)
            .field(VAR_INT.withDescription(o -> "Color: " + Formatting.values()[o].getName()).xmap(Enum::ordinal), TeamS2CPacket.SerializableTeam::getColor)
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, d -> "Prefix", TeamS2CPacket.SerializableTeam::getPrefix)
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, d -> "Suffix", TeamS2CPacket.SerializableTeam::getSuffix));

    public static final DataHighlightInstruction<PacketByteBuf, RecipeBookOptions> RECIPE_BOOK_OPTIONS = packet(RecipeBookOptions.class, builder -> builder
            .list((curWriterIndex, receivedByteBuf, buf, value) -> {
                List<Highlight<?>> bools = Lists.newArrayList();
                var h = BOOL.noDesc().write(curWriterIndex, receivedByteBuf, buf, null);
                bools.addAll(h);
                bools.addAll(h);
                return bools;
            }, c -> "", o -> Arrays.stream(RecipeBookType.values()).toList()));

    public static final DataHighlightInstruction<PacketByteBuf, LightData> LIGHT_DATA = packet(LightData.class, builder -> builder
            .compoundField(LONG_ARRAY_WITH_LEN.xmap(BitSet::toLongArray), LightData::getInitedSky)
            .compoundField(LONG_ARRAY_WITH_LEN.xmap(BitSet::toLongArray), LightData::getInitedBlock)
            .compoundField(LONG_ARRAY_WITH_LEN.xmap(BitSet::toLongArray), LightData::getUninitedSky)
            .compoundField(LONG_ARRAY_WITH_LEN.xmap(BitSet::toLongArray), LightData::getUninitedBlock)
            .listWithSize(BYTE_ARRAY_WITH_LEN, LightData::getSkyNibbles)
            .listWithSize(BYTE_ARRAY_WITH_LEN, LightData::getBlockNibbles));

    public static final DataHighlightInstruction<PacketByteBuf, ExperimentalMinecartController.Step> MINECART_CONTROLLER_STEP = packet(ExperimentalMinecartController.Step.class, builder -> builder
            .compoundField(VEC3D, ExperimentalMinecartController.Step::position)
            .compoundField(VEC3D, ExperimentalMinecartController.Step::movement)
            .constantSizeOf(BYTE)
            .constantSizeOf(BYTE)
            .constantSizeOf(FLOAT));

    public static final DataHighlightInstruction<RegistryByteBuf, TradedItem> TRADED_ITEM = registry(TradedItem.class, builder -> builder
            .packetCodec(PacketCodecs.registryEntry(RegistryKeys.ITEM), v -> "Item: " + v.getIdAsString(), TradedItem::item)
            .field(VAR_INT.withDescription(prefixed("Count")), TradedItem::count)
            .packetCodec(ComponentPredicate.PACKET_CODEC, c -> "Component Predicate: " + c.toString(), TradedItem::components));

    public static final DataHighlightInstruction<RegistryByteBuf, TradeOffer> TRADE_OFFER = registry(TradeOffer.class, builder -> builder
            .compoundField(TRADED_ITEM, i -> "First buy item", TradeOffer::getFirstBuyItem)
            .compoundField(ITEM_STACK, i -> "Sell item", TradeOffer::getSellItem)
            .compoundField(optional(TRADED_ITEM), i -> "Second buy item", TradeOffer::getSecondBuyItem)
            .field(BOOL.withDescription(trueOfFalse("Is Disabled")), TradeOffer::isDisabled)
            .field(INT.withDescription(prefixed("Uses")), TradeOffer::getUses)
            .field(INT.withDescription(prefixed("Max Uses")), TradeOffer::getMaxUses)
            .field(INT.withDescription(prefixed("Merchant Experience")), TradeOffer::getMerchantExperience)
            .field(INT.withDescription(prefixed("Special Price")), TradeOffer::getSpecialPrice)
            .field(FLOAT.withDescription(prefixed("Price Multiplier")), TradeOffer::getPriceMultiplier)
            .field(INT.withDescription(prefixed("Demand Bonus")), TradeOffer::getDemandBonus));

    public static final DataHighlightInstruction<ByteBuf, OptionalInt> OPTIONAL_INT = register(OptionalInt.class, builder -> builder
            .field(VAR_INT, i -> i.isPresent() ? i.getAsInt() + 1 : 0));

    public static final DataHighlightInstruction<RegistryByteBuf, RecipeDisplayEntry> RECIPE_DISPLAY_ENTRY = registry(RecipeDisplayEntry.class, builder -> builder
            .compoundField(NETWORK_RECIPE_ID, id -> "ID", RecipeDisplayEntry::id)
            .packetCodec(RecipeDisplay.STREAM_CODEC, d -> "Recipe display", RecipeDisplayEntry::display)
            .compoundField(OPTIONAL_INT, g -> "Group", RecipeDisplayEntry::group)
            .packetCodec(PacketCodecs.registryValue(RegistryKeys.RECIPE_BOOK_CATEGORY), c -> "Category", RecipeDisplayEntry::category)
            .packetCodec(Ingredient.PACKET_CODEC.collect(PacketCodecs.toList()).collect(PacketCodecs::optional), c -> "Crafting Requirements", RecipeDisplayEntry::craftingRequirements));

    public static final DataHighlightInstruction<RegistryByteBuf, RecipeBookAddS2CPacket.Entry> RBA_ENTRY = registry(RecipeBookAddS2CPacket.Entry.class, builder -> builder
            .compoundField(RECIPE_DISPLAY_ENTRY, RecipeBookAddS2CPacket.Entry::contents)
            .constantSizeOf(BYTE));

    public static final EnumMap<PlayerListS2CPacket.Action, DataHighlightInstruction<RegistryByteBuf, PlayerListS2CPacket.Entry>> PLAYER_LIST_ACTIONS = Util.make(Maps.newEnumMap(PlayerListS2CPacket.Action.class), map -> {
        map.put(PlayerListS2CPacket.Action.ADD_PLAYER, make(builder -> builder
                .compoundField(STRING, s -> "Name: " + s, e -> e.profile().getName())
                .compoundField(PROPERTY_MAP, e -> e.profile().getProperties())));

        map.put(PlayerListS2CPacket.Action.INITIALIZE_CHAT, make(builder -> builder
                .compoundField(nullable(s -> "", PPS_SERIALIZED), PlayerListS2CPacket.Entry::chatSession)));

        map.put(PlayerListS2CPacket.Action.UPDATE_GAME_MODE, make(builder -> builder
                .field(VAR_INT.withDescription(prefixed("Game Mode ID")), e -> e.gameMode().getId())));

        map.put(PlayerListS2CPacket.Action.UPDATE_LISTED, make(builder -> builder
                .field(BOOL.withDescription(trueOfFalse("Listed")), PlayerListS2CPacket.Entry::listed)));

        map.put(PlayerListS2CPacket.Action.UPDATE_LATENCY, make(builder -> builder
                .field(VAR_INT.withDescription(prefixed("Latency")), PlayerListS2CPacket.Entry::latency)));

        map.put(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, make(builder -> builder
                .compoundField(nullable(s -> "", TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC), s -> "Display Name", PlayerListS2CPacket.Entry::displayName)));

        map.put(PlayerListS2CPacket.Action.UPDATE_LIST_ORDER, make(builder -> builder
                .field(VAR_INT.withDescription(prefixed("List Order")), PlayerListS2CPacket.Entry::listOrder)));

        map.put(PlayerListS2CPacket.Action.UPDATE_HAT, make(builder -> builder
                .field(BOOL.withDescription(trueOfFalse("Show Hat")), PlayerListS2CPacket.Entry::showHat)));
    });

    public static final DataHighlightInstruction<PacketByteBuf, ArgumentSerializer.ArgumentTypeProperties> ARGUMENT_TYPE_PROPERTIES = packet(ArgumentSerializer.ArgumentTypeProperties.class, builder -> builder
            .field(VAR_INT, p -> Registries.COMMAND_ARGUMENT_TYPE.getRawId(p.getSerializer()))
            .packetEncoder((buf, value) -> value.getSerializer().writePacket(value, buf), Function.identity()));

    public static final DataHighlightInstruction<PacketByteBuf, CommandTreeS2CPacket.SuggestableNode> SUGGESTABLE_NODE = packet(CommandTreeS2CPacket.SuggestableNode.class, builder -> builder
            .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                if (value instanceof CommandTreeS2CPacket.LiteralNode node) {
                    return new RecursiveInstruction<>(STRING, v -> "", Function.identity()).write(curWriterIndex, receivedByteBuf, buf, node.literal);
                }

                if (value instanceof CommandTreeS2CPacket.ArgumentNode node) {
                    return DataHighlightInstructionBuilder.<PacketByteBuf, CommandTreeS2CPacket.ArgumentNode>builder()
                            .compoundField(STRING, s -> "Name: " + s, n -> n.name)
                            .compoundField(ARGUMENT_TYPE_PROPERTIES, p -> "Properties", n -> n.properties)
                            .compoundField((curWriterIndex1, receivedByteBuf1, buf1, value1) -> {
                                if (value1 == null) {
                                    return Collections.singletonList(NO_HIGHLIGHT);
                                }

                                return IDENTIFIER.write(curWriterIndex1, receivedByteBuf1, buf1, value1);
                            }, id -> "ID: " + id, n -> n.id)
                            .build()
                            .write(curWriterIndex, receivedByteBuf, buf, node);
                }

                return BufInstruction.<PacketByteBuf, CommandTreeS2CPacket.SuggestableNode>writeAndGuess((byteBuf, o) -> o.write(byteBuf), v -> "").write(curWriterIndex, receivedByteBuf, buf, value);
            }, Function.identity()));

    public static final DataHighlightInstruction<PacketByteBuf, CommandTreeS2CPacket.CommandNodeData> COMMAND_NODE_DATA = packet(CommandTreeS2CPacket.CommandNodeData.class, builder -> builder
            .constantSizeOf(BYTE.withDescription(nil -> "Flags"))
            .compoundField(INT_ARRAY_WITH_LEN, data -> data.childNodeIndices)
            .field((curWriterIndex, receivedByteBuf, buf, value) -> {
                if ((value.flags & 8) == 0) {
                    return Collections.singletonList(NO_HIGHLIGHT);
                }

                return VAR_INT.withDescription(prefixed("Redirect Node Index")).write(curWriterIndex, receivedByteBuf, buf, value.redirectNodeIndex);
            }, Function.identity())
            .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                if (value == null) {
                    return Collections.singletonList(NO_HIGHLIGHT);
                }

                return SUGGESTABLE_NODE.write(curWriterIndex, receivedByteBuf, buf, value);
            }, n -> "Suggestable Node", n -> n.suggestableNode));

    public static final DataHighlightInstruction<RegistryByteBuf, ChunkData.BlockEntityData> CHUNK_BLOCK_ENTITY_DATA = registry(ChunkData.BlockEntityData.class, builder -> builder
            .field(BYTE.withDescription(prefixed("Local XZ")).xmap(Integer::byteValue), d -> d.localXz)
            .field(SHORT.withDescription(prefixed("y")).xmap(Integer::shortValue), d -> d.y)
            .packetCodec(PacketCodecs.registryValue(RegistryKeys.BLOCK_ENTITY_TYPE), t -> "Block Entity Type", d -> d.type)
            .packetEncoder((buf, value) -> buf.writeNbt(value), n -> "NBT", d -> d.nbt));

    public static final DataHighlightInstruction<RegistryByteBuf, ChunkData> CHUNK_DATA = registry(ChunkData.class, builder -> builder
            .packetEncoder((buf, value) -> buf.writeNbt(value), m -> "Height Map", ChunkData::getHeightmap)
            .field(VAR_INT.withDescription(prefixed("Sections Data Length")), d -> d.sectionsData.length)
            .field(BYTE_ARRAY, p -> p.sectionsData)
            .listWithSize(CHUNK_BLOCK_ENTITY_DATA, p -> p.blockEntities));

    public static final DataHighlightInstruction<PacketByteBuf, LastSeenMessageList.Indexed> LSM_INDEXED = packet(LastSeenMessageList.Indexed.class, builder -> builder
            .listWithSize(MSD_INDEXED, LastSeenMessageList.Indexed::buf));

    public static final DataHighlightInstruction<PacketByteBuf, MessageBody.Serialized> BODY_SERIALIZED = packet(MessageBody.Serialized.class, builder -> builder
            .compoundField(STRING, s -> "Content: " + s, MessageBody.Serialized::content)
            .compoundField(INSTANT, i -> "Timestamp: " + i, MessageBody.Serialized::timestamp)
            .field(LONG.withDescription(prefixed("Salt")), MessageBody.Serialized::salt)
            .compoundField(LSM_INDEXED, i -> "Last Seen", MessageBody.Serialized::lastSeen));

    public static final DataHighlightInstruction<RegistryByteBuf, MessageType.Parameters> MESSAGE_TYPE_PARAMETERS = registry(MessageType.Parameters.class, builder -> builder
            .packetCodec(MessageType.ENTRY_PACKET_CODEC, t -> "Message Type: " + t.getIdAsString(), MessageType.Parameters::type)
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, n -> "Name", MessageType.Parameters::name)
            .compoundField(optional(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC), n -> "Target Name", MessageType.Parameters::targetName));

    public static final DataHighlightInstruction<RegistryByteBuf, BossBarS2CPacket.AddAction> ADD_ACTION = registry(BossBarS2CPacket.AddAction.class, builder -> builder
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, t -> "Name", a -> a.name)
            .field(FLOAT.withDescription(prefixed("Percent")), a -> a.percent)
            .field(VAR_INT.withDescription(o -> "Color: " + BossBar.Color.values()[o]).xmap(Enum::ordinal), a -> a.color)
            .field(VAR_INT.withDescription(o -> "Style: " + BossBar.Style.values()[o]).xmap(Enum::ordinal), a -> a.style)
            .field(BYTE.withDescription(b -> {
                boolean darken = (b & 1) > 0;
                boolean dragonMusic = (b & 2) > 0;
                boolean thicken = (b & 4) > 0;
                return "Darken Sky: " + (darken ? "true" : "false") + "\n" +
                        "Dragon Music: " + (dragonMusic ? "true" : "false") + "\n" +
                        "Thicken Fog: " + (thicken ? "true" : "false");
            }).xmap(Integer::byteValue), a -> BossBarS2CPacket.maskProperties(a.darkenSky, a.dragonMusic, a.thickenFog)));

    public static final DataHighlightInstruction<RegistryByteBuf, BossBarS2CPacket.UpdateProgressAction> UPDATE_PROGRESS_ACTION = registry(BossBarS2CPacket.UpdateProgressAction.class, builder -> builder
            .field(FLOAT.withDescription(prefixed("Progress")), BossBarS2CPacket.UpdateProgressAction::progress));

    public static final DataHighlightInstruction<RegistryByteBuf, BossBarS2CPacket.UpdateNameAction> UPDATE_NAME_ACTION = registry(BossBarS2CPacket.UpdateNameAction.class, builder -> builder
            .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, n -> "Name", BossBarS2CPacket.UpdateNameAction::name));

    public static final DataHighlightInstruction<RegistryByteBuf, BossBarS2CPacket.UpdateStyleAction> UPDATE_STYLE_ACTION = registry(BossBarS2CPacket.UpdateStyleAction.class, builder -> builder
            .field(VAR_INT.withDescription(o -> "Color: " + BossBar.Color.values()[o]).xmap(Enum::ordinal), a -> a.color)
            .field(VAR_INT.withDescription(o -> "Style: " + BossBar.Style.values()[o]).xmap(Enum::ordinal), a -> a.style));

    public static final DataHighlightInstruction<RegistryByteBuf, BossBarS2CPacket.UpdatePropertiesAction> UPDATE_PROPERTIES_ACTION = registry(BossBarS2CPacket.UpdatePropertiesAction.class, builder -> builder
            .field(BYTE.withDescription(b -> {
                boolean darken = (b & 1) > 0;
                boolean dragonMusic = (b & 2) > 0;
                boolean thicken = (b & 4) > 0;
                return "Darken Sky: " + (darken ? "true" : "false") + "\n" +
                        "Dragon Music: " + (dragonMusic ? "true" : "false") + "\n" +
                        "Thicken Fog: " + (thicken ? "true" : "false");
            }).xmap(Integer::byteValue), a -> BossBarS2CPacket.maskProperties(a.darkenSky, a.dragonMusic, a.thickenFog)));

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

        // CUSTOM
        registerCommonPayloads();
        registerC2SPayloads();
        registerS2CPayloads();

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
                .constantSizeOf(UUID)
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
                .compoundField(optional(UUID.noDesc()), ResourcePackRemoveS2CPacket::id));
        packet(ResourcePackSendS2CPacket.class, builder -> builder
                .field(UUID, ResourcePackSendS2CPacket::id)
                .compoundField(STRING, ResourcePackSendS2CPacket::url)
                .compoundField(STRING, ResourcePackSendS2CPacket::hash)
                .field(BOOL, ResourcePackSendS2CPacket::required)
                .compoundField(optional(TextCodecs.PACKET_CODEC), ResourcePackSendS2CPacket::prompt));
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
                .constantSizeOf(BOOL)
                .constantSizeOf(BOOL));
        packet(BookUpdateC2SPacket.class, builder -> builder
                .field(VAR_INT, BookUpdateC2SPacket::slot)
                .listWithSize(STRING, BookUpdateC2SPacket::pages)
                .compoundField(optional(STRING), BookUpdateC2SPacket::title));
        packet(BundleItemSelectedC2SPacket.class, builder -> builder
                .field(VAR_INT, BundleItemSelectedC2SPacket::slotId)
                .field(VAR_INT, BundleItemSelectedC2SPacket::selectedItemIndex));
        packet(ButtonClickC2SPacket.class, builder -> builder
                .field(VAR_INT, ButtonClickC2SPacket::syncId)
                .field(VAR_INT, ButtonClickC2SPacket::buttonId));
        packet(ChatCommandSignedC2SPacket.class, builder -> builder
                .compoundField(STRING, ChatCommandSignedC2SPacket::command)
                .field(INSTANT, ChatCommandSignedC2SPacket::timestamp)
                .constantSizeOf(LONG)
                .compoundField(ARGUMENT_SIGNATURE_DATA_MAP, ChatCommandSignedC2SPacket::argumentSignatures)
                .compoundField(LSM_ACK, ChatCommandSignedC2SPacket::lastSeenMessages));
        packet(ChatMessageC2SPacket.class, builder -> builder
                .compoundField(STRING, ChatMessageC2SPacket::chatMessage)
                .field(INSTANT, ChatMessageC2SPacket::timestamp)
                .constantSizeOf(LONG)
                .compoundField(nullable(d -> "", MESSAGE_SIGNATURE_DATA), ChatMessageC2SPacket::signature)
                .compoundField(LSM_ACK, ChatMessageC2SPacket::acknowledgment));
        registry(ClickSlotC2SPacket.class, builder -> builder
                .field(VAR_INT, ClickSlotC2SPacket::getSyncId)
                .field(VAR_INT, ClickSlotC2SPacket::getRevision)
                .constantSizeOf(SHORT)
                .constantSizeOf(BYTE)
                .field(VAR_INT.withDescription(o -> "Slot Action Type: " + SlotActionType.values()[o]), p -> p.getActionType().ordinal())
                .indexed(6).mapWithSize(
                        SHORT.noDesc().xmap(Integer::shortValue), ITEM_STACK,
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
                .constantSizeOf(SHORT)
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
                .constantSizeOf(BOOL));
        packet(PlayerInteractItemC2SPacket.class, builder -> builder
                .field(VAR_INT, p -> p.getHand().ordinal())
                .field(VAR_INT, PlayerInteractItemC2SPacket::getSequence)
                .field(FLOAT, PlayerInteractItemC2SPacket::getYaw)
                .field(FLOAT, PlayerInteractItemC2SPacket::getPitch));
        packet(PlayerMoveC2SPacket.Full.class, builder -> builder
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());
        packet(PlayerMoveC2SPacket.PositionAndOnGround.class, builder -> builder
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .notBeWritten()
                .notBeWritten()
                .constantSizeOf(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());
        packet(PlayerMoveC2SPacket.LookAndOnGround.class, builder -> builder
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());
        packet(PlayerMoveC2SPacket.OnGroundOnly.class, builder -> builder
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .constantSizeOf(BYTE)
                .sameAs(5)
                .notBeWritten()
                .notBeWritten());
        packet(PlayerSessionC2SPacket.class, builder -> builder
                .compoundField(PPS_SERIALIZED, PlayerSessionC2SPacket::chatSession));
        packet(QueryBlockNbtC2SPacket.class, builder -> builder
                .field(VAR_INT, QueryBlockNbtC2SPacket::getTransactionId)
                .compoundField(BLOCK_POS, QueryBlockNbtC2SPacket::getPos));
        packet(QueryEntityNbtC2SPacket.class, builder -> builder
                .field(VAR_INT, QueryEntityNbtC2SPacket::getTransactionId)
                .field(VAR_INT, QueryEntityNbtC2SPacket::getEntityId));
        packet(RecipeCategoryOptionsC2SPacket.class, builder -> builder
                .field(VAR_INT.withDescription(o -> "Recipe Book Type: " + RecipeBookType.values()[o]).xmap(Enum::ordinal), RecipeCategoryOptionsC2SPacket::getCategory)
                .constantSizeOf(BOOL)
                .constantSizeOf(BOOL));
        packet(RequestCommandCompletionsC2SPacket.class, builder -> builder
                .field(VAR_INT, RequestCommandCompletionsC2SPacket::getCompletionId)
                .compoundField(STRING, RequestCommandCompletionsC2SPacket::getPartialCommand));
        packet(SlotChangedStateC2SPacket.class, builder -> builder
                .field(VAR_INT, SlotChangedStateC2SPacket::slotId)
                .field(VAR_INT, SlotChangedStateC2SPacket::screenHandlerId)
                .constantSizeOf(BOOL));
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
                .indexed(2).constantSizeOf(BYTE)
                .indexed(3).sameAs(3)
                .indexed(4).sameAs(3));
        packet(UpdateCommandBlockMinecartC2SPacket.class, builder -> builder
                .field(VAR_INT, p -> p.entityId)
                .compoundField(STRING, UpdateCommandBlockMinecartC2SPacket::getCommand)
                .constantSizeOf(BOOL));
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
                .indexed(2).constantSizeOf(BOOL)
                .indexed(1).list(STRING, c -> "", p -> Lists.newArrayList(p.getText())));
        packet(UpdateStructureBlockC2SPacket.class, builder -> builder
                .compoundField(BLOCK_POS, UpdateStructureBlockC2SPacket::getPos)
                .field(VAR_INT.xmap(Enum::ordinal), UpdateStructureBlockC2SPacket::getAction)
                .field(VAR_INT.xmap(Enum::ordinal), UpdateStructureBlockC2SPacket::getMode)
                .compoundField(STRING, UpdateStructureBlockC2SPacket::getTemplateName)
                .compoundField(DataHighlightInstructionBuilder.<PacketByteBuf, BlockPos>builder()
                        .constantSizeOf(BYTE.withDescription(nil -> "x"))
                        .constantSizeOf(BYTE.withDescription(nil -> "y"))
                        .constantSizeOf(BYTE.withDescription(nil -> "z"))
                        .build(), UpdateStructureBlockC2SPacket::getOffset)
                .compoundField(DataHighlightInstructionBuilder.<PacketByteBuf, Vec3i>builder()
                        .constantSizeOf(BYTE.withDescription(nil -> "x"))
                        .constantSizeOf(BYTE.withDescription(nil -> "y"))
                        .constantSizeOf(BYTE.withDescription(nil -> "z"))
                        .build(), UpdateStructureBlockC2SPacket::getSize)
                .field(VAR_INT.xmap(Enum::ordinal), UpdateStructureBlockC2SPacket::getMirror)
                .field(VAR_INT.xmap(Enum::ordinal), UpdateStructureBlockC2SPacket::getRotation)
                .compoundField(STRING, UpdateStructureBlockC2SPacket::getMetadata)
                .indexed(12).constantSizeOf(FLOAT)
                .indexed(13).field(VAR_LONG, UpdateStructureBlockC2SPacket::getSeed)
                .indexed(9).constantSizeOf(BYTE)
                .indexed(10).sameAs(11)
                .indexed(11).sameAs(11));
        packet(VehicleMoveC2SPacket.class, builder -> builder
                .compoundField(VEC3D, VehicleMoveC2SPacket::position)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(BOOL));
    }

    private static void registerPlayS2CPackets() {
        registry(AdvancementUpdateS2CPacket.class, builder -> builder
                .constantSizeOf(BOOL)
                .listWithSize(ADVANCEMENT_ENTRY, AdvancementUpdateS2CPacket::getAdvancementsToEarn)
                .listWithSize(IDENTIFIER, AdvancementUpdateS2CPacket::getAdvancementIdsToRemove)
                .mapWithSize(IDENTIFIER, ADVANCEMENT_PROGRESS, m -> "", Either.right(buf -> {
                    return buf.readMap(Maps::newLinkedHashMapWithExpectedSize, PacketByteBuf::readIdentifier, AdvancementProgress::fromPacket);
                })));
        packet(BlockBreakingProgressS2CPacket.class, builder -> builder
                .field(VAR_INT, BlockBreakingProgressS2CPacket::getEntityId)
                .compoundField(BLOCK_POS, BlockBreakingProgressS2CPacket::getPos)
                .constantSizeOf(BYTE));
        registry(BlockEntityUpdateS2CPacket.class, builder -> builder
                .compoundField(BLOCK_POS, BlockEntityUpdateS2CPacket::getPos)
                .packetCodec(PacketCodecs.registryValue(RegistryKeys.BLOCK_ENTITY_TYPE), BlockEntityUpdateS2CPacket::getBlockEntityType)
                .packetCodec(PacketCodecs.UNLIMITED_NBT_COMPOUND, BlockEntityUpdateS2CPacket::getNbt));
        registry(BlockEventS2CPacket.class, builder -> builder
                .compoundField(BLOCK_POS, BlockEventS2CPacket::getPos)
                .constantSizeOf(BYTE)
                .constantSizeOf(BYTE)
                .packetCodec(PacketCodecs.registryValue(RegistryKeys.BLOCK), Block::toString, BlockEventS2CPacket::getBlock));
        registry(BlockUpdateS2CPacket.class, builder -> builder
                .compoundField(BLOCK_POS, BlockUpdateS2CPacket::getPos)
                .packetCodec(PacketCodecs.entryOf(Block.STATE_IDS), State::toString, BlockUpdateS2CPacket::getState));

        registry(BossBarS2CPacket.class, builder -> builder
                .field(UUID, p -> p.uuid)
                .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    List<Highlight<?>> highlights = Lists.newArrayList();
                    highlights.addAll(VAR_INT.withDescription(o -> "Type: " + BossBarS2CPacket.Type.values()[o]).<Enum<BossBarS2CPacket.Type>>xmap(Enum::ordinal).write(curWriterIndex, receivedByteBuf, buf, value.action.getType()));

                    curWriterIndex += getWrittenByteLen(highlights);

                    List<Highlight<?>> list = switch (value.action) {
                        case BossBarS2CPacket.AddAction add ->
                                ADD_ACTION.write(curWriterIndex, receivedByteBuf, buf, add);
                        case BossBarS2CPacket.UpdateProgressAction action ->
                                UPDATE_PROGRESS_ACTION.write(curWriterIndex, receivedByteBuf, buf, action);
                        case BossBarS2CPacket.UpdateNameAction action ->
                                UPDATE_NAME_ACTION.write(curWriterIndex, receivedByteBuf, buf, action);
                        case BossBarS2CPacket.UpdateStyleAction action ->
                                UPDATE_STYLE_ACTION.write(curWriterIndex, receivedByteBuf, buf, action);
                        case BossBarS2CPacket.UpdatePropertiesAction action ->
                                UPDATE_PROPERTIES_ACTION.write(curWriterIndex, receivedByteBuf, buf, action);
                        default -> List.of();
                    };

                    if (!list.isEmpty()) {
                        highlights.addAll(list);
                    }

                    return highlights;
                }, Function.identity()));

        registry(ChatMessageS2CPacket.class, builder -> builder
                .field(UUID, ChatMessageS2CPacket::sender)
                .field(VAR_INT, ChatMessageS2CPacket::index)
                .compoundField(nullable(s -> "", MESSAGE_SIGNATURE_DATA), ChatMessageS2CPacket::signature)
                .compoundField(BODY_SERIALIZED, ChatMessageS2CPacket::body)
                .compoundField(nullable(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC), ChatMessageS2CPacket::unsignedContent)
                .packetEncoder(FilterMask::writeMask, ChatMessageS2CPacket::filterMask)
                .compoundField(MESSAGE_TYPE_PARAMETERS, ChatMessageS2CPacket::serializedParameters));

        packet(ChatSuggestionsS2CPacket.class, builder -> builder
                .field(VAR_INT.xmap(Enum::ordinal), ChatSuggestionsS2CPacket::action)
                .listWithSize(STRING, ChatSuggestionsS2CPacket::entries));

        packet(ChunkBiomeDataS2CPacket.class, builder -> builder
                .listWithSize(CBD_SERIALIZED, ChunkBiomeDataS2CPacket::chunkBiomeData));

        registry(ChunkDataS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .constantSizeOf(INT)
                .compoundField(CHUNK_DATA, ChunkDataS2CPacket::getChunkData)
                .compoundField(LIGHT_DATA, ChunkDataS2CPacket::getLightData));

        packet(ChunkDeltaUpdateS2CPacket.class, builder -> builder
                .field(CHUNK_SECTION_POS, p -> p.sectionPos)
                .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    List<Highlight<?>> highlights = Lists.newArrayList();
                    highlights.addAll(VAR_INT.withDescription(prefixed("Data Length")).write(curWriterIndex, receivedByteBuf, buf, value.positions.length));

                    curWriterIndex += getWrittenByteLen(highlights);

                    for (int i = 0; i < value.positions.length; ++i) {
                        var written = VAR_LONG.withDescription(l -> "Data").write(curWriterIndex, receivedByteBuf, buf, (long) Block.getRawIdFromState(value.blockStates[i]) << 12 | (long) value.positions[i]);
                        curWriterIndex += getWrittenByteLen(written);
                        highlights.addAll(written);
                    }

                    return highlights;
                }, s -> "Section Update Data", Function.identity())
                .sameAs(1));

        packet(ChunkRenderDistanceCenterS2CPacket.class, builder -> builder
                .field(VAR_INT, ChunkRenderDistanceCenterS2CPacket::getChunkX)
                .field(VAR_INT, ChunkRenderDistanceCenterS2CPacket::getChunkZ));


        registry(CommandSuggestionsS2CPacket.class, builder -> builder
                .field(VAR_INT, CommandSuggestionsS2CPacket::id)
                .field(VAR_INT, CommandSuggestionsS2CPacket::start)
                .field(VAR_INT, CommandSuggestionsS2CPacket::length)
                .listWithSize(COMMAND_SUGGESTION, CommandSuggestionsS2CPacket::suggestions));

        packet(CommandTreeS2CPacket.class, builder -> builder
                .indexed(1).listWithSize(COMMAND_NODE_DATA, p -> p.nodes)
                .indexed(0).field(VAR_INT, p -> p.rootSize));

        registry(CooldownUpdateS2CPacket.class, builder -> builder
                .compoundField(IDENTIFIER, CooldownUpdateS2CPacket::cooldownGroup)
                .field(VAR_INT, CooldownUpdateS2CPacket::cooldown));

        registry(CraftFailedResponseS2CPacket.class, builder -> builder
                .field(VAR_INT, CraftFailedResponseS2CPacket::syncId)
                .packetCodec(RecipeDisplay.STREAM_CODEC, CraftFailedResponseS2CPacket::recipeDisplay));

        packet(DamageTiltS2CPacket.class, builder -> builder
                .field(VAR_INT, DamageTiltS2CPacket::id)
                .constantSizeOf(FLOAT));

        registry(DeathMessageS2CPacket.class, builder -> builder
                .field(VAR_INT, DeathMessageS2CPacket::playerId)
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, DeathMessageS2CPacket::message));

        packet(DebugSampleS2CPacket.class, builder -> builder
                .compoundField(LONG_ARRAY_WITH_LEN, DebugSampleS2CPacket::sample)
                .field(VAR_INT.xmap(Enum::ordinal), DebugSampleS2CPacket::debugSampleType));

        packet(DifficultyS2CPacket.class, builder -> builder
                .field(BYTE
                                .withDescription(id -> Difficulty.byId(id).getName())
                                .xmap(Integer::byteValue)
                                .xmap(Difficulty::getId),
                        DifficultyS2CPacket::getDifficulty)
                .constantSizeOf(BOOL));

        registry(EntitiesDestroyS2CPacket.class, builder -> builder
                .compoundField(INT_LIST, EntitiesDestroyS2CPacket::getEntityIds));

        packet(EntityAnimationS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityAnimationS2CPacket::getEntityId)
                .constantSizeOf(BYTE));

        packet(EntityAttachS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .constantSizeOf(INT));

        registry(EntityAttributesS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityAttributesS2CPacket::getEntityId)
                .listWithSize(ATTRIBUTE_ENTRY, EntityAttributesS2CPacket::getEntries));

        registry(EntityDamageS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityDamageS2CPacket::entityId)
                .packetCodec(DamageType.ENTRY_PACKET_CODEC, RegistryEntry::getIdAsString, EntityDamageS2CPacket::sourceType)
                .field(VAR_INT, p -> p.sourceCauseId() + 1)
                .field(VAR_INT, p -> p.sourceDirectId() + 1)
                .compoundField(optional(VEC3D), EntityDamageS2CPacket::sourcePosition));

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

        packet(EntityPassengersSetS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityPassengersSetS2CPacket::getEntityId)
                .compoundField(INT_ARRAY_WITH_LEN, EntityPassengersSetS2CPacket::getPassengerIds));

        packet(EntityPositionS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityPositionS2CPacket::entityId)
                .compoundField(PLAYER_POSITION, EntityPositionS2CPacket::change)
                .packetCodec(PositionFlag.PACKET_CODEC, EntityPositionS2CPacket::relatives)
                .constantSizeOf(BOOL));

        packet(EntityPositionSyncS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityPositionSyncS2CPacket::id)
                .compoundField(PLAYER_POSITION, EntityPositionSyncS2CPacket::values)
                .constantSizeOf(BOOL));

        packet(EntityS2CPacket.RotateAndMoveRelative.class, builder -> builder
                .field(VAR_INT, p -> p.id)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT)
                .constantSizeOf(BYTE)
                .constantSizeOf(BYTE)
                .constantSizeOf(BOOL)
                .notBeWritten()
                .notBeWritten());

        packet(EntityS2CPacket.MoveRelative.class, builder -> builder
                .field(VAR_INT, p -> p.id)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT)
                .notBeWritten()
                .notBeWritten()
                .constantSizeOf(BOOL)
                .notBeWritten()
                .notBeWritten());

        packet(EntityS2CPacket.Rotate.class, builder -> builder
                .field(VAR_INT, p -> p.id)
                .notBeWritten()
                .notBeWritten()
                .notBeWritten()
                .constantSizeOf(BYTE)
                .constantSizeOf(BYTE)
                .constantSizeOf(BOOL)
                .notBeWritten()
                .notBeWritten());

        packet(EntitySetHeadYawS2CPacket.class, builder -> builder
                .field(VAR_INT, p -> p.entityId)
                .constantSizeOf(BYTE));

        registry(EntitySpawnS2CPacket.class, builder -> builder
                .field(VAR_INT, EntitySpawnS2CPacket::getEntityId)
                .field(UUID, EntitySpawnS2CPacket::getUuid)
                .packetCodec(PacketCodecs.registryValue(RegistryKeys.ENTITY_TYPE), EntityType::toString, EntitySpawnS2CPacket::getEntityType)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .indexed(9).constantSizeOf(BYTE)
                .indexed(10).constantSizeOf(BYTE)
                .indexed(11).constantSizeOf(BYTE)
                .indexed(12).field(VAR_INT, EntitySpawnS2CPacket::getEntityData)
                .indexed(6).constantSizeOf(SHORT)
                .indexed(7).constantSizeOf(SHORT)
                .indexed(8).constantSizeOf(SHORT));

        registry(EntityStatusEffectS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityStatusEffectS2CPacket::getEntityId)
                .packetCodec(StatusEffect.ENTRY_PACKET_CODEC, RegistryEntry::getIdAsString, EntityStatusEffectS2CPacket::getEffectId)
                .field(VAR_INT, EntityStatusEffectS2CPacket::getAmplifier)
                .field(VAR_INT, EntityStatusEffectS2CPacket::getDuration)
                .constantSizeOf(BYTE));

        packet(EntityStatusS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .constantSizeOf(BYTE));

        registry(EntityTrackerUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityTrackerUpdateS2CPacket::id)
                .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    List<Highlight<?>> highlights = Lists.newArrayList();

                    highlights.addAll(DataHighlightInstructionBuilder.<RegistryByteBuf, List<DataTracker.SerializedEntry<?>>>builder()
                            .list(writeAndGuess((registryByteBuf, serializedEntry) -> serializedEntry.write(registryByteBuf), v -> ""), c -> "", Function.identity())
                            .build()
                            .write(curWriterIndex, receivedByteBuf, buf, value));

                    curWriterIndex += getWrittenByteLen(highlights);

                    highlights.addAll(BYTE.withDescription(nil -> "End").write(curWriterIndex, receivedByteBuf, buf, null));

                    return highlights;
                }, EntityTrackerUpdateS2CPacket::trackedValues));

        packet(EntityVelocityUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, EntityVelocityUpdateS2CPacket::getEntityId)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT));

        packet(ExperienceBarUpdateS2CPacket.class, builder -> builder
                .constantSizeOf(FLOAT)
                .indexed(2).field(VAR_INT, ExperienceBarUpdateS2CPacket::getExperience)
                .indexed(1).field(VAR_INT, ExperienceBarUpdateS2CPacket::getExperienceLevel));

        packet(ExperienceOrbSpawnS2CPacket.class, builder -> builder
                .field(VAR_INT, ExperienceOrbSpawnS2CPacket::getEntityId)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(SHORT));

        registry(ExplosionS2CPacket.class, builder -> builder
                .compoundField(VEC3D, ExplosionS2CPacket::center)
                .compoundField(optional(VEC3D), ExplosionS2CPacket::playerKnockback)
                .packetCodec(ParticleTypes.PACKET_CODEC, ExplosionS2CPacket::explosionParticle)
                .packetCodec(SoundEvent.ENTRY_PACKET_CODEC, RegistryEntry::getIdAsString, ExplosionS2CPacket::explosionSound));

        registry(GameJoinS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .constantSizeOf(BOOL)
                .listWithSize(REGISTRY_KEY, c -> "", Either.right(buf -> {
                    return buf.readCollection(Lists::newArrayListWithExpectedSize, (b) -> b.readRegistryKey(RegistryKeys.WORLD));
                }))
                .field(VAR_INT, GameJoinS2CPacket::maxPlayers)
                .field(VAR_INT, GameJoinS2CPacket::viewDistance)
                .field(VAR_INT, GameJoinS2CPacket::simulationDistance)
                .constantSizeOf(BOOL)
                .constantSizeOf(BOOL)
                .constantSizeOf(BOOL)
                .compoundField(COMMON_PLAYER_SPAWN_INFO, GameJoinS2CPacket::commonPlayerSpawnInfo)
                .constantSizeOf(BOOL));

        registry(GameMessageS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, GameMessageS2CPacket::content)
                .constantSizeOf(BOOL));

        packet(GameStateChangeS2CPacket.class, builder -> builder
                .constantSizeOf(BYTE)
                .constantSizeOf(FLOAT));

        packet(HealthUpdateS2CPacket.class, builder -> builder
                .constantSizeOf(FLOAT)
                .field(VAR_INT, HealthUpdateS2CPacket::getFood)
                .constantSizeOf(FLOAT));

        registry(InventoryS2CPacket.class, builder -> builder
                .field(VAR_INT, InventoryS2CPacket::getSyncId)
                .field(VAR_INT, InventoryS2CPacket::getRevision)
                .listWithSize(ITEM_STACK, InventoryS2CPacket::getContents)
                .compoundField(ITEM_STACK, InventoryS2CPacket::getCursorStack));

        packet(ItemPickupAnimationS2CPacket.class, builder -> builder
                .field(VAR_INT, ItemPickupAnimationS2CPacket::getEntityId)
                .field(VAR_INT, ItemPickupAnimationS2CPacket::getCollectorEntityId)
                .field(VAR_INT, ItemPickupAnimationS2CPacket::getStackAmount));

        packet(LightUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, LightUpdateS2CPacket::getChunkX)
                .field(VAR_INT, LightUpdateS2CPacket::getChunkZ)
                .compoundField(LIGHT_DATA, LightUpdateS2CPacket::getData));

        packet(LookAtS2CPacket.class, builder -> builder
                .indexed(4).field(VAR_INT.xmap(Enum::ordinal), LookAtS2CPacket::getSelfAnchor)
                .indexed(0).constantSizeOf(DOUBLE)
                .indexed(1).constantSizeOf(DOUBLE)
                .indexed(2).constantSizeOf(DOUBLE)
                .indexed(6).field(BOOL, p -> p.lookAtEntity, b -> b)
                .indexed(3).field(VAR_INT, p -> p.entityId)
                .indexed(5).field(VAR_INT.xmap(Enum::ordinal), p -> p.targetAnchor));

        packet(MoveMinecartAlongTrackS2CPacket.class, builder -> builder
                .field(VAR_INT, MoveMinecartAlongTrackS2CPacket::entityId)
                .listWithSize(MINECART_CONTROLLER_STEP, MoveMinecartAlongTrackS2CPacket::lerpSteps));

        packet(NbtQueryResponseS2CPacket.class, builder -> builder
                .field(VAR_INT, NbtQueryResponseS2CPacket::getTransactionId)
                .packetEncoder((buf, value) -> buf.writeNbt(value), NbtQueryResponseS2CPacket::getNbt));

        packet(OpenHorseScreenS2CPacket.class, builder -> builder
                .field(VAR_INT, OpenHorseScreenS2CPacket::getSyncId)
                .field(VAR_INT, OpenHorseScreenS2CPacket::getSlotColumnCount)
                .constantSizeOf(INT));

        registry(OpenScreenS2CPacket.class, builder -> builder
                .field(VAR_INT, OpenScreenS2CPacket::getSyncId)
                .packetCodec(PacketCodecs.registryValue(RegistryKeys.SCREEN_HANDLER), OpenScreenS2CPacket::getScreenHandlerType)
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, OpenScreenS2CPacket::getName));

        registry(ParticleS2CPacket.class, builder -> builder
                .indexed(8).constantSizeOf(BOOL)
                .indexed(9).constantSizeOf(BOOL)
                .indexed(0).constantSizeOf(DOUBLE)
                .indexed(1).constantSizeOf(DOUBLE)
                .indexed(2).constantSizeOf(DOUBLE)
                .indexed(3).constantSizeOf(FLOAT)
                .indexed(4).constantSizeOf(FLOAT)
                .indexed(5).constantSizeOf(FLOAT)
                .indexed(6).constantSizeOf(FLOAT)
                .indexed(7).constantSizeOf(INT)
                .indexed(10).packetCodec(ParticleTypes.PACKET_CODEC, ParticleS2CPacket::getParameters));

        packet(PlayerAbilitiesS2CPacket.class, builder -> builder
                .constantSizeOf(BYTE)
                .sameAs(0)
                .sameAs(0)
                .sameAs(0)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT));

        registry(PlayerListHeaderS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, PlayerListHeaderS2CPacket::header)
                .packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, PlayerListHeaderS2CPacket::footer));

        registry(PlayerListS2CPacket.class, builder -> builder
                .packetEncoder((buf, value) -> buf.writeEnumSet(value, PlayerListS2CPacket.Action.class), AbstractCollection::toString, PlayerListS2CPacket::getActions)
                .field((curWriterIndex, receivedByteBuf, buf, packet) -> {
                    var recur = DataHighlightInstructionBuilder.<RegistryByteBuf, List<PlayerListS2CPacket.Entry>>builder()
                            .listWithSize((curWriterIndex1, receivedByteBuf1, buf1, entry) -> {
                                List<Highlight<?>> highlights = Lists.newArrayList();
                                highlights.addAll(UUID.withDescription(id -> "Profile ID: " + id).write(curWriterIndex1, receivedByteBuf1, buf1, entry.profileId()));

                                curWriterIndex1 += getWrittenByteLen(highlights);

                                for (var action : packet.getActions()) {
                                    var writer = PLAYER_LIST_ACTIONS.get(action);
                                    var written = writer.write(curWriterIndex1, receivedByteBuf1, buf1, entry);
                                    highlights.addAll(written);

                                    curWriterIndex1 += getWrittenByteLen(written);
                                }

                                return highlights;
                            }, Function.identity())
                            .build();

                    return recur.write(curWriterIndex, receivedByteBuf, buf, packet.getEntries());
                }, Function.identity()));

        packet(PlayerPositionLookS2CPacket.class, builder -> builder
                .field(VAR_INT, PlayerPositionLookS2CPacket::teleportId)
                .compoundField(PLAYER_POSITION, PlayerPositionLookS2CPacket::change)
                .packetCodec(PositionFlag.PACKET_CODEC, PlayerPositionLookS2CPacket::relatives));

        packet(PlayerRemoveS2CPacket.class, builder -> builder
                .listWithSize(UUID, PlayerRemoveS2CPacket::profileIds));

        registry(PlayerRespawnS2CPacket.class, builder -> builder
                .compoundField(COMMON_PLAYER_SPAWN_INFO, PlayerRespawnS2CPacket::commonPlayerSpawnInfo)
                .constantSizeOf(BYTE));

        packet(PlayerRotationS2CPacket.class, builder -> builder
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT));

        packet(PlayerSpawnPositionS2CPacket.class, builder -> builder
                .compoundField(BLOCK_POS, PlayerSpawnPositionS2CPacket::getPos)
                .constantSizeOf(FLOAT));

        registry(PlaySoundFromEntityS2CPacket.class, builder -> builder
                .packetCodec(SoundEvent.ENTRY_PACKET_CODEC, PlaySoundFromEntityS2CPacket::getSound)
                .field(VAR_INT.xmap(Enum::ordinal), PlaySoundFromEntityS2CPacket::getCategory)
                .field(VAR_INT, PlaySoundFromEntityS2CPacket::getEntityId)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(LONG));

        registry(PlaySoundS2CPacket.class, builder -> builder
                .packetCodec(SoundEvent.ENTRY_PACKET_CODEC, e -> e.getIdAsString(), PlaySoundS2CPacket::getSound)
                .field(VAR_INT.withDescription(o -> "" + SoundCategory.values()[o]).xmap(Enum::ordinal), PlaySoundS2CPacket::getCategory)
                .constantSizeOf(INT)
                .constantSizeOf(INT)
                .constantSizeOf(INT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT)
                .constantSizeOf(LONG));

        packet(ProjectilePowerS2CPacket.class, builder -> builder
                .field(VAR_INT, ProjectilePowerS2CPacket::getEntityId)
                .constantSizeOf(DOUBLE));

        registry(RecipeBookAddS2CPacket.class, builder -> builder
                .listWithSize(RBA_ENTRY, RecipeBookAddS2CPacket::entries)
                .constantSizeOf(BOOL));

        register(RecipeBookRemoveS2CPacket.class, builder -> builder
                .listWithSize(NETWORK_RECIPE_ID, RecipeBookRemoveS2CPacket::recipes));

        registry(RemoveEntityStatusEffectS2CPacket.class, builder -> builder
                .field(VAR_INT, RemoveEntityStatusEffectS2CPacket::entityId)
                .packetCodec(StatusEffect.ENTRY_PACKET_CODEC, RemoveEntityStatusEffectS2CPacket::effect));

        packet(ScoreboardDisplayS2CPacket.class, builder -> builder
                .field(VAR_INT.withDescription(o -> ScoreboardDisplaySlot.FROM_ID.apply(o).name()).xmap(ScoreboardDisplaySlot::getId), ScoreboardDisplayS2CPacket::getSlot)
                .compoundField(STRING, ScoreboardDisplayS2CPacket::getName));

        registry(ScoreboardObjectiveUpdateS2CPacket.class, builder -> builder
                .compoundField(STRING, ScoreboardObjectiveUpdateS2CPacket::getName)
                .indexed(4).field(BYTE.xmap(Integer::byteValue), ScoreboardObjectiveUpdateS2CPacket::getMode, m -> m == 0 || m == 2)
                .indexed(1).packetCodec(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC, ScoreboardObjectiveUpdateS2CPacket::getDisplayName)
                .indexed(2).field(VAR_INT.xmap(Enum::ordinal), ScoreboardObjectiveUpdateS2CPacket::getType)
                .indexed(3).compoundField(optional(NumberFormatTypes.PACKET_CODEC), ScoreboardObjectiveUpdateS2CPacket::getNumberFormat));

        packet(ScoreboardScoreResetS2CPacket.class, builder -> builder
                .compoundField(STRING, ScoreboardScoreResetS2CPacket::scoreHolderName)
                .compoundField(nullable(t -> "", STRING), ScoreboardScoreResetS2CPacket::objectiveName));

        registry(ScoreboardScoreUpdateS2CPacket.class, builder -> builder
                .compoundField(STRING, ScoreboardScoreUpdateS2CPacket::scoreHolderName)
                .compoundField(STRING, ScoreboardScoreUpdateS2CPacket::objectiveName)
                .field(VAR_INT, ScoreboardScoreUpdateS2CPacket::score)
                .compoundField(optional(TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC), ScoreboardScoreUpdateS2CPacket::display)
                .compoundField(optional(NumberFormatTypes.PACKET_CODEC), ScoreboardScoreUpdateS2CPacket::numberFormat));

        packet(ScreenHandlerPropertyUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, ScreenHandlerPropertyUpdateS2CPacket::getSyncId)
                .constantSizeOf(SHORT)
                .constantSizeOf(SHORT));

        registry(ScreenHandlerSlotUpdateS2CPacket.class, builder -> builder
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getSyncId)
                .field(VAR_INT, ScreenHandlerSlotUpdateS2CPacket::getRevision)
                .field(SHORT, p -> (short) p.getSlot())
                .compoundField(ITEM_STACK, ScreenHandlerSlotUpdateS2CPacket::getStack));

        register(ServerMetadataS2CPacket.class, builder -> builder
                .packetCodec(TextCodecs.PACKET_CODEC, ServerMetadataS2CPacket::description)
                .compoundField(optional(BYTE_ARRAY_WITH_LEN), ServerMetadataS2CPacket::favicon));

        registry(SetCursorItemS2CPacket.class, builder -> builder
                .compoundField(ITEM_STACK, SetCursorItemS2CPacket::contents));

        registry(SetPlayerInventoryS2CPacket.class, builder -> builder
                .field(VAR_INT, SetPlayerInventoryS2CPacket::slot)
                .compoundField(ITEM_STACK, SetPlayerInventoryS2CPacket::contents));

        registry(SetTradeOffersS2CPacket.class, builder -> builder
                .field(VAR_INT, SetTradeOffersS2CPacket::getSyncId)
                .listWithSize(TRADE_OFFER, SetTradeOffersS2CPacket::getOffers)
                .field(VAR_INT, SetTradeOffersS2CPacket::getLevelProgress)
                .field(VAR_INT, SetTradeOffersS2CPacket::getExperience)
                .constantSizeOf(BOOL)
                .constantSizeOf(BOOL));

        packet(SignEditorOpenS2CPacket.class, builder -> builder
                .compoundField(BLOCK_POS, SignEditorOpenS2CPacket::getPos)
                .constantSizeOf(BOOL));

        registry(StatisticsS2CPacket.class, builder -> builder
                .mapWithSize(writeAndGuess(Stat.PACKET_CODEC::encode, Stat::toString), VAR_INT.noDesc(), m -> "", Either.right(buf -> {
                    return PacketCodecs.map(Object2IntLinkedOpenHashMap::new, Stat.PACKET_CODEC, PacketCodecs.VAR_INT).decode(buf);
                })));

        packet(StopSoundS2CPacket.class, builder -> builder
                .indexed(1).compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    List<Highlight<?>> highlights = Lists.newArrayList();
                    highlights.addAll(BYTE.noDesc().write(curWriterIndex, receivedByteBuf, buf, null));

                    curWriterIndex += getWrittenByteLen(highlights);

                    if (value.getCategory() == null) {
                        return highlights;
                    }

                    highlights.addAll(VAR_INT.noDesc().write(curWriterIndex, receivedByteBuf, buf, value.getCategory().ordinal()));

                    return highlights;
                }, Function.identity())
                .indexed(0).compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    if (value == null) {
                        return Collections.singletonList(NO_HIGHLIGHT);
                    }

                    return IDENTIFIER.write(curWriterIndex, receivedByteBuf, buf, value);
                }, StopSoundS2CPacket::getSoundId));

        registry(SynchronizeRecipesS2CPacket.class, builder -> builder
                .packetCodec(PacketCodecs.map(Maps::newHashMapWithExpectedSize, RegistryKey.createPacketCodec(RecipePropertySet.REGISTRY), RecipePropertySet.PACKET_CODEC), SynchronizeRecipesS2CPacket::itemSets)
                .packetCodec(CuttingRecipeDisplay.Grouping.codec(), SynchronizeRecipesS2CPacket::stonecutterRecipes));

        registry(TeamS2CPacket.class, builder -> builder
                .indexed(1).compoundField(STRING, TeamS2CPacket::getTeamName)
                .indexed(0).constantSizeOf(BYTE)
                .indexed(3).compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    if (value.packetType != 0 && value.packetType != 2) {
                        return Collections.singletonList(NO_HIGHLIGHT);
                    }

                    return TEAM.write(curWriterIndex, receivedByteBuf, buf, value.getTeam().orElseThrow());
                }, Function.identity())
                .indexed(2).compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    if (value.packetType != 0 && value.packetType != 3 && value.packetType != 4) {
                        return Collections.singletonList(NO_HIGHLIGHT);
                    }

                    return DataHighlightInstructionBuilder.<ByteBuf, TeamS2CPacket>builder()
                            .listWithSize(STRING, TeamS2CPacket::getPlayerNames)
                            .build()
                            .write(curWriterIndex, receivedByteBuf, buf, value);
                }, Function.identity()));

        packet(TitleFadeS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .constantSizeOf(INT)
                .constantSizeOf(INT));

        packet(UnloadChunkS2CPacket.class, builder -> builder
                .compoundField(CHUNK_POS, UnloadChunkS2CPacket::pos));

        packet(UpdateTickRateS2CPacket.class, builder -> builder
                .constantSizeOf(FLOAT)
                .constantSizeOf(BOOL));

        packet(VehicleMoveS2CPacket.class, builder -> builder
                .compoundField(VEC3D, VehicleMoveS2CPacket::position)
                .constantSizeOf(FLOAT)
                .constantSizeOf(FLOAT));

        packet(WorldBorderCenterChangedS2CPacket.class, builder -> builder
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE));

        packet(WorldBorderInitializeS2CPacket.class, builder -> builder
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .field(VAR_LONG, WorldBorderInitializeS2CPacket::getSizeLerpTime)
                .field(VAR_INT, WorldBorderInitializeS2CPacket::getMaxRadius)
                .field(VAR_INT, WorldBorderInitializeS2CPacket::getWarningBlocks)
                .field(VAR_INT, WorldBorderInitializeS2CPacket::getWarningTime));

        packet(WorldBorderInterpolateSizeS2CPacket.class, builder -> builder
                .constantSizeOf(DOUBLE)
                .constantSizeOf(DOUBLE)
                .field(VAR_LONG, WorldBorderInterpolateSizeS2CPacket::getSizeLerpTime));

        packet(WorldEventS2CPacket.class, builder -> builder
                .constantSizeOf(INT)
                .compoundField(BLOCK_POS, WorldEventS2CPacket::getPos)
                .constantSizeOf(INT)
                .constantSizeOf(BOOL));

        registry(WorldTimeUpdateS2CPacket.class, builder -> builder
                .constantSizeOf(LONG)
                .constantSizeOf(LONG)
                .constantSizeOf(BOOL));
    }

    private static void registerCommonPayloads() {
        packet(BrandCustomPayload.class, builder -> builder
                .compoundField(STRING, s -> "Brand: " + s, BrandCustomPayload::brand));

        packet(RegistrationPayload.class, builder -> builder
                .compoundField((curWriterIndex, receivedByteBuf, buf, value) -> {
                    List<Highlight<?>> highlights = org.apache.commons.compress.utils.Lists.newArrayList();

                    int i = 0;
                    boolean first = true;
                    for (var e : value) {
                        List<Highlight<?>> sub = Lists.newArrayList();
                        int start = curWriterIndex;

                        if (first) {
                            first = false;
                        } else {
                            var hs = BYTE.withDescription(b -> "Separator").write(curWriterIndex, receivedByteBuf, buf, (byte) 0);
                            curWriterIndex += getWrittenByteLen(hs);

                            sub.addAll(hs);
                        }

                        if (!e.getPath().isEmpty()) {
                            var hs = BYTE_ARRAY.withDescription(b -> e.toString()).write(curWriterIndex, receivedByteBuf, buf, e.toString().getBytes(StandardCharsets.US_ASCII));
                            curWriterIndex += getWrittenByteLen(hs);
                            sub.addAll(hs);
                        }

                        var indexed = new Highlight<>(new Highlight.HighlightRange(start, curWriterIndex - 1), e, "Index: " + i, sub);
                        highlights.add(indexed);
                        i++;
                    }

                    return highlights;
                }, RegistrationPayload::channels));

        packet(CommonRegisterPayload.class, builder -> builder
                .field(VAR_INT, CommonRegisterPayload::version)
                .compoundField(STRING, CommonRegisterPayload::phase)
                .listWithSize(IDENTIFIER, CommonRegisterPayload::channels));

        packet(CommonVersionPayload.class, builder -> builder
                .compoundField(INT_ARRAY_WITH_LEN, CommonVersionPayload::versions));
    }

    private static void registerC2SPayloads() {

    }

    private static void registerS2CPayloads() {

    }

    public static <B extends PacketByteBuf, T> DataHighlightInstruction<B, T> packet(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        return register(clazz, consumer);
    }

    public static <B extends RegistryByteBuf, T> DataHighlightInstruction<B, T> registry(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        return register(clazz, true, consumer);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        return register(clazz, false, consumer);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> register(Class<T> clazz, boolean registry, Consumer<DataHighlightInstructionBuilder<B, T>> consumer) {
        var builder = DataHighlightInstructionBuilder.<B, T>builder(registry);
        consumer.accept(builder);
        var built = builder.build();
        HIGHLIGHTERS.put(clazz, built);
        return built;
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(PacketEncoder<B, T> encoder) {
        return optional(t -> "", encoder);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(Function<T, String> descriptor, PacketEncoder<B, T> encoder) {
        return make(b -> b
                .field(BOOL.withDescription(bool -> bool ? "Present" : "Empty"), Optional::isPresent, Boolean::booleanValue)
                .packetEncoder(encoder, descriptor, Optional::get));
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(BufInstruction<B, T> sub) {
        return optional(t -> "", sub);
    }

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, Optional<T>> optional(Function<T, String> descriptor, BufInstruction<B, T> sub) {
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

    public static <B extends ByteBuf, T> DataHighlightInstruction<B, T> nullable(Function<T, String> descriptor, BufInstruction<B, T> sub) {
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
