package com.jdj125.invasion.client;

import com.jdj125.invasion.Invasion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** The infection meter, and when you're corrupted: a glitching screen with "OVERRIDE IN PROGRESS". */
@EventBusSubscriber(modid = Invasion.MODID, value = Dist.CLIENT)
public final class InvasionHud {
    private static final RandomSource RANDOM = RandomSource.create();

    private InvasionHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Invasion.Sync s = Invasion.Sync.latest;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        if (s.corrupted()) {
            // Glitch bars and scan lines all over the screen.
            g.fill(0, 0, w, h, 0x40300008);
            for (int i = 0; i < 14; i++) {
                int y = RANDOM.nextInt(h), bh = 1 + RANDOM.nextInt(4), x = RANDOM.nextInt(w / 2), bw = w / 4 + RANDOM.nextInt(w / 2);
                int c = RANDOM.nextBoolean() ? 0x60FF2040 : 0x6020E0FF;
                g.fill(x, y, x + bw, y + bh, c);
            }
            for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x18000000);
            Component t = Component.translatable("chromeflesh_invasion.hud.override");
            int dx = RANDOM.nextInt(3) - 1;
            g.drawCenteredString(mc.font, t, w / 2 + dx, h / 2 - 30, 0xFF3040);
            g.drawCenteredString(mc.font, Component.translatable("chromeflesh_invasion.hud.override_help"), w / 2, h / 2 - 18, 0xC0C0C0);
        } else if (s.infection() > 0) {
            int x = w / 2 - 50, y = h - 60;
            g.drawString(mc.font, Component.translatable("chromeflesh_invasion.hud.infection", Math.round(s.infection())), x, y - 10, 0xFF5060, true);
            g.fill(x, y, x + 100, y + 4, 0x80000000);
            g.fill(x, y, x + Math.round(s.infection()), y + 4, 0xFFE02040);
        }
    }
}
