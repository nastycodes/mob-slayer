package codes.nasty.mobslayer;

import codes.nasty.mobslayer.block.RedstoneMode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * Server-side settings, stored in {@code config/mobslayer.json}.
 * Unknown keys are ignored and missing keys fall back to the defaults below,
 * so the file is rewritten on every successful load to pick up new options.
 */
public final class SlayerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(MobSlayer.MOD_ID + ".json");
	private static SlayerConfig instance = new SlayerConfig();

	/** Base damage of a newly placed block (2 = one heart). Players change it per block in the GUI. */
	public double defaultDamage = 6.0;
	/** The range players can set in the GUI. Setting both to the same value fixes the damage. */
	public double minDamage = 1.0;
	public double maxDamage = 40.0;
	/** Ticks between two attacks (20 = one second). Below 10 the same mob is still protected by its invulnerability frames. */
	public int attackIntervalTicks = 20;
	/** How many blocks in front of the blade side are attacked. */
	public int reach = 1;
	/** Extra blocks of reach per level of Sweeping Edge. */
	public int reachPerSweepingEdgeLevel = 1;
	/** Upper limit for the reach including Sweeping Edge. */
	public int maxReach = 8;
	/** Mobs hit per attack at most, nearest first. */
	public int maxTargetsPerAttack = 16;

	/** Players are only hurt when this is on and PvP is allowed on the server. */
	public boolean damagePlayers = false;
	/** Tamed wolves, cats, parrots, horses, ... */
	public boolean damageTamedAnimals = false;
	/** Mobs with a name tag. */
	public boolean damageNamedMobs = false;
	/** Baby animals (calves, lambs, ...), so breeding pens survive. Baby monsters such as baby zombies are always attacked. */
	public boolean damageBabyAnimals = false;
	/** Entity ids that are never attacked. */
	public List<String> excludedEntities = new ArrayList<>(List.of(
		"minecraft:villager",
		"minecraft:wandering_trader"
	));

	/** Experience that goes into the reservoir per kill, as a factor of what the mob would drop for a player. */
	public double xpMultiplier = 1.0;
	/** Most experience points one block holds; 0 = no limit. Anything over the limit drops as orbs. */
	public int maxStoredXp = 0;
	/** Redstone mode of a newly placed block: "high" (runs with a signal), "low" (runs without a signal) or "ignored" (always runs). */
	public String defaultRedstoneMode = RedstoneMode.HIGH.getSerializedName();
	/** Sweep particles and sound on every attack. */
	public boolean attackEffects = true;

	private transient Set<Identifier> excludedIds = Set.of();
	private transient RedstoneMode redstoneMode = RedstoneMode.HIGH;

	public static SlayerConfig get() {
		return instance;
	}

	/** Replaces the active settings, e.g. from game tests. The values are checked like a loaded file. */
	public static void apply(SlayerConfig config) {
		config.sanitize();
		instance = config;
	}

	/** Loads the config at startup; a broken file is reported and the defaults are used instead. */
	public static void load() {
		try {
			reload();
		} catch (IOException | JsonParseException e) {
			MobSlayer.LOGGER.error("Could not read {}, using default settings", PATH, e);
			apply(new SlayerConfig());
		}
	}

	public static void reload() throws IOException {
		SlayerConfig config = new SlayerConfig();
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
				SlayerConfig parsed = GSON.fromJson(reader, SlayerConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			}
		}
		apply(config);
		save(config);
	}

	/** Activates settings changed in the config screen and writes them to the file. */
	public static void update(SlayerConfig config) {
		apply(config);
		save(config);
	}

	/** A deep copy, for the config screen to change. */
	public SlayerConfig copy() {
		return GSON.fromJson(GSON.toJsonTree(this), SlayerConfig.class);
	}

	private static void save(SlayerConfig config) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			MobSlayer.LOGGER.error("Could not write {}", PATH, e);
		}
	}

	public float clampDamage(float damage) {
		return (float) clamp(damage, minDamage, maxDamage);
	}

	public boolean isExcluded(Identifier entityType) {
		return excludedIds.contains(entityType);
	}

	public RedstoneMode defaultRedstoneMode() {
		return redstoneMode;
	}

	private void sanitize() {
		minDamage = clamp(minDamage, 0.0, 1000.0);
		maxDamage = clamp(maxDamage, minDamage, 1000.0);
		defaultDamage = clamp(defaultDamage, minDamage, maxDamage);
		attackIntervalTicks = clamp(attackIntervalTicks, 1, 1200);
		maxReach = clamp(maxReach, 1, 16);
		reach = clamp(reach, 1, maxReach);
		reachPerSweepingEdgeLevel = clamp(reachPerSweepingEdgeLevel, 0, 16);
		maxTargetsPerAttack = clamp(maxTargetsPerAttack, 1, 256);
		if (excludedEntities == null) excludedEntities = new ArrayList<>();
		Set<Identifier> ids = new HashSet<>();
		for (String id : excludedEntities) {
			Identifier parsed = id == null ? null : Identifier.tryParse(id);
			if (parsed != null) {
				ids.add(parsed);
			} else {
				MobSlayer.LOGGER.warn("Ignoring invalid entity id '{}' in excludedEntities", id);
			}
		}
		excludedIds = ids;
		xpMultiplier = clamp(xpMultiplier, 0.0, 1000.0);
		maxStoredXp = Math.max(0, maxStoredXp);
		redstoneMode = RedstoneMode.byName(defaultRedstoneMode);
		defaultRedstoneMode = redstoneMode.getSerializedName();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
