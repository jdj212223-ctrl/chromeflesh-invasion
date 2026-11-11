package com.jdj125.invasion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;

/**
 * The rogue android: fast, tough, not bothered by daylight. Every hit infects a player (unless their Firewall stops
 * it). It leaves corrupted ground behind it and ignores players who are already corrupted: they're on its side now.
 */
public class Android extends Monster {
    public Android(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.ARMOR, 4).add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, e -> !(e instanceof Player p) || !Invasion.corrupted(p)));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof ServerPlayer p) Invasion.infect(p, Invasion.HIT);
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl) || tickCount % 20 != 0 || !InvasionConfig.GRIEFING.get()) return;
        if (!sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) || random.nextInt(5) != 0) return;
        BlockPos below = blockPosition().below();
        BlockState s = sl.getBlockState(below);
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.COARSE_DIRT))
            sl.setBlockAndUpdate(below, Invasion.CORRUPTED_SOIL.get().defaultBlockState());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Invasion.GLITCH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.3f, 1.4f);
    }
}
