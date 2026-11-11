package com.jdj125.invasion.test;

import com.chromeflesh.implant.Implant;
import com.chromeflesh.implant.Implants;
import com.jdj125.invasion.Invasion;
import com.jdj125.invasion.Tv;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.BiConsumer;

@GameTestHolder(Invasion.MODID)
@PrefixGameTestTemplate(false)
public class InvasionGameTests {

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void withTwo(GameTestHelper helper, BiConsumer<ServerPlayer, ServerPlayer> check) {
        ServerPlayer a = player(helper), b = player(helper);
        helper.runAfterDelay(70, () -> {
            a.setGameMode(GameType.SURVIVAL);
            b.setGameMode(GameType.SURVIVAL);
            check.accept(a, b);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void fourHitsCorrupt(GameTestHelper helper) {
        withTwo(helper, (p, unused) -> {
            for (int i = 0; i < 3; i++) Invasion.infect(p, Invasion.HIT);
            helper.assertFalse(Invasion.corrupted(p), "75% infected, still in control");
            Invasion.infect(p, Invasion.HIT);
            helper.assertTrue(Invasion.corrupted(p), "the fourth hit corrupts");
            Invasion.second(p);
            helper.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH).getValue() <= 0.001, "can't even jump");
            Invasion.cure(p, 0);
            helper.assertTrue(!Invasion.corrupted(p) && Invasion.infection(p) == 0, "a purge brings them back");
            helper.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH).getValue() > 0.1, "jumping works again");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void firewallBlocksIt(GameTestHelper helper) {
        withTwo(helper, (p, unused) -> {
            Implants.install(p, Implant.FIREWALL);
            helper.assertFalse(Invasion.infect(p, 100), "the firewall keeps the virus out");
            helper.assertFalse(Invasion.corrupted(p), "not corrupted");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void corruptionSpreads(GameTestHelper helper) {
        withTwo(helper, (a, b) -> {
            b.teleportTo(a.getX() + 1, a.getY(), a.getZ());
            Invasion.corrupt(a);
            Invasion.second(a);
            helper.assertTrue(Invasion.infection(b) >= Invasion.SPREAD, "standing next to a corrupted player infects you");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void theSystemFightsBack(GameTestHelper helper) {
        withTwo(helper, (p, unused) -> {
            Invasion.corrupt(p);
            p.setData(Invasion.CORRUPTED, 999_999L); // corrupted a very long time ago
            Invasion.second(p);
            helper.assertFalse(Invasion.corrupted(p), "nobody stays corrupted forever");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void breachTurnsTvsToTheAlert(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, Invasion.TV.get().defaultBlockState().setValue(Tv.SCREEN, 1));
        Invasion.start(helper.getLevel().getServer());
        BlockPos abs = helper.absolutePos(at);
        BlockState s = helper.getLevel().getBlockState(abs);
        s.tick(helper.getLevel(), abs, helper.getLevel().random);
        helper.assertTrue(helper.getLevel().getBlockState(abs).getValue(Tv.SCREEN) == 2, "the TV switched to the emergency alert");
        helper.succeed();
    }
}
