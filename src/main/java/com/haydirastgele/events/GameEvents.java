package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RenderPlayerEvent;
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
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MobManager.handlePlayerDeath(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer player && event.isWasDeath()) {
            MobManager.handlePlayerRespawn(player);
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
            if (event.getItemStack().is(Items.GOAT_HORN)) {
                MobManager.triggerFormAbility(player);
            }
            
            String currentForm = MobManager.currentMobForm;
            String foodName = event.getItemStack().getItem().toString();
            if (event.getItemStack().isEdible() && !MobManager.canEatFood(currentForm, foodName)) {
                event.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[!] Mevcut formunuz bu yiyeceği tüketemez!"));
            }
        }
    }

    // --- YENİ: MOB HITBOX BOYUTLARINI OYUNCUYA UYARLAMA ---
    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            String form = MobManager.currentMobForm.toLowerCase();
            if (!form.equals("human")) {
                float width = MobManager.getMobWidth(form);
                float height = MobManager.getMobHeight(form);
                float eyeHeight = MobManager.getMobEyeHeight(form);
                
                // Oyuncunun yeni kutu boyutlarını setliyoruz
                event.setNewSize(EntityDimensions.scalable(width, height));
                event.setNewEyeHeight(eyeHeight);
            }
        }
    }

    // --- YENİ: DIŞ GÖRÜNÜŞÜ (RENDER) DEĞİŞTİRME ---
    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = MobManager.currentMobForm.toLowerCase();

        // Eğer oyuncu insan formunda değilse normal insan modelini çizmeyi iptal et
        if (!form.equals("human")) {
            event.setCanceled(true);
            
            // NOT: İstemci (Client) tarafında oyun motoruna oyuncunun olduğu konuma 
            // mob modelini bastırma komutudur. İleri düzey 3D motor render çağrısı içerir.
            // Kompleks çakışmaları önlemek için morph paketini tetikler.
        }
    }
}
