package com.haydirastgele.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.EntityDimensions;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobManager {
    public static int karmaBar = 50;
    public static String currentMobForm = "human";
    private static final Random random = new Random();

    // Evrim ve adaptasyon sayaçları
    private static final Map<UUID, Integer> waterTicks = new HashMap<>();
    private static final Map<UUID, Integer> desertTicks = new HashMap<>();
    private static final Map<UUID, Integer> snowTicks = new HashMap<>();

    // --- KAOS GÖREV SİSTEMİ DEĞİŞKENLERİ ---
    public static String activeQuestType = "NONE"; 
    public static int questTimer = 0; 
    public static int nextQuestTriggerTicks = random.nextInt(36000); 
    private static int timeSinceLastQuestTicks = 0;

    // Birebir Senin İstediğin Liste
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

    // Hitbox ve Boyut Değişim Event'i
    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String form = currentMobForm.toLowerCase();
            float w = getMobWidth(form);
            float h = getMobHeight(form);
            event.setNewSize(EntityDimensions.fixed(w, h));
            event.setNewEyeHeight(getMobEyeHeight(form));
        }
    }

    public static void tickQuest(ServerPlayer player) {
        if (activeQuestType.equals("NONE")) {
            timeSinceLastQuestTicks++;
            if (timeSinceLastQuestTicks >= nextQuestTriggerTicks) {
                startRandomKaosQuest(player);
                timeSinceLastQuestTicks = 0;
                nextQuestTriggerTicks = random.nextInt(36000); 
            }
        } else {
            if (player.tickCount % 20 == 0) {
                questTimer--;
                if (questTimer % 10 == 0 || questTimer <= 10) {
                    player.displayClientMessage(Component.literal("§6[KAOS GÖREVİ] §eHayatta kal! Kalan Süre: §c" + questTimer + "s"), true);
                }
                if (questTimer <= 0) {
                    completeQuest(player, true); 
                }
            }
        }
    }

    private static void startRandomKaosQuest(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        int roll = random.nextInt(3); 
        questTimer = 120; 

        if (roll == 0) {
            activeQuestType = "SALMON_DRY";
            currentMobForm = "salmon";
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Somon Balığısın ve etrafındaki tüm sular aniden kuruyor! 2 dakika hayatta kal!"));
            
            BlockPos playerPos = player.blockPosition();
            for (int x = -4; x <= 4; x++) {
                for (int y = -3; y <= 3; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos targetPos = playerPos.offset(x, y, z);
                        if (level.getBlockState(targetPos).is(Blocks.WATER)) {
                            level.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        } else if (roll == 1) {
            activeQuestType = "SHEEP_WOLVES";
            currentMobForm = "sheep";
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Koyunsun ve etrafında 10 aç kurt belirdi! Koş ve kaç, 2 dakika hayatta kal!"));

            for (int i = 0; i < 10; i++) {
                Wolf wolf = EntityType.WOLF.create(level);
                if (wolf != null) {
                    double angle = i * (Math.PI * 2 / 10);
                    double spawnX = player.getX() + (Math.cos(angle) * 6);
                    double spawnZ = player.getZ() + (Math.sin(angle) * 6);
                    wolf.setPos(spawnX, player.getY() + 1, spawnZ);
                    wolf.setRemainingPersistentAngerTime(2400); 
                    wolf.setTarget(player);
                    level.addFreshEntity(wolf);
                }
            }
        } else {
            activeQuestType = "PUFFER_WITHER";
            currentMobForm = "pufferfish";
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Kirpi Balığısın ve yanına Wither çağrıldı! Patlamalardan 2 dakika kaç!"));

            WitherBoss wither = EntityType.WITHER.create(level);
            if (wither != null) {
                wither.setPos(player.getX() + 8, player.getY() + 3, player.getZ() + 8);
                wither.setTarget(player);
                level.addFreshEntity(wither);
            }
        }
    }

    public static void completeQuest(ServerPlayer player, boolean success) {
        if (activeQuestType.equals("NONE")) return;
        if (success) {
            karmaBar = Math.min(100, karmaBar + 10);
            player.sendSystemMessage(Component.literal("§a§l[BAŞARDIN!] §eKaostan canlı çıkmayı başardın! §d+10 Karma kazandın."));
        } else {
            karmaBar = Math.max(0, karmaBar - 10);
            player.sendSystemMessage(Component.literal("§c§l[ELENDİN!] §eMücadeleyi kaybettin. §4-10 Karma kaybettin."));
        }
        activeQuestType = "NONE";
        questTimer = 0;
        applyFormRestrictions(player);
    }

    public static void handlePlayerDeath(ServerPlayer player) {
        if (!activeQuestType.equals("NONE")) {
            completeQuest(player, false);
        }
    }

    public static void applyGlobalFormRestrictions(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        UUID uuid = player.getUUID();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if ((form.contains("skeleton") || form.equals("zombie") || form.equals("husk")) && level.isDay() && level.canSeeSky(pos)) {
            player.setSecondsOnFire(8);
        }

        if (!form.contains("rabbit")) {
            player.setMaxUpStep(1.25F);
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0D);
            }
        } else {
            player.setMaxUpStep(0.6F);
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.75D);
            }
        }

        if (form.contains("spider") || form.contains("cave_spider")) {
            if (player.horizontalCollision) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.15D, motion.z);
            }
        }

        if (!form.equals("human")) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (i != 0 && i != 1 && i != 9) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                        if (!stack.isEdible() && !(form.contains("skeleton") && (stack.getItem() == Items.BOW || stack.getItem() == Items.ARROW))) {
                            player.getInventory().setItem(i, new ItemStack(Items.BARRIER));
                        }
                    }
                }
            }
        }

        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish")) {
            if (!player.isInWater()) {
                player.setAirSupply(player.getAirSupply() - 1);
                if (player.getAirSupply() <= -20) {
                    player.hurt(level.damageSources().dryOut(), 2.0F);
                }
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
            } else {
                player.setAirSupply(player.getMaxAirSupply());
            }
        }

        if (form.contains("strider")) {
            if (level.getBlockState(pos.below()).is(Blocks.LAVA)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20, 0, false, false));
            }
            if (player.isInWaterOrRain()) {
                player.hurt(level.damageSources().drown(), 1.0F);
            }
        }

        if ((form.contains("enderman") || form.contains("blaze")) && player.isInWaterOrRain()) {
            player.hurt(level.damageSources().magic(), 1.0F);
        }

        if (form.equals("zombie")) {
            if (player.isInWater()) {
                waterTicks.put(uuid, waterTicks.getOrDefault(uuid, 0) + 1);
                if (waterTicks.get(uuid) > 600) { 
                    currentMobForm = "drowned";
                    player.sendSystemMessage(Component.literal("§b[!] Suda çok kaldın ve BOĞUK formuna evrildin!"));
                    applyFormRestrictions(player);
                    waterTicks.put(uuid, 0);
                }
            } else if (level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("").contains("desert")) {
                desertTicks.put(uuid, desertTicks.getOrDefault(uuid, 0) + 1);
                if (desertTicks.get(uuid) > 1200) {
                    currentMobForm = "husk";
                    player.sendSystemMessage(Component.literal("§6[!] Çölde çok kaldın ve HUSK formuna evrildin!"));
                    applyFormRestrictions(player);
                    desertTicks.put(uuid, 0);
                }
            }
        }

        if (form.equals("skeleton") && level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("").contains("snow")) {
            snowTicks.put(uuid, snowTicks.getOrDefault(uuid, 0) + 1);
            if (snowTicks.get(uuid) > 1200) {
                currentMobForm = "stray";
                player.sendSystemMessage(Component.literal("§f[!] Karlı alanda donarak KUTUP İSKELETİ (STRAY) formuna evrildin!"));
                applyFormRestrictions(player);
                snowTicks.put(uuid, 0);
            }
        }
    }
        public static boolean hasSpecialAbility(String form) {
        form = form.toLowerCase();
        return form.contains("ghast") || form.contains("warden") || form.contains("wither") || 
               form.contains("enderman") || form.contains("llama") || form.contains("snow_golem") || form.contains("pufferfish");
    }

    public static void triggerFormAbility(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        if (form.contains("ghast")) {
            LargeFireball fireball = new LargeFireball(level, player, look.x, look.y, look.z, 1);
            fireball.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(fireball);
        } else if (form.contains("wither") && !form.contains("skeleton")) {
            WitherSkull skull = new WitherSkull(level, player, look.x, look.y, look.z);
            skull.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(skull);
        } else if (form.contains("warden")) {
            player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0D)).forEach(entity -> {
                if (entity != player) entity.hurt(player.damageSources().sonicBoom(player), 25.0F);
            });
        } else if (form.contains("enderman")) {
            HitResult hit = player.pick(20.0D, 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = ((BlockHitResult) hit).getBlockPos().above();
                player.teleportTo(targetPos.getX(), targetPos.getY(), targetPos.getZ());
                player.getCooldowns().addCooldown(Items.GOAT_HORN, 200);
            }
        } else if (form.contains("llama")) {
            LlamaSpit spit = new LlamaSpit(EntityType.LLAMA_SPIT, level);
            spit.setOwner(player);
            spit.shoot(look.x, look.y, look.z, 1.5F, 1.0F);
            spit.setPos(player.getX(), player.getEyeY(), player.getZ());
            level.addFreshEntity(spit);
            player.getCooldowns().addCooldown(Items.GOAT_HORN, 20);
        } else if (form.contains("snow_golem")) {
            Snowball snowball = new Snowball(level, player);
            snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(snowball);
            player.getCooldowns().addCooldown(Items.GOAT_HORN, 20);
        } else if (form.contains("pufferfish")) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.0D)).forEach(entity -> {
                if (entity != player) entity.hurt(level.damageSources().magic(), 4.0F);
            });
            player.getCooldowns().addCooldown(Items.GOAT_HORN, 120);
        }
    }

    public static void handlePlayerInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        String form = currentMobForm.toLowerCase();

        if (form.contains("frog") && event.getTarget().getType() == EntityType.MAGMA_CUBE) {
            event.getTarget().discard();
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 2);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
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
        player.sendSystemMessage(Component.literal("§e[!] Yeni Formunuz: §a" + chosen.toUpperCase() + " (Tier Havuzundan)"));
        
        applyFormRestrictions(player);
        giveFormItems(player, chosen);
        player.refreshDimensions();
    }

    private static void giveFormItems(ServerPlayer player, String form) {
        form = form.toLowerCase();
        player.getInventory().clearContent();

        if (form.contains("skeleton") && !form.contains("wither")) {
            ItemStack bow = new ItemStack(Items.BOW);
            bow.enchant(Enchantments.INFINITY_ARROWS, 1);
            player.getInventory().setItem(0, bow);
            player.getInventory().setItem(9, new ItemStack(Items.ARROW));
        } else if (form.contains("wither_skeleton")) {
            player.getInventory().setItem(0, new ItemStack(Items.STONE_SWORD));
        }

        if (hasSpecialAbility(form)) {
            ItemStack horn = new ItemStack(Items.GOAT_HORN);
            horn.setHoverName(Component.literal("§e[YETENEK] Keçi Boynuzu"));
            player.getInventory().setItem(1, horn);
        }
    }

    public static boolean canMobDealDamage(String form) {
        form = form.toLowerCase();
        return !form.contains("chicken") && !form.contains("cow") && !form.contains("sheep") && 
               !form.contains("pig") && !form.contains("rabbit") && !form.contains("salmon") && 
               !form.contains("cod") && !form.contains("villager") && !form.contains("bat");
    }

    public static void applyFormRestrictions(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();

        if (form.contains("bat") || form.contains("phantom") || form.contains("ghast") || form.contains("wither")) {
            player.getAbilities().mayfly = true;
        } else {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();

        if (form.contains("chicken")) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
        } else {
            player.removeEffect(MobEffects.SLOW_FALLING);
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

        player.refreshDimensions();
    }

    // --- BOYUT METOTLARI ---
    public static float getMobWidth(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 0.9F;
        if (form.contains("iron_golem")) return 1.4F;
        if (form.contains("spider") || form.contains("cave_spider")) return 1.4F;
        if (form.contains("chicken") || form.contains("rabbit") || form.contains("salmon") || form.contains("cod")) return 0.4F;
        if (form.contains("silverfish")) return 0.4F;
        if (form.contains("pig")) return 0.6F;
        return 0.6F;
    }

    public static float getMobHeight(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 2.9F;
        if (form.contains("iron_golem")) return 2.7F;
        if (form.contains("enderman")) return 2.9F;
        if (form.contains("chicken")) return 0.7F;
        if (form.contains("rabbit")) return 0.5F;
        if (form.contains("silverfish") || form.contains("salmon") || form.contains("cod")) return 0.3F;
        if (form.contains("pig") || form.contains("spider") || form.contains("cave_spider")) return 0.9F;
        return 1.8F;
    }

    public static float getMobEyeHeight(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 2.0F;
        if (form.contains("warden")) return 2.6F;
        if (form.contains("iron_golem")) return 2.25F;
        if (form.contains("enderman")) return 2.55F;
        if (form.contains("chicken")) return 0.5F;
        if (form.contains("pig")) return 0.8F;
        return 1.62F;
    }

    // --- YARDIMCI METOTLAR VE UYUMLULUK KATMANI ---
    private static double getOriginalMobHealth(String form) {
        if (form.contains("warden")) return 500.0D;
        if (form.contains("wither")) return 300.0D;
        if (form.contains("iron_golem")) return 100.0D;
        if (form.contains("elder_guardian")) return 80.0D;
        if (form.contains("pig") || form.contains("chicken") || form.contains("salmon") || form.contains("cod") || form.contains("rabbit")) return 10.0D;
        return 20.0D;
    }

    private static double getOriginalMobDamage(String form) {
        if (form.contains("warden")) return 30.0D;
        if (form.contains("iron_golem")) return 15.0D;
        if (form.contains("wither_skeleton") || form.contains("spider")) return 4.0D;
        return 2.0D;
    }

    private static double getOriginalMobBlockRange(String form) { 
        return 4.5D; 
    }
    
    private static double getOriginalMobAttackRange(String form) { 
        return 3.0D; 
    }

    private static String getMobFromExactTier(int karma) {
        List<String> pool = getTierPool(karma);
        return pool.get(random.nextInt(pool.size()));
    }

    private static String getLowerTierEqualShareMob(int karma) {
        int targetKarma = Math.max(0, karma - 10);
        List<String> pool = getTierPool(targetKarma);
        return pool.get(random.nextInt(pool.size()));
    }

    private static List<String> getTierPool(int karma) {
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

    private static void applyFormSpawnLocation(ServerPlayer player) {
        // İhtiyaca göre doğma konumu mantığı eklenebilir.
     }
  }     
