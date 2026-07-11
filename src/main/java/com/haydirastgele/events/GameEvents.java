package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class GameEvents {
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
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

    @SubscribeEvent
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        MobManager.handleInteract(event);
    }

    @SubscribeEvent
    public static void onEntityAttack(LivingAttackEvent event) {
        MobManager.handleAttack(event);
    }

    // Yeni Eklenen: Blok koyma ve bloklarla etkileşime girme engeli
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // MobManager içinde bu formun blok koyup/etkileşime giremediğini kontrol edeceğiz
            if (MobManager.isInteractionRestricted(player)) {
                event.setCanceled(true);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[!] Bu formdayken blok koyamaz veya etkileşime giremezsiniz!"));
            }
        }
    }
}

