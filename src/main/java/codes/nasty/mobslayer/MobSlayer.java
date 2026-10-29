package codes.nasty.mobslayer;

import codes.nasty.mobslayer.block.MobSlayerBlock;
import codes.nasty.mobslayer.block.MobSlayerBlockEntity;
import codes.nasty.mobslayer.menu.MobSlayerMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MobSlayer implements ModInitializer {
	public static final String MOD_ID = "mobslayer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Identifier MOB_SLAYER_ID = id("mob_slayer");
	private static final ResourceKey<Block> MOB_SLAYER_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, MOB_SLAYER_ID);
	private static final ResourceKey<Item> MOB_SLAYER_ITEM_KEY = ResourceKey.create(Registries.ITEM, MOB_SLAYER_ID);

	/** The damage type of the blades (data/mobslayer/damage_type/blade.json): no knockback, no anger. */
	public static final ResourceKey<DamageType> BLADE = ResourceKey.create(Registries.DAMAGE_TYPE, id("blade"));

	public static final Block MOB_SLAYER = Registry.register(
		BuiltInRegistries.BLOCK,
		MOB_SLAYER_BLOCK_KEY,
		new MobSlayerBlock(BlockBehaviour.Properties.of()
			.setId(MOB_SLAYER_BLOCK_KEY)
			.mapColor(MapColor.METAL)
			.requiresCorrectToolForDrops()
			.strength(5.0F, 6.0F)
			.sound(SoundType.METAL))
	);

	/**
	 * Enchantable like an iron sword: the item is in the melee weapon and sweeping enchantment tags,
	 * so the enchanting table and the anvil offer the sword enchantments for it.
	 */
	public static final Item MOB_SLAYER_ITEM = Registry.register(
		BuiltInRegistries.ITEM,
		MOB_SLAYER_ITEM_KEY,
		new BlockItem(MOB_SLAYER, new Item.Properties().setId(MOB_SLAYER_ITEM_KEY).useBlockDescriptionPrefix().enchantable(14))
	);

	public static final BlockEntityType<MobSlayerBlockEntity> MOB_SLAYER_BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		MOB_SLAYER_ID,
		FabricBlockEntityTypeBuilder.create(MobSlayerBlockEntity::new, MOB_SLAYER).build()
	);

	/** The client gets the block's enchantments when the GUI opens; everything else is synced as menu data. */
	public static final ExtendedScreenHandlerType<MobSlayerMenu, ItemEnchantments> MOB_SLAYER_MENU = Registry.register(
		BuiltInRegistries.MENU,
		MOB_SLAYER_ID,
		new ExtendedScreenHandlerType<>(MobSlayerMenu::new, ItemEnchantments.STREAM_CODEC)
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SlayerConfig.load();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
			.register(entries -> entries.addAfter(Items.DISPENSER, MOB_SLAYER_ITEM));

		ServerLivingEntityEvents.ALLOW_DEATH.register(MobSlayerBlockEntity::onEntityDeath);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal(MOD_ID)
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(context -> {
					try {
						SlayerConfig.reload();
					} catch (Exception e) {
						context.getSource().sendFailure(Component.translatable("mobslayer.command.reload_failed", e.getMessage()));
						return 0;
					}
					context.getSource().sendSuccess(() -> Component.translatable("mobslayer.command.reloaded"), true);
					return 1;
				}))
		));
	}
}
