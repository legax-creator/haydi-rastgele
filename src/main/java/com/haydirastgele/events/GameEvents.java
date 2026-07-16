package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
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
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobManager.handlePlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobManager.handlePlayerRespawn(player);
        }
    }

    // Her karede koşma ve zıplama durumu kontrol edilir
    @SubscribeEvent
    public static void onPlayerUpdate(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String form = MobManager.currentMobForm.toLowerCase();
            
            // Eğer koşmaya elverişli olmayan bir formdaysa koşmayı iptal et
            if (player.isSprinting() && !MobManager.canMobSprint(form)) {
                player.setSprinting(false);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            if (MobManager.isInteractionRestricted(serverPlayer)) {
                event.setCanceled(true);
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[!] Bu formdayken blok kıramazsınız!"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        MobManager.handleInteract(event);
    }

    @SubscribeEvent
    public static void onPlayerAttack(LivingAttackEvent event) {
        MobManager.handleAttack(event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String currentForm = MobManager.currentMobForm.toLowerCase();

            if (event.getItemStack().is(Items.GOAT_HORN) && MobManager.hasSpecialAbility(currentForm)) {
                MobManager.triggerFormAbility(player);
                int cooldownTicks = MobManager.getCooldownTicks(currentForm);
                player.getCooldowns().addCooldown(Items.GOAT_HORN, cooldownTicks);
            }
            
            String foodName = event.getItemStack().getItem().toString();
            if (event.getItemStack().isEdible() && !MobManager.canEatFood(currentForm, foodName)) {
                event.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[!] Mevcut formunuz bu yiyeceği tüketemez!"));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            String form = MobManager.currentMobForm.toLowerCase();
            if (!form.equals("human")) {
                float width = MobManager.getMobWidth(form);
                float height = MobManager.getMobHeight(form);
                float eyeHeight = MobManager.getMobEyeHeight(form);
                
                event.setNewSize(EntityDimensions.scalable(width, height));
                event.setNewEyeHeight(eyeHeight);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = MobManager.currentMobForm.toLowerCase();

        if (!form.equals("human")) {
            event.setCanceled(true);
            
            if (player.level().isClientSide) {
                EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                EntityType<?> type = EntityType.byString(form).orElse(EntityType.PIG); 
                Entity mobEntity = type.create(player.level());
                
                if (mobEntity != null) {
                    mobEntity.setPos(player.getX(), player.getY(), player.getZ());
                    mobEntity.setYRot(player.getYRot());
                    mobEntity.setXRot(player.getXRot());
                    
                    dispatcher.render(mobEntity, 0, 0, 0, player.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
                }
            }
        }
    }
}
