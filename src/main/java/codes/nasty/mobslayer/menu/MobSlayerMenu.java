package codes.nasty.mobslayer.menu;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.block.MobSlayerBlockEntity;
import codes.nasty.mobslayer.block.RedstoneMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Settings screen of a Mob Slayer without any slots. The buttons go through the vanilla container button
 * packet ({@link #clickMenuButton}); the values come back as menu data and the enchantments as opening data.
 */
public class MobSlayerMenu extends AbstractContainerMenu {
	public static final int BUTTON_COLLECT = 0;
	public static final int BUTTON_REDSTONE_MODE = 1;
	public static final int BUTTON_DAMAGE_MINUS_5 = 2;
	public static final int BUTTON_DAMAGE_MINUS_1 = 3;
	public static final int BUTTON_DAMAGE_PLUS_1 = 4;
	public static final int BUTTON_DAMAGE_PLUS_5 = 5;

	private final ContainerLevelAccess access;
	private final ContainerData data;
	/** Only set on the server. */
	private final MobSlayerBlockEntity slayer;
	private final ItemEnchantments enchantments;

	/** Client side, created from the opening data the server sends. */
	public MobSlayerMenu(int containerId, Inventory inventory, ItemEnchantments enchantments) {
		this(containerId, ContainerLevelAccess.NULL, new SimpleContainerData(MobSlayerBlockEntity.DATA_COUNT), null, enchantments);
	}

	public MobSlayerMenu(int containerId, Inventory inventory, MobSlayerBlockEntity slayer) {
		this(containerId, ContainerLevelAccess.create(slayer.getLevel(), slayer.getBlockPos()), slayer.getDataAccess(), slayer, slayer.getEnchantments());
	}

	private MobSlayerMenu(int containerId, ContainerLevelAccess access, ContainerData data, MobSlayerBlockEntity slayer, ItemEnchantments enchantments) {
		super(MobSlayer.MOB_SLAYER_MENU, containerId);
		this.access = access;
		this.data = data;
		this.slayer = slayer;
		this.enchantments = enchantments;
		this.addDataSlots(data);
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.slayer == null || this.slayer.isRemoved()) {
			return false;
		}
		switch (id) {
			case BUTTON_COLLECT -> this.slayer.collectExperience(player);
			case BUTTON_REDSTONE_MODE -> this.slayer.setRedstoneMode(this.slayer.getRedstoneMode().next());
			case BUTTON_DAMAGE_MINUS_5 -> this.slayer.adjustDamage(-5.0F);
			case BUTTON_DAMAGE_MINUS_1 -> this.slayer.adjustDamage(-1.0F);
			case BUTTON_DAMAGE_PLUS_1 -> this.slayer.adjustDamage(1.0F);
			case BUTTON_DAMAGE_PLUS_5 -> this.slayer.adjustDamage(5.0F);
			default -> {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, MobSlayer.MOB_SLAYER);
	}

	public float getDamage() {
		return this.data.get(MobSlayerBlockEntity.DATA_DAMAGE) / 10.0F;
	}

	public float getMinDamage() {
		return this.data.get(MobSlayerBlockEntity.DATA_MIN_DAMAGE) / 10.0F;
	}

	public float getMaxDamage() {
		return this.data.get(MobSlayerBlockEntity.DATA_MAX_DAMAGE) / 10.0F;
	}

	/** The two halves arrive as signed shorts on the client, hence the masks. */
	public int getStoredXp() {
		return (this.data.get(MobSlayerBlockEntity.DATA_XP_LOW) & 0xFFFF) | (this.data.get(MobSlayerBlockEntity.DATA_XP_HIGH) & 0xFFFF) << 16;
	}

	public RedstoneMode getRedstoneMode() {
		return RedstoneMode.byId(this.data.get(MobSlayerBlockEntity.DATA_REDSTONE_MODE));
	}

	public boolean isActive() {
		return this.data.get(MobSlayerBlockEntity.DATA_ACTIVE) != 0;
	}

	public int getReach() {
		return this.data.get(MobSlayerBlockEntity.DATA_REACH);
	}

	public ItemEnchantments getEnchantments() {
		return this.enchantments;
	}
}
