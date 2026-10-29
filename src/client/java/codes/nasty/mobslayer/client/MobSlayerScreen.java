package codes.nasty.mobslayer.client;

import codes.nasty.mobslayer.block.RedstoneMode;
import codes.nasty.mobslayer.menu.MobSlayerMenu;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Settings of one Mob Slayer: redstone mode, base damage, the enchantments it carries and the experience reservoir.
 * There are no slots, so the panel is drawn from plain rectangles in the style of the vanilla containers.
 */
public class MobSlayerScreen extends AbstractContainerScreen<MobSlayerMenu> {
	public static final int IMAGE_WIDTH = 176;
	public static final int IMAGE_HEIGHT = 170;

	/** Widget bounds in GUI coordinates, also used by the client game test. */
	public static final int REDSTONE_X = 8;
	public static final int REDSTONE_Y = 19;
	public static final int REDSTONE_WIDTH = 160;
	public static final int DAMAGE_Y = 53;
	public static final int[] DAMAGE_BUTTON_X = {8, 32, 122, 146};
	public static final int DAMAGE_BUTTON_WIDTH = 22;
	public static final int COLLECT_X = 112;
	public static final int COLLECT_Y = 147;
	public static final int COLLECT_WIDTH = 56;
	public static final int BUTTON_HEIGHT = 16;

	private static final int[] DAMAGE_BUTTON_IDS = {
		MobSlayerMenu.BUTTON_DAMAGE_MINUS_5, MobSlayerMenu.BUTTON_DAMAGE_MINUS_1, MobSlayerMenu.BUTTON_DAMAGE_PLUS_1, MobSlayerMenu.BUTTON_DAMAGE_PLUS_5
	};
	private static final String[] DAMAGE_BUTTON_LABELS = {"-5", "-1", "+1", "+5"};

	private static final int UPGRADES_X = 7;
	private static final int UPGRADES_Y = 87;
	private static final int UPGRADES_WIDTH = 162;
	private static final int UPGRADES_HEIGHT = 52;
	private static final int UPGRADE_LINES = 5;
	private static final int UPGRADE_LINE_HEIGHT = 10;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int PANEL_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_DARK = 0xFF555555;
	private static final int OUTLINE = 0xFF000000;
	private static final int INSET = 0xFF2E2E2E;
	private static final int INSET_DARK = 0xFF373737;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_ACTIVE = 0xFF1E7B1E;
	private static final int TEXT_INACTIVE = 0xFFA01818;
	private static final int TEXT_ENCHANTMENT = 0xFFE0C8FF;
	private static final int TEXT_CURSE = 0xFFFF5555;
	private static final int TEXT_HINT = 0xFFA8A8A8;

	private static final Component DAMAGE_LABEL = Component.translatable("gui.mobslayer.damage");
	private static final Component UPGRADES_LABEL = Component.translatable("gui.mobslayer.upgrades");
	private static final Component COLLECT = Component.translatable("gui.mobslayer.collect");
	private static final ItemStack XP_ICON = new ItemStack(Items.EXPERIENCE_BOTTLE);

	private final List<Button> damageButtons = new ArrayList<>();
	private Button redstoneButton;
	private Button collectButton;
	private RedstoneMode shownMode;
	private int shownXp = -1;
	private int shownLevels;

	public MobSlayerScreen(MobSlayerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = IMAGE_WIDTH;
		this.imageHeight = IMAGE_HEIGHT;
	}

	@Override
	protected void init() {
		super.init();
		this.redstoneButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.press(MobSlayerMenu.BUTTON_REDSTONE_MODE))
			.bounds(this.leftPos + REDSTONE_X, this.topPos + REDSTONE_Y, REDSTONE_WIDTH, BUTTON_HEIGHT)
			.build());
		this.damageButtons.clear();
		for (int i = 0; i < DAMAGE_BUTTON_IDS.length; i++) {
			int id = DAMAGE_BUTTON_IDS[i];
			this.damageButtons.add(this.addRenderableWidget(Button.builder(Component.literal(DAMAGE_BUTTON_LABELS[i]), button -> this.press(id))
				.bounds(this.leftPos + DAMAGE_BUTTON_X[i], this.topPos + DAMAGE_Y, DAMAGE_BUTTON_WIDTH, BUTTON_HEIGHT)
				.build()));
		}
		this.collectButton = this.addRenderableWidget(Button.builder(COLLECT, button -> this.press(MobSlayerMenu.BUTTON_COLLECT))
			.bounds(this.leftPos + COLLECT_X, this.topPos + COLLECT_Y, COLLECT_WIDTH, BUTTON_HEIGHT)
			.tooltip(Tooltip.create(Component.translatable("gui.mobslayer.collect.tooltip")))
			.build());
		this.shownMode = null;
		this.updateButtons();
	}

	private void press(int buttonId) {
		this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.updateButtons();
	}

	private void updateButtons() {
		RedstoneMode mode = this.menu.getRedstoneMode();
		if (mode != this.shownMode) {
			this.shownMode = mode;
			this.redstoneButton.setMessage(Component.translatable("gui.mobslayer.redstone", mode.label()));
			this.redstoneButton.setTooltip(Tooltip.create(mode.description()));
		}
		float damage = this.menu.getDamage();
		this.damageButtons.get(0).active = damage > this.menu.getMinDamage();
		this.damageButtons.get(1).active = damage > this.menu.getMinDamage();
		this.damageButtons.get(2).active = damage < this.menu.getMaxDamage();
		this.damageButtons.get(3).active = damage < this.menu.getMaxDamage();
		int xp = this.menu.getStoredXp();
		this.collectButton.active = xp > 0;
		if (xp != this.shownXp) {
			this.shownXp = xp;
			this.shownLevels = levelsFor(xp);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		this.renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);
		drawInset(graphics, x + UPGRADES_X, y + UPGRADES_Y, UPGRADES_WIDTH, UPGRADES_HEIGHT);
		graphics.renderItem(XP_ICON, x + 8, y + 146);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean active = this.menu.isActive();
		Component status = Component.translatable(active ? "gui.mobslayer.status.active" : "gui.mobslayer.status.inactive");
		int statusWidth = this.font.width(status);
		int titleWidth = this.imageWidth - 16 - statusWidth - 6;
		FormattedCharSequence title = this.font.width(this.title) <= titleWidth
			? this.title.getVisualOrderText()
			: this.font.split(this.title, titleWidth).getFirst();
		graphics.drawString(this.font, title, 8, 7, TEXT, false);
		graphics.drawString(this.font, status, this.imageWidth - 8 - statusWidth, 7, active ? TEXT_ACTIVE : TEXT_INACTIVE, false);

		graphics.drawString(this.font, DAMAGE_LABEL, 8, 42, TEXT, false);
		float damage = this.menu.getDamage();
		Component damageText = Component.literal(this.format(damage) + " (")
			.append(Component.literal("❤").withStyle(ChatFormatting.RED))
			.append(" " + this.format(damage / 2.0F) + ")");
		graphics.drawString(this.font, damageText, 88 - this.font.width(damageText) / 2, DAMAGE_Y + 4, TEXT, false);

		graphics.drawString(this.font, UPGRADES_LABEL, 8, 77, TEXT, false);
		int reach = this.menu.getReach();
		Component reachText = Component.translatable(reach == 1 ? "gui.mobslayer.reach.one" : "gui.mobslayer.reach", reach);
		graphics.drawString(this.font, reachText, this.imageWidth - 8 - this.font.width(reachText), 77, TEXT, false);
		this.renderUpgrades(graphics);

		graphics.drawString(this.font, Component.translatable("gui.mobslayer.xp", this.format(this.shownXp)), 28, 145, TEXT, false);
		graphics.drawString(this.font, Component.translatable("gui.mobslayer.xp.levels", this.format(this.shownLevels)), 28, 155, 0xFF707070, false);
	}

	private void renderUpgrades(GuiGraphics graphics) {
		List<Object2IntMap.Entry<Holder<Enchantment>>> entries = this.sortedEnchantments();
		int x = UPGRADES_X + 4;
		int y = UPGRADES_Y + 2;
		if (entries.isEmpty()) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("gui.mobslayer.upgrades.none"), UPGRADES_WIDTH - 8)) {
				graphics.drawString(this.font, line, x, y + 1, TEXT_HINT);
				y += UPGRADE_LINE_HEIGHT;
			}
			return;
		}
		int shown = entries.size() <= UPGRADE_LINES ? entries.size() : UPGRADE_LINES - 1;
		for (int i = 0; i < shown; i++) {
			Holder<Enchantment> enchantment = entries.get(i).getKey();
			String name = Enchantment.getFullname(enchantment, entries.get(i).getIntValue()).getString();
			graphics.drawString(this.font, name, x, y + 1, enchantment.is(EnchantmentTags.CURSE) ? TEXT_CURSE : TEXT_ENCHANTMENT);
			y += UPGRADE_LINE_HEIGHT;
		}
		if (shown < entries.size()) {
			graphics.drawString(this.font, Component.translatable("gui.mobslayer.upgrades.more", entries.size() - shown), x, y + 1, TEXT_HINT);
		}
	}

	@Override
	protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderTooltip(graphics, mouseX, mouseY);
		if (this.isHovering(UPGRADES_X, UPGRADES_Y, UPGRADES_WIDTH, UPGRADES_HEIGHT, mouseX, mouseY)) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.mobslayer.upgrades.tooltip").withStyle(ChatFormatting.LIGHT_PURPLE));
			for (String key : List.of("damage", "fire", "looting", "knockback", "sweeping")) {
				lines.add(Component.translatable("gui.mobslayer.upgrades.tooltip." + key).withStyle(ChatFormatting.GRAY));
			}
			for (Object2IntMap.Entry<Holder<Enchantment>> entry : this.sortedEnchantments()) {
				if (lines.size() == 6) {
					lines.add(Component.empty());
				}
				lines.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
			}
			graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
		} else if (this.isHovering(8, 144, COLLECT_X - 12, 20, mouseX, mouseY)) {
			graphics.setComponentTooltipForNextFrame(this.font, List.of(
				Component.translatable("gui.mobslayer.xp.tooltip", this.format(this.shownXp)),
				Component.translatable("gui.mobslayer.xp.tooltip.levels", this.format(this.shownLevels)).withStyle(ChatFormatting.GRAY)
			), mouseX, mouseY);
		}
	}

	/** The enchantment map has no stable order, so the list is sorted by the translated name. */
	private List<Object2IntMap.Entry<Holder<Enchantment>>> sortedEnchantments() {
		List<Object2IntMap.Entry<Holder<Enchantment>>> entries = new ArrayList<>(this.menu.getEnchantments().entrySet());
		entries.sort(Comparator.comparing(entry -> entry.getKey().value().description().getString()));
		return entries;
	}

	/** Vanilla container look: black outline with rounded corners, light top-left and dark bottom-right bevel. */
	private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
		int right = x + width;
		int bottom = y + height;
		graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PANEL);
		graphics.fill(x + 2, y, right - 3, y + 1, OUTLINE);
		graphics.fill(x + 3, bottom - 1, right - 2, bottom, OUTLINE);
		graphics.fill(x, y + 2, x + 1, bottom - 3, OUTLINE);
		graphics.fill(right - 1, y + 3, right, bottom - 2, OUTLINE);
		graphics.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
		graphics.fill(right - 3, y + 1, right - 2, y + 2, OUTLINE);
		graphics.fill(right - 2, y + 2, right - 1, y + 3, OUTLINE);
		graphics.fill(x + 1, bottom - 3, x + 2, bottom - 2, OUTLINE);
		graphics.fill(x + 2, bottom - 2, x + 3, bottom - 1, OUTLINE);
		graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, OUTLINE);
		graphics.fill(x + 2, y + 1, right - 3, y + 3, PANEL_LIGHT);
		graphics.fill(x + 1, y + 2, x + 3, bottom - 3, PANEL_LIGHT);
		graphics.fill(x + 3, bottom - 3, right - 2, bottom - 1, PANEL_DARK);
		graphics.fill(right - 3, y + 3, right - 1, bottom - 2, PANEL_DARK);
		graphics.fill(x + 3, y + 3, right - 3, bottom - 3, PANEL);
	}

	/** A sunken area like the slot backgrounds, but dark so the enchantment colors read well. */
	private static void drawInset(GuiGraphics graphics, int x, int y, int width, int height) {
		graphics.fill(x, y, x + width, y + height, PANEL_LIGHT);
		graphics.fill(x, y, x + width - 1, y + height - 1, INSET_DARK);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, INSET);
	}

	/** Points a player needs to go from level 0 to the returned level, the same steps as vanilla. */
	static int levelsFor(int points) {
		long remaining = points;
		int level = 0;
		while (true) {
			int needed = level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
			if (remaining < needed) {
				return level;
			}
			remaining -= needed;
			level++;
		}
	}

	private String format(double value) {
		NumberFormat format = NumberFormat.getNumberInstance(this.locale());
		format.setMaximumFractionDigits(1);
		return format.format(value);
	}

	private Locale locale() {
		return Locale.forLanguageTag(this.minecraft.getLanguageManager().getSelected().replace('_', '-'));
	}
}
