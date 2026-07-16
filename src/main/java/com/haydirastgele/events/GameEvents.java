package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "haydirastgele")
public class GameEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            MobManager.tickQuest(player);
            
            if (!MobManager.activeQuestType.equals("NONE")) {
                MobManager.applyFormRestrictions(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!MobManager.activeQuestType.equals("NONE")) {
                MobManager.completeQuest(player, false);
            } else {
                MobManager.handlePlayerDeath(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobManager.handlePlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobManager.assignNewMob(player);
            MobManager.applyFormSpawnLocation(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        MobManager.handleInteract(event);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        MobManager.handleAttack(event);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            if (MobManager.isInteractionRestricted(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (MobManager.isInteractionRestricted(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (stack.is(Items.GOAT_HORN)) {
                String form = MobManager.currentMobForm.toLowerCase();
                if (MobManager.hasSpecialAbility(form)) {
                    MobManager.triggerFormAbility(serverPlayer);
                    event.getEntity().swing(event.getHand(), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFoodRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (stack.getItem().isEdible()) {
            String foodName = stack.getItem().toString();
            if (!MobManager.canEatFood(MobManager.currentMobForm, foodName)) {
                event.setCanceled(true);
                if (!player.level().isClientSide()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[!] Mevcut formunuz bu yiyeceği tüketemez!"
                    ));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player) {
            String form = MobManager.currentMobForm;
            if (!form.equalsIgnoreCase("human")) {
                float width = MobManager.getMobWidth(form);
                float height = MobManager.getMobHeight(form);
                float eyeHeight = MobManager.getMobEyeHeight(form);
                
                // 1.20.1 için ayrılmış boyutlandırma ve göz yüksekliği metotları
                event.setNewSize(net.minecraft.world.entity.EntityDimensions.scalable(width, height));
                event.setNewEyeHeight(eyeHeight);
            }
        }
    }
}
