package codes.nasty.mobslayer.block;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.SlayerConfig;
import codes.nasty.mobslayer.menu.MobSlayerMenu;
import com.mojang.authlib.GameProfile;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Settings, enchantments and experience reservoir of one Mob Slayer, plus the attack itself.
 *
 * <p>Hits are dealt in the name of a fake player that holds the block's enchanted item, so the enchantments
 * work exactly like on a sword: Sharpness, Smite and Bane of Arthropods raise the damage, Fire Aspect sets
 * mobs on fire, Looting and the player-kill loot (e.g. iron from zombies) apply to the drops. The experience
 * a mob would drop for a player goes into the reservoir instead, see {@link #onEntityDeath}.
 */
public class MobSlayerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<ItemEnchantments> {
	/** Menu data. Values travel as shorts, so damage is sent in tenths and the experience in two halves. */
	public static final int DATA_DAMAGE = 0;
	public static final int DATA_MIN_DAMAGE = 1;
	public static final int DATA_MAX_DAMAGE = 2;
	public static final int DATA_XP_LOW = 3;
	public static final int DATA_XP_HIGH = 4;
	public static final int DATA_REDSTONE_MODE = 5;
	public static final int DATA_ACTIVE = 6;
	public static final int DATA_REACH = 7;
	public static final int DATA_COUNT = 8;

	private static final Component DEFAULT_NAME = Component.translatable("container.mobslayer.mob_slayer");
	/** One fake player per level does every hit; its name only shows up where mods print the killer. */
	private static final GameProfile KILLER_PROFILE = new GameProfile(UUID.fromString("6d6f6273-6c61-4965-b200-000000000001"), "[MobSlayer]");

	/** The block dealing a hit right now, so {@link #onEntityDeath} knows where the experience goes. Server thread only. */
	private static MobSlayerBlockEntity attacking;

	private float damage;
	private RedstoneMode redstoneMode;
	private int storedXp;
	private ItemEnchantments enchantments = ItemEnchantments.EMPTY;
	/** Custom name from an anvil, or null. */
	private Component name;
	private int cooldown;
	/** The block item with this block's enchantments, held by the fake player while it attacks. Rebuilt lazily. */
	private ItemStack weapon;

	private final ContainerData dataAccess = new ContainerData() {
		@Override
		public int get(int index) {
			MobSlayerBlockEntity slayer = MobSlayerBlockEntity.this;
			SlayerConfig config = SlayerConfig.get();
			return switch (index) {
				case DATA_DAMAGE -> tenths(slayer.getDamage());
				case DATA_MIN_DAMAGE -> tenths(config.minDamage);
				case DATA_MAX_DAMAGE -> tenths(config.maxDamage);
				case DATA_XP_LOW -> slayer.storedXp & 0xFFFF;
				case DATA_XP_HIGH -> slayer.storedXp >>> 16;
				case DATA_REDSTONE_MODE -> slayer.redstoneMode.ordinal();
				case DATA_ACTIVE -> slayer.getBlockState().getValue(MobSlayerBlock.ACTIVE) ? 1 : 0;
				case DATA_REACH -> slayer.getReach();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			// Only the client copy of the data is ever written.
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public MobSlayerBlockEntity(BlockPos pos, BlockState state) {
		super(MobSlayer.MOB_SLAYER_BLOCK_ENTITY, pos, state);
		SlayerConfig config = SlayerConfig.get();
		this.damage = (float) config.defaultDamage;
		this.redstoneMode = config.defaultRedstoneMode();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, MobSlayerBlockEntity slayer) {
		boolean active = slayer.updateActiveState(level, pos, state);
		if (slayer.cooldown > 0) {
			slayer.cooldown--;
		}
		if (active && slayer.cooldown <= 0 && level instanceof ServerLevel serverLevel) {
			slayer.cooldown = SlayerConfig.get().attackIntervalTicks;
			slayer.attack(serverLevel);
		}
	}

	/** Shows in the block state whether the redstone mode lets the block run right now, and returns that. */
	private boolean updateActiveState(Level level, BlockPos pos, BlockState state) {
		boolean active = this.redstoneMode.isActive(level.hasNeighborSignal(pos));
		if (state.getValue(MobSlayerBlock.ACTIVE) != active) {
			level.setBlock(pos, state.setValue(MobSlayerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
		}
		return active;
	}

	/** Hits every target in front of the blade side once, regardless of redstone and cooldown, and returns how many were hurt. */
	public int attack(ServerLevel level) {
		SlayerConfig config = SlayerConfig.get();
		BlockPos pos = this.getBlockPos();
		Direction facing = this.getBlockState().getValue(MobSlayerBlock.FACING);
		int extra = this.getReach() - 1;
		AABB zone = new AABB(pos.relative(facing)).expandTowards(facing.getStepX() * extra, facing.getStepY() * extra, facing.getStepZ() * extra);
		Vec3 center = Vec3.atCenterOf(pos);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, zone, entity -> isTarget(entity, config));
		if (targets.isEmpty()) {
			return 0;
		}
		targets.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));

		ItemStack weapon = this.getWeapon();
		FakePlayer killer = FakePlayer.get(level, KILLER_PROFILE);
		float pitch = facing == Direction.UP ? -90.0F : facing == Direction.DOWN ? 90.0F : 0.0F;
		killer.snapTo(center.x, center.y - killer.getEyeHeight(), center.z, facing.toYRot(), pitch);
		killer.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(MobSlayer.BLADE), killer);
		float baseDamage = this.getDamage();
		int hits = 0;

		attacking = this;
		try {
			for (LivingEntity target : targets.subList(0, Math.min(targets.size(), config.maxTargetsPerAttack))) {
				// The same steps as a player's sword hit: enchanted damage, then knockback and post-attack effects.
				float damage = EnchantmentHelper.modifyDamage(level, weapon, target, source, baseDamage);
				if (!target.hurtServer(level, source, damage)) {
					continue;
				}
				hits++;
				// The blade damage type has no knockback of its own, so mobs stay in front of the block unless Knockback says otherwise.
				float knockback = EnchantmentHelper.modifyKnockback(level, weapon, target, source, 0.0F);
				if (knockback > 0.0F && facing.getAxis().isHorizontal()) {
					target.knockback(knockback * 0.5F, -facing.getStepX(), -facing.getStepZ());
				}
				EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, source, weapon);
				if (config.attackEffects) {
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		} finally {
			attacking = null;
			killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}

		if (hits > 0 && config.attackEffects) {
			level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
		}
		return hits;
	}

	private static boolean isTarget(LivingEntity entity, SlayerConfig config) {
		if (!entity.isAlive() || entity.isSpectator() || config.isExcluded(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
			return false;
		}
		if (entity instanceof Player player) {
			return config.damagePlayers && !player.isCreative() && !(player instanceof FakePlayer);
		}
		// Armor stands and other non-mob living entities are left alone.
		if (!(entity instanceof Mob)) {
			return false;
		}
		if (!config.damageTamedAnimals && entity instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
			return false;
		}
		if (!config.damageNamedMobs && entity.hasCustomName()) {
			return false;
		}
		return config.damageBabyAnimals || !entity.isBaby() || entity instanceof Enemy;
	}

	/**
	 * Fabric's allow-death hook, called right before a mob dies and drops its loot. When the killing hit came from
	 * a Mob Slayer, the experience the mob would drop for a player goes into that block, and no orbs drop.
	 */
	public static boolean onEntityDeath(LivingEntity entity, DamageSource source, float amount) {
		MobSlayerBlockEntity slayer = attacking;
		if (slayer != null && entity instanceof Mob && source.is(MobSlayer.BLADE) && entity.level() instanceof ServerLevel level) {
			slayer.absorbExperience(level, entity, source);
		}
		return true;
	}

	/** Same conditions as vanilla's player-kill experience drop, so the reservoir gets exactly what the orbs would hold. */
	private void absorbExperience(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (entity.wasExperienceConsumed() || !entity.shouldDropExperience() || !level.getGameRules().get(GameRules.MOB_DROPS)) {
			return;
		}
		int reward = entity.getExperienceReward(level, source.getEntity());
		entity.skipDropExperience();

		double scaled = reward * SlayerConfig.get().xpMultiplier;
		int amount = (int) scaled;
		if (level.random.nextDouble() < scaled - amount) {
			amount++;
		}
		int stored = this.addExperience(amount);
		if (stored < amount) {
			ExperienceOrb.award(level, entity.position(), amount - stored);
		}
	}

	/** Adds experience points up to the configured limit and returns how many fit. */
	public int addExperience(int amount) {
		int limit = SlayerConfig.get().maxStoredXp;
		int space = (limit == 0 ? Integer.MAX_VALUE : limit) - this.storedXp;
		int added = Math.max(0, Math.min(amount, space));
		if (added > 0) {
			this.storedXp += added;
			this.setChanged();
		}
		return added;
	}

	/** Gives the whole reservoir to the player and returns how many points that were. */
	public int collectExperience(Player player) {
		int amount = this.storedXp;
		if (amount <= 0) {
			return 0;
		}
		this.storedXp = 0;
		this.setChanged();
		player.giveExperiencePoints(amount);
		if (this.level != null) {
			this.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 0.8F + this.level.random.nextFloat() * 0.4F);
		}
		return amount;
	}

	/** Changes the base damage by the given amount, within the configured range. */
	public void adjustDamage(float delta) {
		this.setDamage(this.getDamage() + delta);
	}

	public void setDamage(float damage) {
		this.damage = SlayerConfig.get().clampDamage(damage);
		this.setChanged();
	}

	/** The base damage before enchantments, clamped to the current config. */
	public float getDamage() {
		return SlayerConfig.get().clampDamage(this.damage);
	}

	public void setRedstoneMode(RedstoneMode mode) {
		this.redstoneMode = mode;
		this.setChanged();
		if (this.level != null) {
			this.updateActiveState(this.level, this.getBlockPos(), this.getBlockState());
		}
	}

	public RedstoneMode getRedstoneMode() {
		return this.redstoneMode;
	}

	public int getStoredXp() {
		return this.storedXp;
	}

	public void setEnchantments(ItemEnchantments enchantments) {
		this.enchantments = enchantments;
		this.weapon = null;
		this.setChanged();
	}

	public ItemEnchantments getEnchantments() {
		return this.enchantments;
	}

	/** Blocks in front of the blade side that are attacked: the configured reach plus Sweeping Edge. */
	public int getReach() {
		SlayerConfig config = SlayerConfig.get();
		int sweeping = this.enchantmentLevel(Enchantments.SWEEPING_EDGE);
		return Math.max(1, Math.min(config.maxReach, config.reach + sweeping * config.reachPerSweepingEdgeLevel));
	}

	private int enchantmentLevel(ResourceKey<Enchantment> enchantment) {
		if (this.level == null) {
			return 0;
		}
		return this.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment)
			.map(this.enchantments::getLevel)
			.orElse(0);
	}

	private ItemStack getWeapon() {
		if (this.weapon == null) {
			ItemStack weapon = new ItemStack(MobSlayer.MOB_SLAYER_ITEM);
			weapon.set(DataComponents.ENCHANTMENTS, this.enchantments);
			this.weapon = weapon;
		}
		return this.weapon;
	}

	public ContainerData getDataAccess() {
		return this.dataAccess;
	}

	private static int tenths(double value) {
		return (int) Math.round(value * 10.0);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		// The reservoir is not lost when the block is broken or blown up.
		if (this.level instanceof ServerLevel level && this.storedXp > 0) {
			ExperienceOrb.award(level, Vec3.atCenterOf(pos), this.storedXp);
			this.storedXp = 0;
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putFloat("Damage", this.damage);
		output.putString("RedstoneMode", this.redstoneMode.getSerializedName());
		output.putInt("StoredXp", this.storedXp);
		if (!this.enchantments.isEmpty()) {
			output.store("Enchantments", ItemEnchantments.CODEC, this.enchantments);
		}
		output.storeNullable("CustomName", ComponentSerialization.CODEC, this.name);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		SlayerConfig config = SlayerConfig.get();
		this.damage = input.getFloatOr("Damage", (float) config.defaultDamage);
		this.redstoneMode = RedstoneMode.byName(input.getStringOr("RedstoneMode", config.defaultRedstoneMode().getSerializedName()));
		this.storedXp = Math.max(0, input.getIntOr("StoredXp", 0));
		this.enchantments = input.read("Enchantments", ItemEnchantments.CODEC).orElse(ItemEnchantments.EMPTY);
		this.weapon = null;
		this.name = parseCustomNameSafe(input, "CustomName");
	}

	/** The item's enchantments and anvil name move into the block when it is placed... */
	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		this.name = components.get(DataComponents.CUSTOM_NAME);
		this.enchantments = components.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		this.weapon = null;
	}

	/** ...and back into the dropped item (see the loot table), so upgrades are never lost. */
	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, this.name);
		if (!this.enchantments.isEmpty()) {
			components.set(DataComponents.ENCHANTMENTS, this.enchantments);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard("CustomName");
		output.discard("Enchantments");
	}

	@Override
	public ItemEnchantments getScreenOpeningData(ServerPlayer player) {
		return this.enchantments;
	}

	@Override
	public Component getDisplayName() {
		return this.name != null ? this.name : DEFAULT_NAME;
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new MobSlayerMenu(containerId, inventory, this);
	}
}
