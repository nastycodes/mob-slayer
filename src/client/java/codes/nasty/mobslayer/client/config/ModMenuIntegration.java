package codes.nasty.mobslayer.client.config;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.SlayerConfig;
import codes.nasty.mobslayer.block.RedstoneMode;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.gui.screens.Screen;

/** The config button in Mod Menu's mod list. Mod Menu is optional; without it this class is never loaded. */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ModMenuIntegration::createScreen;
	}

	public static Screen createScreen(Screen parent) {
		return new ConfigScreen<>(MobSlayer.MOD_ID, parent, SlayerConfig.get().copy(), new SlayerConfig(), SlayerConfig::update)
			.section("damage")
			.decimal("defaultDamage", 0.0, 1000.0, c -> c.defaultDamage, (c, v) -> c.defaultDamage = v)
			.decimal("minDamage", 0.0, 1000.0, c -> c.minDamage, (c, v) -> c.minDamage = v)
			.decimal("maxDamage", 0.0, 1000.0, c -> c.maxDamage, (c, v) -> c.maxDamage = v)
			.integer("attackIntervalTicks", 1, 1200, c -> c.attackIntervalTicks, (c, v) -> c.attackIntervalTicks = v)
			.integer("reach", 1, 16, c -> c.reach, (c, v) -> c.reach = v)
			.integer("reachPerSweepingEdgeLevel", 0, 16, c -> c.reachPerSweepingEdgeLevel, (c, v) -> c.reachPerSweepingEdgeLevel = v)
			.integer("maxReach", 1, 16, c -> c.maxReach, (c, v) -> c.maxReach = v)
			.integer("maxTargetsPerAttack", 1, 256, c -> c.maxTargetsPerAttack, (c, v) -> c.maxTargetsPerAttack = v)
			.section("targets")
			.toggle("damagePlayers", c -> c.damagePlayers, (c, v) -> c.damagePlayers = v)
			.toggle("damageTamedAnimals", c -> c.damageTamedAnimals, (c, v) -> c.damageTamedAnimals = v)
			.toggle("damageNamedMobs", c -> c.damageNamedMobs, (c, v) -> c.damageNamedMobs = v)
			.toggle("damageBabyAnimals", c -> c.damageBabyAnimals, (c, v) -> c.damageBabyAnimals = v)
			.list("excludedEntities", ConfigScreen.ID, c -> c.excludedEntities, (c, v) -> c.excludedEntities = new ArrayList<>(v))
			.section("experience")
			.decimal("xpMultiplier", 0.0, 1000.0, c -> c.xpMultiplier, (c, v) -> c.xpMultiplier = v)
			.integer("maxStoredXp", 0, Integer.MAX_VALUE, c -> c.maxStoredXp, (c, v) -> c.maxStoredXp = v)
			.section("misc")
			.choice("defaultRedstoneMode", Arrays.stream(RedstoneMode.values()).map(RedstoneMode::getSerializedName).toList(),
				name -> RedstoneMode.byName(name).label(), c -> c.defaultRedstoneMode, (c, v) -> c.defaultRedstoneMode = v)
			.toggle("attackEffects", c -> c.attackEffects, (c, v) -> c.attackEffects = v);
	}
}
