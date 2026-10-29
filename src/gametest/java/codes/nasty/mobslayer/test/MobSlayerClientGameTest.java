package codes.nasty.mobslayer.test;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.SlayerConfig;
import codes.nasty.mobslayer.block.MobSlayerBlock;
import codes.nasty.mobslayer.block.MobSlayerBlockEntity;
import codes.nasty.mobslayer.block.RedstoneMode;
import codes.nasty.mobslayer.client.MobSlayerScreen;
import codes.nasty.mobslayer.client.config.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.ModMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

/**
 * End-to-end check in a real client (English, survival, 1920x1080): opens the Mob Slayer
 * with a right click, presses the damage, redstone and collect buttons and verifies the results on the server, shows
 * the running block, then changes settings through the config screen behind Mod Menu's config button.
 * Run with {@code gradlew runClientGameTest}; screenshots land in build/run/clientGameTest/screenshots.
 */
public class MobSlayerClientGameTest implements FabricClientGameTest {
	/** Where {@link #prepare} put the block; set on the server thread before anything reads it. */
	private static volatile BlockPos pos;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.getLanguageManager().setSelected("en_us"));
		context.runOnClient(client -> client.reloadResourcePacks());
		context.waitFor(client -> client.getOverlay() == null);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule doDaylightCycle false");

			server.runOnServer(MobSlayerClientGameTest::prepare);
			context.waitTicks(10);

			// Right-click with an empty hand opens the settings.
			context.getInput().pressKey(options -> options.keyUse);
			context.waitForScreen(MobSlayerScreen.class);
			context.waitTicks(5);
			assertTrue(!context.computeOnClient(client -> ((MobSlayerScreen) client.screen).getMenu().isActive()), "status is off without a signal");
			context.takeScreenshot("mobslayer_01_opened");

			clickGui(context, MobSlayerScreen.DAMAGE_BUTTON_X[2] + 5, MobSlayerScreen.DAMAGE_Y + 5);
			clickGui(context, MobSlayerScreen.DAMAGE_BUTTON_X[3] + 5, MobSlayerScreen.DAMAGE_Y + 5);
			assertTrue(server.computeOnServer(s -> slayer(s).getDamage()) == 12.0F, "6 + 1 + 5 damage after the plus buttons");
			clickGui(context, MobSlayerScreen.DAMAGE_BUTTON_X[1] + 5, MobSlayerScreen.DAMAGE_Y + 5);
			assertTrue(server.computeOnServer(s -> slayer(s).getDamage()) == 11.0F, "11 damage after the minus button");

			clickGui(context, MobSlayerScreen.REDSTONE_X + 40, MobSlayerScreen.REDSTONE_Y + 5);
			assertTrue(server.computeOnServer(s -> slayer(s).getRedstoneMode()) == RedstoneMode.LOW, "redstone mode LOW after one click");
			assertTrue(server.computeOnServer(s -> state(s)), "the block runs in LOW mode without a signal");
			context.waitTicks(3);
			assertTrue(context.computeOnClient(client -> ((MobSlayerScreen) client.screen).getMenu().isActive()), "the GUI shows the block as active");
			context.takeScreenshot("mobslayer_02_settings");

			clickGui(context, MobSlayerScreen.COLLECT_X + 10, MobSlayerScreen.COLLECT_Y + 5);
			assertTrue(server.computeOnServer(s -> slayer(s).getStoredXp()) == 0, "reservoir is empty after collecting");
			assertTrue(server.computeOnServer(s -> player(s).totalExperience) == 1500, "player got the 1500 stored XP");
			context.takeScreenshot("mobslayer_03_collected");

			// The tooltip over the upgrade list explains what the enchantments do.
			hoverGui(context, 60, 100);
			context.waitTicks(3);
			context.takeScreenshot("mobslayer_04_upgrade_tooltip");

			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			// A husk in front of the running block gets shredded; the kill fills the reservoir again.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				Husk husk = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
				BlockPos front = pos.relative(Direction.WEST);
				husk.snapTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 90.0F, 0.0F);
				husk.setNoAi(true);
				level.addFreshEntity(husk);
				slayer(s).setRedstoneMode(RedstoneMode.IGNORED);
				// An idle block next to it shows the resting saw for comparison.
				level.setBlockAndUpdate(pos.north(), MobSlayer.MOB_SLAYER.defaultBlockState().setValue(MobSlayerBlock.FACING, Direction.WEST));
				// Stand south-west of the blocks and look at their blade sides at an angle.
				player(s).connection.teleport(pos.getX() - 2.5, pos.getY(), pos.getZ() + 3.0, -140.0F, 15.0F);
			});
			context.waitTicks(12);
			context.takeScreenshot("mobslayer_05_running");
			context.waitTicks(60);
			assertTrue(server.computeOnServer(s -> slayer(s).getStoredXp()) > 0, "the husk's XP went into the reservoir");
			context.takeScreenshot("mobslayer_06_after_kill");

			// The idle neighbour has no enchantments: its GUI explains how to get some.
			server.runOnServer(s -> player(s).openMenu((MobSlayerBlockEntity) s.overworld().getBlockEntity(pos.north())));
			context.waitForScreen(MobSlayerScreen.class);
			hoverGui(context, 40, 150);
			context.waitTicks(5);
			context.takeScreenshot("mobslayer_07_no_upgrades");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			checkConfigScreen(context, server);
			MobSlayer.LOGGER.info("[gametest] all checks passed");
		}
	}

	/** Opens the settings the way Mod Menu's config button does, edits a number and the protected mobs, saves. */
	private static void checkConfigScreen(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> ModMenu.getConfigScreen(MobSlayer.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(5);
		EditBox damage = widget(context, EditBox.class, "config.mobslayer.defaultDamage");
		hoverWidget(context, damage);
		context.takeScreenshot("mobslayer_08_config");

		// Invalid input turns the field red and blocks saving; a comma works as decimal separator.
		context.runOnClient(client -> damage.setValue("lots"));
		context.waitTicks(2);
		assertTrue(!widget(context, Button.class, "gui.done").active, "Done is blocked while a field is invalid");
		context.runOnClient(client -> damage.setValue("12,5"));
		context.waitTicks(2);
		assertTrue(widget(context, Button.class, "gui.done").active, "Done works again with a valid number");

		// The list of protected mobs opens in a text editor; add a line at the end.
		Button protectedMobs = widget(context, Button.class, "config.mobslayer.ui.edit");
		scrollTo(context, protectedMobs);
		clickWidget(context, protectedMobs);
		context.waitFor(client -> !(client.screen instanceof ConfigScreen));
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.getInput().pressKey(InputConstants.KEY_DOWN);
		context.getInput().pressKey(InputConstants.KEY_END);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.getInput().typeChars("minecraft:iron_golem");
		context.waitTicks(3);
		context.takeScreenshot("mobslayer_09_config_list");
		context.clickScreenButton("gui.done");
		context.waitForScreen(ConfigScreen.class);
		context.waitTicks(3);
		context.clickScreenButton("gui.done");
		context.waitForScreen(null);
		context.waitTicks(10);

		assertTrue(server.computeOnServer(s -> SlayerConfig.get().defaultDamage) == 12.5, "default damage saved through the screen");
		assertTrue(server.computeOnServer(s -> SlayerConfig.get().isExcluded(EntityType.getKey(EntityType.IRON_GOLEM))), "iron golem added to the protected mobs");
		String file = readConfigFile();
		assertTrue(file.contains("\"defaultDamage\": 12.5") && file.contains("minecraft:iron_golem"), "the screen wrote config/mobslayer.json");

		// Esc with unsaved changes asks first; discarding keeps the saved values.
		context.setScreen(() -> ModMenu.getConfigScreen(MobSlayer.MOD_ID, null));
		context.waitForScreen(ConfigScreen.class);
		EditBox reopened = widget(context, EditBox.class, "config.mobslayer.defaultDamage");
		assertTrue(context.computeOnClient(client -> reopened.getValue()).equals("12.5"), "the screen shows the saved value");
		context.runOnClient(client -> reopened.setValue("30"));
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(ConfirmScreen.class);
		context.clickScreenButton("config.mobslayer.ui.discard.yes");
		context.waitForScreen(null);
		context.waitTicks(5);
		assertTrue(server.computeOnServer(s -> SlayerConfig.get().defaultDamage) == 12.5, "discarded changes are not saved");

		server.runOnServer(s -> SlayerConfig.update(new SlayerConfig()));
	}

	private static String readConfigFile() {
		try {
			return Files.readString(FabricLoader.getInstance().getConfigDir().resolve(MobSlayer.MOD_ID + ".json"));
		} catch (IOException e) {
			throw new AssertionError("config file not readable", e);
		}
	}

	/** Finds a widget of the current screen, also inside lists, by the translation key of its message. */
	private static <T extends AbstractWidget> T widget(ClientGameTestContext context, Class<T> type, String translationKey) {
		return context.computeOnClient(client -> {
			List<GuiEventListener> todo = new ArrayList<>(client.screen.children());
			while (!todo.isEmpty()) {
				GuiEventListener listener = todo.removeLast();
				if (type.isInstance(listener) && ((AbstractWidget) listener).getMessage().getContents() instanceof TranslatableContents contents
					&& contents.getKey().equals(translationKey)) {
					return type.cast(listener);
				}
				if (listener instanceof ContainerEventHandler container) {
					todo.addAll(container.children());
				}
			}
			throw new AssertionError("no " + type.getSimpleName() + " with message " + translationKey);
		});
	}

	/**
	 * Scrolls the config list down until the widget is fully visible. Rows only get their position when they are drawn,
	 * so a row below the visible part still reports its old position until it scrolls into view.
	 */
	private static void scrollTo(ClientGameTestContext context, AbstractWidget widget) {
		double[] center = context.computeOnClient(client -> new double[] {client.getWindow().getScreenWidth() / 2.0, client.getWindow().getScreenHeight() / 2.0});
		context.getInput().setCursorPos(center[0], center[1]);
		for (int i = 0; i < 60 && !context.computeOnClient(client -> widget.getY() >= 40 && widget.getBottom() <= client.getWindow().getGuiScaledHeight() - 40); i++) {
			context.getInput().scroll(-1);
			context.waitTicks(1);
		}
	}

	private static void hoverWidget(ClientGameTestContext context, AbstractWidget widget) {
		double[] cursor = context.computeOnClient(client -> {
			double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
		context.waitTicks(3);
	}

	private static void clickWidget(ClientGameTestContext context, AbstractWidget widget) {
		hoverWidget(context, widget);
		context.getInput().pressMouse(0);
		context.waitTicks(3);
	}

	private static void prepare(MinecraftServer server) {
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		player.setExperiencePoints(0);
		player.setExperienceLevels(0);

		BlockPos feet = player.blockPosition();
		pos = feet.east(2);
		ServerLevel level = server.overworld();
		level.setBlockAndUpdate(pos, MobSlayer.MOB_SLAYER.defaultBlockState().setValue(MobSlayerBlock.FACING, Direction.WEST));
		MobSlayerBlockEntity slayer = slayer(server);
		HolderLookup.RegistryLookup<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		enchantments.set(registry.getOrThrow(Enchantments.SHARPNESS), 5);
		enchantments.set(registry.getOrThrow(Enchantments.FIRE_ASPECT), 2);
		enchantments.set(registry.getOrThrow(Enchantments.LOOTING), 3);
		enchantments.set(registry.getOrThrow(Enchantments.SWEEPING_EDGE), 1);
		slayer.setEnchantments(enchantments.toImmutable());
		slayer.addExperience(1500);
		// Look east at the block.
		player.connection.teleport(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, -90.0F, 30.0F);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static MobSlayerBlockEntity slayer(MinecraftServer server) {
		return (MobSlayerBlockEntity) server.overworld().getBlockEntity(pos);
	}

	private static boolean state(MinecraftServer server) {
		return server.overworld().getBlockState(pos).getValue(MobSlayerBlock.ACTIVE);
	}

	/** Clicks a point given in the GUI's own coordinates and waits for the server to answer. */
	private static void clickGui(ClientGameTestContext context, int guiX, int guiY) {
		hoverGui(context, guiX, guiY);
		context.waitTicks(2);
		context.getInput().pressMouse(0);
		context.waitTicks(5);
	}

	private static void hoverGui(ClientGameTestContext context, int guiX, int guiY) {
		double[] cursor = context.computeOnClient(client -> {
			var window = client.getWindow();
			double scale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			int left = (window.getGuiScaledWidth() - MobSlayerScreen.IMAGE_WIDTH) / 2;
			int top = (window.getGuiScaledHeight() - MobSlayerScreen.IMAGE_HEIGHT) / 2;
			return new double[] {(left + guiX + 0.5) * scale, (top + guiY + 0.5) * scale};
		});
		context.getInput().setCursorPos(cursor[0], cursor[1]);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
