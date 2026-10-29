package codes.nasty.mobslayer.test;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.SlayerConfig;
import codes.nasty.mobslayer.block.MobSlayerBlock;
import codes.nasty.mobslayer.block.MobSlayerBlockEntity;
import codes.nasty.mobslayer.block.RedstoneMode;
import codes.nasty.mobslayer.menu.MobSlayerMenu;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Server-side checks of the mechanics. Run with {@code gradlew runGameTest} (also part of {@code gradlew build}).
 * The tests run at the same time and share one config, so per-test values (damage, enchantments, redstone mode)
 * are set on the block entities. Without a redstone signal the default mode keeps the blocks idle, so the tests
 * call {@link MobSlayerBlockEntity#attack} themselves to hit at an exact moment.
 */
public class MobSlayerGameTest {
	private static final BlockPos SLAYER = new BlockPos(1, 1, 2);
	private static final BlockPos FRONT = SLAYER.east();

	private static void useTestConfig() {
		SlayerConfig config = new SlayerConfig();
		config.defaultDamage = 4.0;
		config.minDamage = 1.0;
		config.maxDamage = 100.0;
		config.attackIntervalTicks = 10;
		config.reach = 1;
		config.reachPerSweepingEdgeLevel = 1;
		config.maxReach = 8;
		config.maxTargetsPerAttack = 16;
		config.xpMultiplier = 1.0;
		config.maxStoredXp = 0;
		config.defaultRedstoneMode = "high";
		config.attackEffects = false;
		SlayerConfig.apply(config);
	}

	@GameTest
	public void hitsOnlyTheMobInFront(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		slayer.setDamage(100.0F);
		Husk front = still(helper.spawn(EntityType.HUSK, FRONT));
		Husk behind = still(helper.spawn(EntityType.HUSK, SLAYER.west()));
		Husk beside = still(helper.spawn(EntityType.HUSK, SLAYER.south()));
		Husk diagonal = still(helper.spawn(EntityType.HUSK, FRONT.north()));

		helper.assertTrue(slayer.attack(helper.getLevel()) == 1, "Exactly one husk should be hit");
		helper.assertTrue(front.isDeadOrDying(), "The husk in front should die");
		for (Husk other : List.of(behind, beside, diagonal)) {
			helper.assertTrue(other.getHealth() == other.getMaxHealth(), "Husks outside the blade side should be unharmed");
		}
		// An adult husk drops 5 experience for a player; the reservoir gets exactly that and no orbs drop.
		helper.assertTrue(slayer.getStoredXp() == 5, "Expected 5 stored XP, got " + slayer.getStoredXp());
		helper.assertTrue(helper.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "No experience orbs should drop");
		helper.succeed();
	}

	@GameTest
	public void facingUpHitsTheMobAbove(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.UP);
		slayer.setDamage(2.0F);
		Husk above = still(helper.spawn(EntityType.HUSK, SLAYER.above()));
		Husk front = still(helper.spawn(EntityType.HUSK, FRONT));

		helper.assertTrue(slayer.attack(helper.getLevel()) == 1, "Exactly one husk should be hit");
		helper.assertTrue(above.getHealth() < above.getMaxHealth(), "The husk above the upward blade should be hurt");
		helper.assertTrue(front.getHealth() == front.getMaxHealth(), "The husk to the east should be unharmed");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void redstoneSwitchesTheBlock(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		slayer.setDamage(1.0F);
		Husk husk = still(helper.spawn(EntityType.HUSK, FRONT));

		helper.runAfterDelay(30, () -> {
			helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "Without a signal the default mode should not attack");
			helper.assertBlockProperty(SLAYER, MobSlayerBlock.ACTIVE, false);
			helper.setBlock(SLAYER.west(), Blocks.REDSTONE_BLOCK);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertBlockProperty(SLAYER, MobSlayerBlock.ACTIVE, true);
			helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "With a signal the husk should take damage");
			// Hits count as player hits (Looting, player-only drops) but make no mob angry at the fake player.
			helper.assertTrue(husk.getLastHurtByPlayer() instanceof FakePlayer, "The hit should be credited to the fake player");
			helper.assertTrue(husk.getLastHurtByMob() == null, "The blade should not make the husk angry");

			slayer.setRedstoneMode(RedstoneMode.LOW);
			helper.assertBlockProperty(SLAYER, MobSlayerBlock.ACTIVE, false);
			slayer.setRedstoneMode(RedstoneMode.IGNORED);
			helper.assertBlockProperty(SLAYER, MobSlayerBlock.ACTIVE, true);
			helper.succeed();
		});
	}

	@GameTest
	public void sharpnessAndFireAspectWorkLikeOnASword(GameTestHelper helper) {
		MobSlayerBlockEntity plain = placeSlayer(helper, SLAYER, Direction.EAST);
		plain.setDamage(2.0F);
		Cow plainCow = still(helper.spawn(EntityType.COW, FRONT));

		BlockPos enchantedPos = SLAYER.south(3);
		MobSlayerBlockEntity enchanted = placeSlayer(helper, enchantedPos, Direction.EAST);
		enchanted.setDamage(2.0F);
		enchanted.setEnchantments(enchantments(helper, Enchantments.SHARPNESS, 5, Enchantments.FIRE_ASPECT, 2));
		Cow enchantedCow = still(helper.spawn(EntityType.COW, enchantedPos.east()));

		plain.attack(helper.getLevel());
		enchanted.attack(helper.getLevel());
		helper.assertTrue(plainCow.getHealth() == 8.0F, "2 damage should leave the cow at 8, got " + plainCow.getHealth());
		// Sharpness V adds 0.5 * 5 + 0.5 = 3 damage, like on a sword.
		helper.assertTrue(enchantedCow.getHealth() == 5.0F, "2 damage + Sharpness V should leave the cow at 5, got " + enchantedCow.getHealth());
		helper.assertTrue(!plainCow.isOnFire(), "Without Fire Aspect the cow should not burn");
		helper.assertTrue(enchantedCow.isOnFire(), "Fire Aspect should set the cow on fire");
		helper.succeed();
	}

	@GameTest
	public void lootingRaisesTheDrops(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		slayer.setDamage(100.0F);
		slayer.setEnchantments(enchantments(helper, Enchantments.LOOTING, 3));
		int cows = 12;
		for (int i = 0; i < cows; i++) {
			still(helper.spawn(EntityType.COW, FRONT));
		}

		helper.assertTrue(slayer.attack(helper.getLevel()) == cows, "All cows should be hit");
		int leather = 0;
		for (ItemEntity item : helper.getEntities(EntityType.ITEM)) {
			if (item.getItem().is(Items.LEATHER)) {
				leather += item.getItem().getCount();
			}
		}
		// Without Looting a cow drops at most 2 leather; Looting III adds up to 3 more each (on average 42 in total).
		helper.assertTrue(leather > 2 * cows, "Looting III should give more than " + 2 * cows + " leather, got " + leather);
		// Each cow gives 1-3 experience.
		int xp = slayer.getStoredXp();
		helper.assertTrue(xp >= cows && xp <= 3 * cows, "Expected " + cows + "-" + 3 * cows + " stored XP, got " + xp);
		helper.assertTrue(helper.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "No experience orbs should drop");
		helper.succeed();
	}

	@GameTest
	public void sweepingEdgeAddsReach(GameTestHelper helper) {
		BlockPos pos = new BlockPos(0, 1, 1);
		MobSlayerBlockEntity slayer = placeSlayer(helper, pos, Direction.EAST);
		slayer.setDamage(2.0F);
		slayer.setEnchantments(enchantments(helper, Enchantments.SWEEPING_EDGE, 2));
		Husk third = still(helper.spawn(EntityType.HUSK, pos.east(3)));
		Husk fourth = still(helper.spawn(EntityType.HUSK, pos.east(4)));

		helper.assertTrue(slayer.getReach() == 3, "Sweeping Edge II should give a reach of 3, got " + slayer.getReach());
		slayer.attack(helper.getLevel());
		helper.assertTrue(third.getHealth() < third.getMaxHealth(), "The husk 3 blocks away should be hit");
		helper.assertTrue(fourth.getHealth() == fourth.getMaxHealth(), "The husk 4 blocks away should be out of reach");
		helper.succeed();
	}

	@GameTest
	public void protectedMobsAreLeftAlone(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		slayer.setDamage(100.0F);
		Villager villager = still(helper.spawn(EntityType.VILLAGER, FRONT));
		Husk named = still(helper.spawn(EntityType.HUSK, FRONT));
		named.setCustomName(Component.literal("Bob"));
		Cow calf = still(helper.spawn(EntityType.COW, FRONT));
		calf.setBaby(true);
		Wolf pet = still(helper.spawn(EntityType.WOLF, FRONT));
		pet.setTame(true, false);
		pet.setOwner(mockPlayer(helper));
		Husk victim = still(helper.spawn(EntityType.HUSK, FRONT));
		// Baby monsters are not protected, or they would pile up in mob farms.
		Husk babyHusk = still(helper.spawn(EntityType.HUSK, FRONT));
		babyHusk.setBaby(true);

		helper.assertTrue(slayer.attack(helper.getLevel()) == 2, "Only the plain and the baby husk should be hit");
		helper.assertTrue(victim.isDeadOrDying() && babyHusk.isDeadOrDying(), "Both husks should die");
		for (Mob mob : List.<Mob>of(villager, named, calf, pet)) {
			helper.assertTrue(mob.getHealth() == mob.getMaxHealth(), mob.getName().getString() + " should be unharmed");
		}
		helper.succeed();
	}

	@GameTest
	public void menuButtonsChangeTheSettings(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		ServerPlayer player = mockPlayer(helper);
		MobSlayerMenu menu = new MobSlayerMenu(1, player.getInventory(), slayer);

		helper.assertTrue(slayer.getDamage() == 4.0F, "New blocks start with the default damage");
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_PLUS_5);
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_PLUS_1);
		helper.assertTrue(slayer.getDamage() == 10.0F, "4 + 5 + 1 should be 10, got " + slayer.getDamage());
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_MINUS_5);
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_MINUS_5);
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_MINUS_1);
		helper.assertTrue(slayer.getDamage() == 1.0F, "Damage should stop at the configured minimum, got " + slayer.getDamage());
		slayer.setDamage(98.0F);
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_DAMAGE_PLUS_5);
		helper.assertTrue(slayer.getDamage() == 100.0F, "Damage should stop at the configured maximum, got " + slayer.getDamage());
		helper.assertTrue(menu.getDamage() == 100.0F, "The menu data should show the damage, got " + menu.getDamage());

		helper.assertTrue(slayer.getRedstoneMode() == RedstoneMode.HIGH, "New blocks start in the default redstone mode");
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_REDSTONE_MODE);
		helper.assertTrue(slayer.getRedstoneMode() == RedstoneMode.LOW, "The button should switch to LOW");
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_REDSTONE_MODE);
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_REDSTONE_MODE);
		helper.assertTrue(slayer.getRedstoneMode() == RedstoneMode.HIGH, "Three clicks should come back to HIGH");

		// The reservoir is sent in two halves; a value above 65535 must survive that.
		slayer.addExperience(123_456);
		helper.assertTrue(menu.getStoredXp() == 123_456, "The menu data should show the XP, got " + menu.getStoredXp());
		menu.clickMenuButton(player, MobSlayerMenu.BUTTON_COLLECT);
		helper.assertTrue(player.totalExperience == 123_456, "The player should get all stored XP, got " + player.totalExperience);
		helper.assertTrue(slayer.getStoredXp() == 0, "The reservoir should be empty after collecting");
		helper.succeed();
	}

	@GameTest
	public void reservoirNeverOverflows(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		helper.assertTrue(slayer.addExperience(Integer.MAX_VALUE) == Integer.MAX_VALUE, "Without a limit everything fits");
		helper.assertTrue(slayer.addExperience(10) == 0, "A full int reservoir takes nothing more instead of overflowing");
		helper.succeed();
	}

	@GameTest
	public void savingAndBreakingKeepEverything(GameTestHelper helper) {
		MobSlayerBlockEntity slayer = placeSlayer(helper, SLAYER, Direction.EAST);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(SLAYER);
		slayer.setDamage(12.0F);
		slayer.setRedstoneMode(RedstoneMode.IGNORED);
		slayer.setEnchantments(enchantments(helper, Enchantments.SHARPNESS, 3, Enchantments.LOOTING, 2));
		slayer.addExperience(321);

		CompoundTag tag = slayer.saveWithFullMetadata(level.registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(pos, slayer.getBlockState(), tag, level.registryAccess());
		helper.assertTrue(loaded instanceof MobSlayerBlockEntity, "Saved data should load as a Mob Slayer");
		MobSlayerBlockEntity copy = (MobSlayerBlockEntity) loaded;
		helper.assertTrue(copy.getDamage() == 12.0F && copy.getRedstoneMode() == RedstoneMode.IGNORED && copy.getStoredXp() == 321,
			"Damage, redstone mode and XP should survive saving");
		helper.assertTrue(copy.getEnchantments().equals(slayer.getEnchantments()), "Enchantments should survive saving");

		level.destroyBlock(pos, true);
		int orbXp = 0;
		for (ExperienceOrb orb : helper.getEntities(EntityType.EXPERIENCE_ORB)) {
			orbXp += orb.getValue();
		}
		helper.assertTrue(orbXp == 321, "Breaking should drop the stored XP as orbs, got " + orbXp);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0), item -> item.getItem().is(MobSlayer.MOB_SLAYER_ITEM));
		helper.assertTrue(drops.size() == 1, "The block should drop itself");
		ItemStack dropped = drops.getFirst().getItem();
		helper.assertTrue(dropped.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).equals(slayer.getEnchantments()),
			"The dropped item should keep the enchantments");

		// Placing the item again moves the enchantments back into the block.
		helper.setBlock(SLAYER, MobSlayer.MOB_SLAYER.defaultBlockState());
		MobSlayerBlockEntity replaced = helper.getBlockEntity(SLAYER, MobSlayerBlockEntity.class);
		replaced.applyComponentsFromItemStack(dropped);
		helper.assertTrue(replaced.getEnchantments().equals(slayer.getEnchantments()), "A placed item should bring its enchantments");
		helper.succeed();
	}

	@GameTest
	public void dataIsLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(MobSlayer.BLADE).isPresent(), "The blade damage type should be loaded");
		ItemStack item = new ItemStack(MobSlayer.MOB_SLAYER_ITEM);
		HolderLookup.RegistryLookup<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		for (ResourceKey<Enchantment> key : List.of(Enchantments.SHARPNESS, Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS,
				Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.KNOCKBACK, Enchantments.SWEEPING_EDGE)) {
			Enchantment enchantment = registry.getOrThrow(key).value();
			helper.assertTrue(enchantment.canEnchant(item), key.identifier() + " should be applicable on an anvil");
			helper.assertTrue(key == Enchantments.SWEEPING_EDGE || enchantment.isPrimaryItem(item), key.identifier() + " should be offered by the enchanting table");
		}
		helper.assertTrue(!registry.getOrThrow(Enchantments.EFFICIENCY).value().canEnchant(item), "Tool enchantments should not apply");
		helper.assertTrue(item.isEnchantable(), "The item should be enchantable at an enchanting table");
		helper.succeed();
	}

	private static MobSlayerBlockEntity placeSlayer(GameTestHelper helper, BlockPos pos, Direction facing) {
		useTestConfig();
		helper.setBlock(pos, MobSlayer.MOB_SLAYER.defaultBlockState().setValue(MobSlayerBlock.FACING, facing));
		return helper.getBlockEntity(pos, MobSlayerBlockEntity.class);
	}

	private static <T extends Mob> T still(T mob) {
		mob.setNoAi(true);
		return mob;
	}

	private static ItemEnchantments enchantments(GameTestHelper helper, Object... keysAndLevels) {
		HolderLookup.RegistryLookup<Enchantment> registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		for (int i = 0; i < keysAndLevels.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) keysAndLevels[i];
			enchantments.set(registry.getOrThrow(key), (Integer) keysAndLevels[i + 1]);
		}
		return enchantments.toImmutable();
	}

	private static ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mobslayer-test"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos standing = helper.absolutePos(new BlockPos(5, 1, 6));
		player.snapTo(standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
		return player;
	}
}
