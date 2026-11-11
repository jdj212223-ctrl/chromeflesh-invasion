package com.jdj125.invasion;

import com.chromeflesh.implant.Implant;
import com.chromeflesh.implant.Implants;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Chromeflesh: Invasion. On day 20 (configurable) every TV that's on switches to an emergency alert: a rogue android
 * has breached. It infects the players it hits; at 100% infection a player is corrupted: they lose control (they just
 * crouch there, glitching), and anyone who stays near them gets infected too. Every night after the breach more
 * androids come. A Purge Shot cures it, a Chromeflesh Firewall implant protects you, and a corrupted player's own
 * system fights back after a while (15 minutes by default), so nobody is locked out of their game forever.
 */
@Mod(Invasion.MODID)
public final class Invasion {
    public static final String MODID = "chromeflesh_invasion";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    public static final Supplier<EntityType<Android>> ANDROID = ENTITIES.register("android",
            () -> EntityType.Builder.of(Android::new, MobCategory.MONSTER).sized(0.6f, 1.95f).clientTrackingRange(10).build("android"));
    public static final DeferredBlock<Block> TV = BLOCKS.register("tv", () -> new Tv(BlockBehaviour.Properties.of()
            .strength(1.5f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(Tv.SCREEN) > 0 ? 6 : 0)));
    public static final DeferredBlock<Block> CORRUPTED_SOIL = BLOCKS.registerSimpleBlock("corrupted_soil",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6f).sound(SoundType.GRAVEL).lightLevel(s -> 3));
    public static final DeferredItem<Item> PURGE_SHOT = ITEMS.register("purge_shot", () -> new PurgeShot(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> ANDROID_EGG = ITEMS.register("android_spawn_egg",
            () -> new DeferredSpawnEggItem(ANDROID, 0xB8C0C8, 0xE02030, new Item.Properties()));
    public static final Supplier<SoundEvent> ALERT = sound("alert"), GLITCH = sound("glitch");

    public static final Supplier<AttachmentType<Float>> INFECTION = ATTACHMENTS.register("infection",
            () -> AttachmentType.builder(() -> 0f).serialize(Codec.FLOAT).build());
    /** How long this player has been corrupted, in ticks (counted from 1); 0 = not corrupted. */
    public static final Supplier<AttachmentType<Long>> CORRUPTED = ATTACHMENTS.register("corrupted",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).build());

    static {
        ITEMS.registerSimpleBlockItem(TV);
        ITEMS.registerSimpleBlockItem(CORRUPTED_SOIL);
    }

    public static final Supplier<CreativeModeTab> TAB = TABS.register("invasion", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chromeflesh_invasion"))
            .icon(() -> new ItemStack(TV.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> out.accept(e.get())))
            .build());

    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MODID, name)));
    }

    public Invasion(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, InvasionConfig.SPEC);
        modBus.addListener(Invasion::attributes);
        modBus.addListener(Invasion::payloads);
        NeoForge.EVENT_BUS.register(Invasion.class);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ANDROID.get(), Android.attributes().build());
    }

    // ---- Infection ----

    public static final float SPREAD = 4, DECAY = 0.2f, HIT = 25;
    private static final ResourceLocation NO_JUMP = ResourceLocation.fromNamespaceAndPath(MODID, "corrupted_no_jump");

    private static void jumpLock(ServerPlayer p, boolean on) {
        var jump = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        if (jump == null) return;
        if (on && !jump.hasModifier(NO_JUMP))
            jump.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(NO_JUMP, -1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on) jump.removeModifier(NO_JUMP);
    }

    public static float infection(Player p) {
        return p.getData(INFECTION);
    }

    public static boolean corrupted(Player p) {
        return p.getData(CORRUPTED) > 0;
    }

    /** Protected by Chromeflesh's Firewall implant: the virus can't get in. */
    public static boolean firewalled(Player p) {
        try {
            return Implants.has(p, Implant.FIREWALL);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** More infection; at 100% the player is corrupted. Returns false if a firewall stopped it. */
    public static boolean infect(ServerPlayer p, float amount) {
        GameType mode = p.gameMode.getGameModeForPlayer();
        if (!InvasionConfig.INFECTION.get() || mode == GameType.CREATIVE || mode == GameType.SPECTATOR || corrupted(p)) return false;
        if (firewalled(p)) {
            p.displayClientMessage(Component.translatable("chromeflesh_invasion.firewall_blocked"), true);
            return false;
        }
        float before = infection(p), now = Math.min(100, before + amount);
        p.setData(INFECTION, now);
        if (before < 50 && now >= 50) p.displayClientMessage(Component.translatable("chromeflesh_invasion.infection_half").withStyle(ChatFormatting.RED), false);
        if (now >= 100) corrupt(p);
        return true;
    }

    public static void corrupt(ServerPlayer p) {
        p.setData(INFECTION, 100f);
        p.setData(CORRUPTED, 1L);
        p.refreshDisplayName();
        p.displayClientMessage(Component.translatable("chromeflesh_invasion.you_corrupted").withStyle(ChatFormatting.DARK_RED), false);
        p.server.getPlayerList().broadcastSystemMessage(Component.translatable("chromeflesh_invasion.player_corrupted", glitch(p.getGameProfile().getName()))
                .withStyle(ChatFormatting.RED), false);
        p.level().playSound(null, p.blockPosition(), GLITCH.get(), SoundSource.PLAYERS, 1, 0.8f);
        sync(p);
    }

    /** Back to normal: a Purge Shot, the firewall, or the player's own system after a while. */
    public static void cure(ServerPlayer p, float infectionLeft) {
        boolean was = corrupted(p);
        p.setData(INFECTION, infectionLeft);
        p.setData(CORRUPTED, 0L);
        p.setForcedPose(null);
        jumpLock(p, false);
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.DIG_SLOWDOWN);
        p.removeEffect(MobEffects.WEAKNESS);
        p.refreshDisplayName();
        if (was) p.displayClientMessage(Component.translatable("chromeflesh_invasion.cured").withStyle(ChatFormatting.AQUA), false);
        sync(p);
    }

    /** One second of infection for this player. */
    public static void second(ServerPlayer p) {
        if (corrupted(p)) {
            long since = p.getData(CORRUPTED) + 20;
            p.setData(CORRUPTED, since);
            if (since >= InvasionConfig.CORRUPTED_MINUTES.get() * 60L * 20 || firewalled(p)) {
                cure(p, 40);
                return;
            }
            // Overridden: can't move, can't fight, can't mine. Just crouching there, glitching.
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 255, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 255, false, false));
            p.setForcedPose(Pose.CROUCHING);
            jumpLock(p, true);
            p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
            p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.5, 0.3, 0.05);
            if (p.getRandom().nextInt(6) == 0) p.level().playSound(null, p.blockPosition(), GLITCH.get(), SoundSource.PLAYERS, 0.5f, 0.6f + p.getRandom().nextFloat());
            // The corruption spreads to anyone close who can see you.
            for (ServerPlayer o : p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(3),
                    o -> o != p && !corrupted(o) && o.hasLineOfSight(p)))
                infect(o, SPREAD);
        } else if (infection(p) > 0) {
            p.setData(INFECTION, Math.max(0, infection(p) - DECAY));
        }
        sync(p);
    }

    /** "r̷o̴b̴o̸t̶": glitched text, like the alert. */
    public static String glitch(String s) {
        String marks = "̷̴̸̵̶";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) b.append(s.charAt(i)).append(marks.charAt(i % marks.length()));
        return b.toString();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && p.tickCount % 20 == 0) second(p);
    }

    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat event) {
        if (corrupted(event.getEntity())) event.setDisplayname(Component.literal(glitch(event.getUsername().getString())).withStyle(ChatFormatting.DARK_RED));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            p.refreshDisplayName();
            sync(p);
        }
    }

    // A corrupted player has no control: nothing they try to do works.
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (corrupted(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        if (corrupted(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract e) {
        if (corrupted(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (corrupted(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (corrupted(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (corrupted(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p && corrupted(p)) e.setCanceled(true);
    }

    // ---- The invasion ----

    public static class Data extends SavedData {
        public boolean started;
        public long startDay;

        public static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Data::new, Data::load), "chromeflesh_invasion");
        }

        static Data load(CompoundTag t, HolderLookup.Provider p) {
            Data d = new Data();
            d.started = t.getBoolean("started");
            d.startDay = t.getLong("startDay");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
            t.putBoolean("started", started);
            t.putLong("startDay", startDay);
            return t;
        }
    }

    public static long day(Level level) {
        return level.getDayTime() / 24000 + 1;
    }

    public static boolean started(MinecraftServer server) {
        return Data.get(server).started;
    }

    /** The breach: the first android appears near a random player, and from now on every TV that's on shows the alert. */
    public static void start(MinecraftServer server) {
        Data d = Data.get(server);
        if (d.started) return;
        d.started = true;
        d.startDay = day(server.overworld());
        d.setDirty();
        List<ServerPlayer> players = server.overworld().players();
        if (!players.isEmpty()) {
            ServerPlayer p = players.get(server.overworld().random.nextInt(players.size()));
            Android unit = spawnNear(server.overworld(), p, 40, 56);
            if (unit != null) {
                unit.setPersistenceRequired();
                unit.setCustomName(Component.literal(glitch("UNIT-0")).withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    /** An android on the surface 'min'..'max' blocks from this player. */
    public static Android spawnNear(ServerLevel level, Player p, int min, int max) {
        for (int tries = 0; tries < 8; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = min + level.random.nextDouble() * (max - min);
            int x = (int) (p.getX() + Math.cos(a) * r), z = (int) (p.getZ() + Math.sin(a) * r);
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getFluidState(at.below()).isEmpty() || !level.hasChunkAt(at)) continue;
            Android android = ANDROID.get().spawn(level, at, MobSpawnType.EVENT);
            if (android != null) return android;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel world = server.overworld();
        if (world.getGameTime() % 100 != 0 || !InvasionConfig.INVASION.get()) return;
        Data d = Data.get(server);
        if (!d.started && day(world) >= InvasionConfig.INVASION_DAY.get()) start(server);
        if (!d.started || world.getGameTime() % 600 != 0) return;
        // Every night after the breach, more androids come: 2 on the first night, one more each night.
        long t = world.getDayTime() % 24000;
        if (t < 13000 || t > 23000) return;
        int cap = (int) Math.min(InvasionConfig.MAX_ANDROIDS.get(), 2 + Math.max(0, day(world) - d.startDay));
        for (ServerPlayer p : world.players()) {
            if (p.isSpectator() || corrupted(p)) continue;
            int near = world.getEntitiesOfClass(Android.class, p.getBoundingBox().inflate(64)).size();
            if (near < cap) spawnNear(world, p, 24, 40);
        }
    }

    // ---- The HUD: infection meter and the corruption overlay ----

    public record Sync(float infection, boolean corrupted) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Sync::infection,
                ByteBufCodecs.BOOL, Sync::corrupted, Sync::new);
        public static volatile Sync latest = new Sync(0, false);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void sync(ServerPlayer p) {
        if (p.connection != null && p.connection.hasChannel(Sync.TYPE)) PacketDistributor.sendToPlayer(p, new Sync(infection(p), corrupted(p)));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Sync.TYPE, Sync.CODEC, (payload, ctx) -> Sync.latest = payload);
    }

    // ---- The cure ----

    static class PurgeShot extends Item {
        PurgeShot(Properties props) {
            super(props);
        }

        // On someone else (a corrupted player can't use anything themselves).
        @Override
        public InteractionResult interactLivingEntity(ItemStack stack, Player user, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.InteractionHand hand) {
            if (!(target instanceof ServerPlayer patient)) return InteractionResult.PASS;
            cure(patient, 0);
            if (!user.isCreative()) stack.shrink(1);
            user.displayClientMessage(Component.translatable("chromeflesh_invasion.purged", patient.getName()), true);
            return InteractionResult.SUCCESS;
        }

        // On yourself, while you still can.
        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player p, net.minecraft.world.InteractionHand hand) {
            ItemStack stack = p.getItemInHand(hand);
            if (!level.isClientSide && p instanceof ServerPlayer sp && infection(sp) > 0) {
                cure(sp, 0);
                if (!p.isCreative()) stack.shrink(1);
                p.displayClientMessage(Component.translatable("chromeflesh_invasion.purged_self"), true);
                return net.minecraft.world.InteractionResultHolder.consume(stack);
            }
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            lines.add(Component.translatable("item.chromeflesh_invasion.purge_shot.tip"));
        }
    }
}
