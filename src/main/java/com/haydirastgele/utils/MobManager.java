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
import net.minecraft.world.phys.Vec3;
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

    public static void triggerFormAbility(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        ServerLevel level = (ServerLevel) player.level();

        if (form.contains("ghast")) {
            Vec3 look = player.getLookAngle();
            LargeFireball fireball = new LargeFireball(level, player, look.x, look.y, look.z, 1);
            fireball.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(fireball);
            player.sendSystemMessage(Component.literal("§c[!] Ateş topu fırlattınız!"));
        } else if (form.contains("warden")) {
            player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10.0D)).forEach(entity -> {
                if (entity != player) {
                    entity.hurt(player.damageSources().sonicBoom(player), 20.0F);
                }
            });
            player.sendSystemMessage(Component.literal("§4[!] Sonik Patlama gerçekleşti!"));
        } else {
            player.sendSystemMessage(Component.literal("§e[!] Bu formun özel bir yeteneği yok."));
        }
    }

    public static void handlePlayerDeath(ServerPlayer player) {
        String mob = currentMobForm.toLowerCase();
        if (!(mob.contains("slime") || mob.contains("magma_cube"))) {
            karmaBar = Math.max(0, karmaBar - 20);
            assignNewMob(player);
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
    }

    public static void applyFormRestrictions(ServerPlayer player) {
        if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
            player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0D);
        }
        if (player.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            double originalDamage = getOriginalMobDamage(currentMobForm.toLowerCase());
            player.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(originalDamage);
        }
        if (player.getAttribute(Attributes.MAX_HEALTH) != null) {
            double originalHealth = getOriginalMobHealth(currentMobForm.toLowerCase());
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(originalHealth);
            player.setHealth((float) originalHealth);
        }
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
            
            if (targetForm.contains("villager") || targetForm.contains("wandering_trader")) {
                if (event.getEntity() instanceof ServerPlayer customer && customer != targetPlayer) {
                    customer.sendSystemMessage(Component.literal("§6[!] " + targetPlayer.getName().getString() + " ile otomatik takas arayüzü açılıyor..."));
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

    public static boolean shouldMobIgnorePlayer(LivingEntity mob, ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        String mobName = mob.getType().getDescriptionId().toLowerCase();
        
        if ((form.contains("villager") || form.contains("cow") || form.contains("sheep") || form.contains("pig")) 
            && (mobName.contains("cow") || mobName.contains("sheep") || mobName.contains("pig"))) {
            return true;
        }
        return false;
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
        
        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish") || form.contains("elder_guardian")) {
            return foodItem.contains("kelp") || foodItem.contains("yosun");
        }
        if (form.contains("villager") || form.contains("wandering_trader")) {
            return foodItem.contains("bread") || foodItem.contains("ekmek");
        }
        if (form.contains("strider")) {
            return foodItem.contains("crimson_roots") || foodItem.contains("sarmaşık");
        }
        if (form.contains("frog")) {
            return foodItem.contains("fish") || foodItem.contains("balık");
        }
        if (form.contains("silverfish")) {
            return foodItem.contains("stone") || foodItem.contains("taş");
        }
        if (form.contains("horse") || form.contains("donkey")) {
            return foodItem.contains("carrot") || foodItem.contains("apple") || foodItem.contains("elma");
        }
        if (form.contains("parrot") || form.contains("chicken")) {
            return foodItem.contains("seeds") || foodItem.contains("tohum");
        }
        if (form.contains("bat") || form.contains("bee")) {
            return foodItem.contains("flower") || foodItem.contains("Çimen");
        }
        if (form.contains("hoglin") || form.contains("zombified_piglin") || form.contains("piglin")) {
            return foodItem.contains("carrot") || foodItem.contains("havuç");
        }
        if (form.contains("zombie") || form.contains("skeleton") || form.contains("wither_skeleton")) {
            return foodItem.contains("meat") || foodItem.contains("et");
        }
        if (form.contains("spider")) {
            return foodItem.contains("string") || foodItem.contains("ip");
        }
        if (form.contains("enderman")) {
            return foodItem.contains("warped_fungus");
        }
        if (form.contains("iron_golem")) {
            return foodItem.contains("iron") || foodItem.contains("demir");
        }
        if (form.contains("sniffer")) {
            return foodItem.contains("torchflower") || foodItem.contains("tohum");
        }
        
        return false;
    }

    private static double getOriginalMobDamage(String form) {
        if (form.contains("warden")) return 30.0D; 
        if (form.contains("iron_golem")) return 15.0D; 
        if (form.contains("wither")) return 8.0D; 
        if (form.contains("hoglin")) return 6.0D; 
        if (form.contains("enderman")) return 7.0D;
        if (form.contains("zombie") || form.contains("spider")) return 3.0D;
        if (form.contains("skeleton")) return 4.0D;
        return 2.0D; 
    }

    private static double getOriginalMobHealth(String form) {
        if (form.contains("warden")) return 500.0D;
        if (form.contains("iron_golem")) return 100.0D;
        if (form.contains("wither")) return 300.0D;
        if (form.contains("zombie") || form.contains("skeleton")) return 20.0D;
        return 20.0D;
    }

    private static double getOriginalMobAttackRange(String form) {
        if (form.contains("iron_golem")) return 2.0D; 
        if (form.contains("warden")) return 4.5D; 
        if (form.contains("ghast") || form.contains("wither")) return 15.0D; 
        if (form.contains("spider") || form.contains("cave_spider")) return 3.0D; 
        if (form.contains("hoglin") || form.contains("zombie") || form.contains("skeleton")) return 3.0D;
        return 2.5D; 
    }

    public static void applyFormSpawnLocation(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        String form = currentMobForm.toLowerCase();

        if (form.contains("strider") || form.contains("hoglin") || form.contains("piglin")) {
            player.teleportTo(level, pos.getX(), level.getSeaLevel(), pos.getZ(), player.getYHeadRot(), player.getXRot());
        } else if (form.contains("phantom") && level.isDay()) {
            player.sendSystemMessage(Component.literal("§c[!] Güneş altındasınız, hareket hızınız sınırlandı!"));
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
                                 
