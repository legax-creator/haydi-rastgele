package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
            // Yeni eklenen genel form kısıtlamaları (Zıplama yasağı, otomatik zıplama basamak ayarı, çevre etkileri, envanter kilitleri)
            MobManager.applyGlobalFormRestrictions(player);
            MobManager.tickSurvivalTask(player);

            // Bu oyuncu başka bir oyuncuya biniyorsa, WASD'ini o oyuncuya (mount'a) uygula
            if (player.getVehicle() instanceof ServerPlayer mountPlayer) {
                MobManager.applyRiderControlToMount(player, mountPlayer);
            }
            
            MobManager.tickQuest(player);
            
            if (!MobManager.activeQuestType.equals("NONE")) {
                MobManager.applyFormRestrictions(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Forma özel ölüm mesajı (örn: "X bir Zombi olarak açlıktan öldü")
            MobManager.applyCustomDeathMessage(event, player);

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
        // Kurbağanın magma küpü yemesi ve yaşlı gardiyanın madenci yorgunluğu vermesi burada işleniyor
        MobManager.handleInteract(event);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        MobManager.handleAttack(event);
    }

    // Yeni eklenen vuruş efektleri (Arı sokması, Wither/Mağara örümceği etkileri) için hasar event'i
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        MobManager.handleLivingHurt(event);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            // Artık her formun kırabildiği blok türü ayrı ayrı, gerçekçi bir şekilde kontrol ediliyor
            if (!MobManager.canBreakBlock(MobManager.getForm(player.getUUID()), event.getState())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Yerleştirme izni de aynı "bu blok türüyle etkileşebilir mi" mantığına bağlandı
            if (!MobManager.canBreakBlock(MobManager.getForm(player.getUUID()), event.getPlacedBlock())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Keçi boynuzu basılınca (Enderman, Kirpi balığı, Lama, Kar Golemi) yeteneklerini tetikler
            if (stack.is(Items.GOAT_HORN)) {
                String form = MobManager.getForm(serverPlayer.getUUID()).toLowerCase();
                if (MobManager.hasSpecialAbility(form)) {
                    MobManager.triggerFormAbility(serverPlayer);
                    event.getEntity().swing(event.getHand(), true);
                }
            }
        }
    }

    // Köylünün kapı ve sandık açabilmesi, diğer mobların açamaması kuralı
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (MobManager.isBlockInteractionRestricted(player, event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFoodRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (stack.getItem().isEdible()) {
            String foodName = stack.getItem().toString();
            if (!MobManager.canEatFood(MobManager.getForm(player.getUUID()), foodName)) {
                event.setCanceled(true);
                if (!player.level().isClientSide()) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[!] Mevcut formunuz bu yiyeceği tüketemez!"
                    ));
                }
            }
        }
    }

    // NOT: Hitbox/boyut (EntityEvent.Size) artık SADECE MobManager.onPlayerSize içinde yönetiliyor.
    // Önceden burada da ikinci bir dinleyici vardı; aynı event'i iki farklı yöntemle
    // (scalable vs fixed) ve iki farklı koşulla işlemeleri client/server arasında
    // tutarsız hitbox'lara (asıl "hitbox doğru değil" sorununun kaynağı) yol açıyordu.
    // Artık tek doğru kaynak MobManager.
}
