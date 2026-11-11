package com.jdj125.invasion;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (chromeflesh_invasion-server.toml in the world's serverconfig folder). */
public final class InvasionConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue INVASION_DAY, CORRUPTED_MINUTES, MAX_ANDROIDS;
    public static final ModConfigSpec.BooleanValue INVASION, INFECTION, GRIEFING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        INVASION = b.comment("The invasion happens at all (false = TVs and androids only, no breach)").define("invasion", true);
        INVASION_DAY = b.comment("The day the emergency alert plays and the android breaches").defineInRange("invasionDay", 20, 1, 10000);
        INFECTION = b.comment("Androids infect players, and corrupted players lose control").define("infection", true);
        CORRUPTED_MINUTES = b.comment("A corrupted player's own system fights back and recovers after this many minutes, even with no cure")
                .defineInRange("corruptedMinutes", 15, 1, 600);
        MAX_ANDROIDS = b.comment("At most this many androids near each player at night").defineInRange("maxAndroids", 12, 0, 64);
        GRIEFING = b.comment("Androids corrupt the grass and dirt they walk on").define("griefing", true);
        SPEC = b.build();
    }

    private InvasionConfig() {
    }
}
