package codes.nasty.mobslayer.block;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** When the Mob Slayer runs, depending on the redstone signal it receives. */
public enum RedstoneMode implements StringRepresentable {
	/** Runs only while powered, like a dispenser that keeps firing. */
	HIGH,
	/** Runs only while not powered, like a hopper. */
	LOW,
	/** Always runs. */
	IGNORED;

	private final String name = this.name().toLowerCase(Locale.ROOT);

	public boolean isActive(boolean powered) {
		return switch (this) {
			case HIGH -> powered;
			case LOW -> !powered;
			case IGNORED -> true;
		};
	}

	public RedstoneMode next() {
		return values()[(this.ordinal() + 1) % values().length];
	}

	public Component label() {
		return Component.translatable("gui.mobslayer.redstone." + this.name);
	}

	public Component description() {
		return Component.translatable("gui.mobslayer.redstone." + this.name + ".desc");
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public static RedstoneMode byName(String name) {
		for (RedstoneMode mode : values()) {
			if (mode.name.equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return HIGH;
	}

	public static RedstoneMode byId(int id) {
		RedstoneMode[] modes = values();
		return id >= 0 && id < modes.length ? modes[id] : HIGH;
	}
}
