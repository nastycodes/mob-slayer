package codes.nasty.mobslayer.client;

import codes.nasty.mobslayer.MobSlayer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class MobSlayerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(MobSlayer.MOB_SLAYER_MENU, MobSlayerScreen::new);
	}
}
