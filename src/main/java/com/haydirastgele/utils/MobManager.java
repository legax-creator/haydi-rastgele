package com.haydirastgele.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.*;

public class MobManager {
    public static int karmaBar = 50;
    public static String currentMobForm = "human";
    private static final Random random = new Random();

    private static final List<String> TIER_0 = Arrays.asList("salmon", "cod", "villager", "strider", "frog");
    private static final List<String> TIER_10 = Arrays.asList("pufferfish", "silverfish", "horse", "donkey", "parrot", "sniffer", "wandering_trader", "bat", "bee");
    private static final List<String> TIER_20 = Arrays.asList("hoglin", "zombified_piglin", "piglin", "llama");
    private static final List<String> TIER_30 = Arrays.asList("zombie", "skeleton", "wither_skeleton");
    private static final List<String> TIER_40 = Arrays.asList("phantom", "camel", "axolotl", "magma_cube", "slime");
    private static final List<String> TIER_50 = Arrays.asList("chicken", "cow", "sheep", "mooshroom", "snow_golem", "pig", "rabbit");
    private static final List<String> TIER_60 = Arrays.asList("ghast");
    private static final List<String> TIER_70 = Arrays.asList("spider", "cave_spider");
    private static final List<String> TIER_80 = Arrays.asList("enderman");
    private static final List<String> TIER_90 = Arrays.asList("iron_golem");
    private static final List<String> TIER_100 = Arrays.asList("warden", "wither", "elder_guardian");

    public static boolean hasSpecialAbility(String form) {
        form = form.toLowerCase();
        return form.contains("ghast") || form.contains("warden") || form.contains("wither");
    }

    public static int getCooldownTicks(String form) {
        form = form.toLowerCase();
        if (form.contains("warden")) return 300; 
        if (form.contains("wither")) return 200; 
        if (form.contains("ghast")) return 120;  
        return 60;
    }

    // Mob türüne göre zıplama yüksekliği sınırlamaları
    public static double getMobJumpStrength(String form) {
        form = form.toLowerCase();
        if (form.contains("human")) return 0.42D; 

        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish") || form.contains("silverfish") || form.contains("endermite")) {
            return 0.0D; 
        }
        
        if (form.contains("rabbit") || form.contains("frog") || form.contains("slime") || form.contains("magma_cube")) {
            return 0.75D; 
        }

        if (form.contains("warden") || form.contains("iron_golem") || form.contains("cow") || form.contains("pig") || form.contains("sniffer")) {
            return 0.2D; 
        }

        return 0.42D; 
    }

    // Mob türüne göre koşma (sprinting) izni
    public static boolean canMobSprint(String form) {
        form = form.toLowerCase();
        if (form.contains("human")) return true;

        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish") || form.contains("silverfish") || form.contains("sniffer") || form.contains("turtle")) {
            return false;
        }
        return true;
    }

    // Minecraft'ta fiziksel olarak hasar vuramayan pasif varlıklar
    public static boolean canMobDealDamage(String form) {
        form = form.toLowerCase();
        if (form.contains("chicken") || form.contains("cow") || form.contains("sheep") || form.contains("pig") || 
            form.contains("rabbit") || form.contains("salmon") || form.contains("cod") || form.contains("squid") || 
            form.contains("bat") || form.contains("villager") || form.contains("wandering_trader") || 
            form.contains("frog") || form.contains("strider") || form.contains("horse") || form.contains("donkey") || 
            form.contains("llama") || form.contains("parrot") || form.contains("sniffer")) {
            return false; 
        }
        return true;
    }

    // Orijinal Minecraft mob blok kırma mesafeleri (BLOCK_REACH)
    public static double getOriginalMobBlockRange(String form) {
        form = form.toLowerCase();
        if (form.contains("human")) return 4.5D; 

        if (form.contains("warden")) return 3.0D;
        if (form.contains("iron_golem") || form.contains("ghast") || form.contains("wither") || form.contains("enderman")) {
            return 2.5D; 
        }

        if (form.contains("zombie") || form.contains("skeleton") || form.contains("piglin") || form.contains("villager")) {
            return 2.5D; 
        }

        if (form.contains("spider") || form.contains("cave_spider")) {
            return 2.0D;
        }

        if (form.contains("chicken") || form.contains("rabbit") || form.contains("silverfish") || form.contains("frog")) {
            return 1.2D; 
        }

        return 1.0D; 
    }

    // Orijinal Minecraft mob sol tık fiziksel vuruş mesafeleri (ENTITY_REACH)
    private static double getOriginalMobAttackRange(String form) {
        form = form.toLowerCase();
        if (form.contains("human")) return 3.0D; 

        if (form.contains("warden")) return 3.0D; 
        if (form.contains("iron_golem")) return 2.5D; 
        
        if (form.contains("zombie") || form.contains("skeleton") || form.contains("piglin") || form.contains("enderman")) {
            return 2.0D; 
        }

        if (form.contains("spider") || form.contains("cave_spider")) {
            return 2.0D;
        }

        if (form.contains("chicken") || form.contains("rabbit") || form.contains("frog") || form.contains("silverfish")) {
            return 1.0D; 
        }

        return 0.8D; 
    }

    public static void triggerFormAbility(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        if (form.contains("ghast")) {
            LargeFireball fireball = new LargeFireball(level, player, look.x, look.y, look.z, 1);
            fireball.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(fireball);
            player.sendSystemMessage(Component.literal("§c[!] Dev Güç: Büyük ateş topu fırlatıldı!"));
            
        } else if (form.contains("wither")) {
            WitherSkull skull = new WitherSkull(level, player, look.x, look.y, look.z);
            skull.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(skull);
            player.sendSystemMessage(Component.literal("§8[!] Kara Güç: Wither kafatası fırlatıldı!"));
            
        } else if (form.contains("warden")) {
            player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0D)).forEach(entity -> {
                if (entity != player) {
                    entity.hurt(player.damageSources().sonicBoom(player), 25.0F);
                }
            });
            player.sendSystemMessage(Component.literal("§4[!] Kadim Güç: Sonik çığlık etraftaki her şeyi sarstı!"));
        }
    }

    public static void handlePlayerDeath(ServerPlayer player) {
        String mob = currentMobForm.toLowerCase();
        if (!(mob.contains("slime") || mob.contains("magma_cube"))) {
            karmaBar = Math.max(0, karmaBar - 20);
        }
    }

    public static void handlePlayerRespawn(ServerPlayer player) {
        assignNewMob(player);
        applyFormSpawnLocation(player);
    }

    public static void assignNewMob(ServerPlayer player) {
        double roll = random.nextDouble() * 100;
        String chosen = (roll < karmaBar) ? getMobFromExactTier(karmaBar) : getLowerTierEqualShareMob(karmaBar);
        
        currentMobForm = chosen;
        player.sendSystemMessage(Component.literal("§e[!] Yeni Formunuz: §a" + chosen + " (Karma: %" + karmaBar + ")"));
        
        if (random.nextInt(100) < 15) {
            player.sendSystemMessage(Component.literal("§d[!] Şans eseri bebek formunda doğdunuz!"));
        }
        applyFormRestrictions(player);
        giveFormItems(player, chosen);
        
        player.refreshDimensions();
    }

    private static void giveFormItems(ServerPlayer player, String form) {
        form = form.toLowerCase();
        player.getInventory().clearOrCountMatchingItems(p -> p.getItem() == Items.GOAT_HORN, -1, player.inventoryMenu.getCraftSlots());

        if (hasSpecialAbility(form)) {
            ItemStack horn = new ItemStack(Items.GOAT_HORN);
            String instrument = "minecraft:ponder_goat_horn";
            String customName = "§eÖzel Yetenek";

            if (form.contains("warden")) {
                instrument = "minecraft:sing_goat_horn";
                customName = "§4[KADİM] Warden Çığlığı";
            } else if (form.contains("wither")) {
                instrument = "minecraft:yearn_goat_horn";
                customName = "§8[BELA] Wither Tahribatı";
            } else if (form.contains("ghast")) {
                instrument = "minecraft:seek_goat_horn";
                customName = "§c[YIKIM] Ghast Patlaması";
            }

            horn.getOrCreateTag().putString("instrument", instrument);
            horn.setHoverName(Component.literal(customName));
            
            player.getInventory().add(horn);
            player.sendSystemMessage(Component.literal("§a[!] Güçlü bir canavara dönüştünüz! " + customName + " envanterinize eklendi."));
        }
    }

    public static void applyFormRestrictions(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();

        if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
            double jumpPower = getMobJumpStrength(form);
            player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(jumpPower);
        }
        
        if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double originalDamage = canMobDealDamage(form) ? getOriginalMobDamage(form) : 0.0D;
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(originalDamage);
        }
        
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            double originalHealth = getOriginalMobHealth(form);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(originalHealth);
            player.setHealth((float) originalHealth);
        }

        double blockReach = getOriginalMobBlockRange(form);
        double entityReach = getOriginalMobAttackRange(form);
        
        if (player.getAttribute(ForgeMod.BLOCK_REACH.get()) != null) {
            player.getAttribute(ForgeMod.BLOCK_REACH.get()).setBaseValue(blockReach);
        }
        
        if (player.getAttribute(ForgeMod.ENTITY_REACH.get()) != null) {
            player.getAttribute(ForgeMod.ENTITY_REACH.get()).setBaseValue(entityReach);
        }
    }

    public static float getMobWidth(String form) {
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 0.9F;
        if (form.contains("iron_golem")) return 1.4F;
        if (form.contains("spider")) return 1.4F;
        if (form.contains("chicken") || form.contains("rabbit")) return 0.4F;
        if (form.contains("silverfish") || form.contains("endermite")) return 0.4F;
        return 0.6F;
    }

    public static float getMobHeight(String form) {
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 2.9F;
        if (form.contains("iron_golem")) return 2.7F;
        if (form.contains("enderman")) return 2.9F;
        if (form.contains("chicken")) return 0.7F;
        if (form.contains("rabbit")) return 0.5F;
        if (form.contains("silverfish")) return 0.3F;
        return 1.8F;
    }

    public static float getMobEyeHeight(String form) {
        if (form.contains("ghast")) return 2.0F;
        if (form.contains("warden")) return 2.6F;
        if (form.contains("iron_golem")) return 2.25F;
        if (form.contains("enderman")) return 2.55F;
        if (form.contains("chicken")) return 0.5F;
        return 1.62F;
    }

    public static void handleInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ServerPlayer targetPlayer) {
            String targetForm = currentMobForm.toLowerCase();
            
            if (targetForm.contains("horse") || targetForm.contains("donkey") || targetForm.contains("camel") || targetForm.contains("strider") || targetForm.contains("zombified_piglin")) {
                if (event.getEntity() instanceof ServerPlayer rider && rider != targetPlayer) {
                    rider.startRiding(targetPlayer);
                    rider.sendSystemMessage(Component.literal("§a[!] Oyuncunun sırtına bindiniz!"));
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }
            }
        }
    }

    public static void handleAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String form = currentMobForm.toLowerCase();
            LivingEntity target = event.getEntity();
            
            if (!canMobDealDamage(form)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§c[!] Bir " + form + " olarak fiziksel hasar veremezsiniz!"));
                return;
            }

            double distance = player.distanceTo(target);
            double maxRange = getOriginalMobAttackRange(form);

            if (distance > maxRange) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§c[!] Bu form ile en fazla " + maxRange + " blok mesafeye vurabilirsiniz!"));
            } else {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
            }
        }
    }

    public static boolean isInteractionRestricted(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        if (form.contains("villager") || form.contains("wandering_trader")) {
            return false;
        }
        return true;
    }

    public static boolean canEatFood(String form, String foodItem) {
        form = form.toLowerCase();
        foodItem = foodItem.toLowerCase();
        
        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish")) {
            return foodItem.contains("kelp") || foodItem.contains("yosun");
        }
        if (form.contains("villager")) {
            return foodItem.contains("bread") || foodItem.contains("ekmek");
        }
        return true; 
    }

    private static double getOriginalMobDamage(String form) {
        if (form.contains("warden")) return 30.0D; 
        if (form.contains("iron_golem")) return 15.0D; 
        if (form.contains("wither")) return 8.0D; 
        
        if (form.contains("zombie") || form.contains("piglin")) return 3.0D;
        if (form.contains("spider")) return 2.0D;
        if (form.contains("silverfish")) return 1.0D;

        return 2.0D; 
    }

    private static double getOriginalMobHealth(String form) {
        if (form.contains("warden")) return 500.0D;
        if (form.contains("iron_golem")) return 100.0D;
        return 20.0D;
    }

    public static void applyFormSpawnLocation(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        String form = currentMobForm.toLowerCase();

        BlockPos spawnPos = player.getRespawnPosition();
        if (spawnPos == null) {
            spawnPos = level.getSharedSpawnPos(); 
        }

        if (form.contains("strider")) {
            player.teleportTo(level, spawnPos.getX(), level.getSeaLevel(), spawnPos.getZ(), player.getYHeadRot(), player.getXRot());
        } else {
            player.teleportTo(level, spawnPos.getX(), spawnPos.getY() + 1, spawnPos.getZ(), player.getYHeadRot(), player.getXRot());
        }
    }

    public static String getMobFromExactTier(int karma) {
        List<String> tierList = getListForTier(karma);
        return tierList.get(random.nextInt(tierList.size()));
    }

    public static String getLowerTierEqualShareMob(int currentKarma) {
        List<String> allLowerMobs = new ArrayList<>();
        for (int t = 0; t < currentKarma; t += 10) {
            allLowerMobs.addAll(getListForTier(t));
        }
        if (allLowerMobs.isEmpty()) return "frog";
        return allLowerMobs.get(random.nextInt(allLowerMobs.size()));
    }

    private static List<String> getListForTier(int karma) {
        if (karma >= 100) return TIER_100;
        if (karma >= 90) return TIER_90;
        if (karma >= 80) return TIER_80;
        if (karma >= 70) return TIER_70;
        if (karma >= 60) return TIER_60;
        if (karma >= 50) return TIER_50;
        if (karma >= 40) return TIER_40;
        if (karma >= 30) return TIER_30;
        if (karma >= 20) return TIER_20;
        if (karma >= 10) return TIER_10;
        return TIER_0;
    }
}
