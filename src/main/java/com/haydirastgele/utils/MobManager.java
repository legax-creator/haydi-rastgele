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

import java.util.*;

public class MobManager {
    public static int karmaBar = 50;
    public static String currentMobForm = "human";
    private static final Random random = new Random();

    // Evrim ve adaptasyon sayaçları (Biyom ve Suda kalma takibi için)
    private static final Map<UUID, Integer> waterTicks = new HashMap<>();
    private static final Map<UUID, Integer> desertTicks = new HashMap<>();
    private static final Map<UUID, Integer> snowTicks = new HashMap<>();

    // --- KAOS GÖREV SİSTEMİ DEĞİŞKENLERİ ---
    public static String activeQuestType = "NONE"; 
    public static int questTimer = 0; 
    public static int nextQuestTriggerTicks = random.nextInt(36000); 
    private static int timeSinceLastQuestTicks = 0;

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

    // --- GLOBAL KISITLAMALAR VE TICK MOTORU ---
    public static void applyGlobalFormRestrictions(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        UUID uuid = player.getUUID();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // 1. ZIPLAMA YASAĞI VE STEP ASSIST
        if (!form.contains("rabbit")) {
            player.maxUpStep = 1.25F;
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0D);
            }
        } else {
            player.maxUpStep = 0.6F;
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.75D);
            }
        }

        // Örümceklerin duvara tırmanma mekaniği
        if (form.contains("spider") || form.contains("cave_spider")) {
            if (player.horizontalCollision) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.15D, motion.z);
            }
        }

        // 3. ENVANTER LOCKER MECHANIC & BARRIER LOCK
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (i != 0 && i != 1 && i != 9) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() != Items.BARRIER) {
                    player.getInventory().setItem(i, new ItemStack(Blocks.BARRIER));
                }
            }
        }

        // 4. ÇEVRE ETKİLEŞİMLERİ VE EVRİMLER
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

        // ZOMBİ EVRİMLERİ
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

        // İSKELET -> STRAY EVRİMİ
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
            LlamaSpit spit = new LlamaSpit(level, player);
            spit.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
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
    }

    public static float getMobWidth(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 0.9F;
        if (form.contains("iron_golem")) return 1.4F;
        if (form.contains("spider") || form.contains("cave_spider")) return 1.4F;
        if (form.contains("chicken") || form.contains("rabbit") || form.contains("salmon") || form.contains("cod")) return 0.4F;
        if (form.contains("silverfish")) return 0.4F;
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
        return 1.8F;
    }

    public static float getMobEyeHeight(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 2.0F;
        if (form.contains("warden")) return 2.6F;
        if (form.contains("iron_golem")) return 2.25F;
        if (form.contains("enderman")) return 2.55F;
        if (form.contains("chicken")) return 0.5F;
        return 1.62F;
    }

    public static void handleInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        String form = currentMobForm.toLowerCase();

        if (form.contains("frog") && event.getTarget().getType() == EntityType.MAGMA_CUBE) {
            event.getTarget().discard();
            player.getFoodData().setFoodLevel(20);
            player.sendSystemMessage(Component.literal("§a[!] Magma Küpü yiyerek açlığını fulledin!"));
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (form.contains("elder_guardian") && player.getMainHandItem().is(Items.GOAT_HORN)) {
            if (event.getTarget() instanceof LivingEntity targetEntity) {
                if (!targetEntity.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                    targetEntity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 1200, 2));
                    player.sendSystemMessage(Component.literal("§7[!] Hedefe Madenci Yorgunluğu verildi."));
                }
            }
            event.setCanceled(true);
            return;
        }

        if (event.getTarget() instanceof ServerPlayer targetPlayer) {
            if (form.contains("horse") || form.contains("donkey") || form.contains("camel") || form.contains("strider")) {
                if (event.getEntity() instanceof ServerPlayer rider && rider != targetPlayer) {
                    rider.startRiding(targetPlayer);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                }
            }
        }
    }

    public static void handleLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String form = currentMobForm.toLowerCase();
            LivingEntity target = event.getEntity();

            if (form.contains("wither_skeleton") || form.equals("wither")) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 200, 1));
            }

            if (form.contains("cave_spider") && random.nextInt(100) < 30) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 0));
            }

            if (form.contains("bee")) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 12000, 0));
                player.sendSystemMessage(Component.literal("§c[!] Birini soktun! 'Bunu Neden Yaptım?' efekti başladı. 10 dakika içinde öleceksin."));
            }
        }
    }

    public static void handleAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            String form = currentMobForm.toLowerCase();
            LivingEntity target = event.getEntity();
            
            if (!canMobDealDamage(form)) {
                event.setCanceled(true);
                return;
            }

            double distance = player.distanceTo(target);
            double maxRange = getOriginalMobAttackRange(form);

            if (distance > maxRange) {
                event.setCanceled(true);
            }
        }
    }

    public static boolean isBlockInteractionRestricted(ServerPlayer player, BlockPos pos) {
        String form = currentMobForm.toLowerCase();
        if (form.equals("human") || form.contains("villager") || form.contains("wandering_trader")) {
            return false;
        }
        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(pos);
        return state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST) || state.is(Blocks.BARREL) || 
               state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || 
               state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
    }

    public static boolean isInteractionRestricted(ServerPlayer player) {
        String form = currentMobForm.toLowerCase();
        if (form.equals("human")) return false;
        if (form.contains("villager") || form.contains("wandering_trader")) return false;
        return true;
    }

    public static boolean canEatFood(String form, String foodItem) {
        form = form.toLowerCase();
        foodItem = foodItem.toLowerCase();
        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish")) {
            return foodItem.contains("kelp") || foodItem.contains("seagrass");
        }
        if (form.contains("villager")) {
            return foodItem.contains("bread") || foodItem.contains("apple");
        }
        return true; 
    }

    private static double getOriginalMobDamage(String form) {
        form = form.toLowerCase();
        if (form.contains("warden")) return 30.0D; 
        if (form.contains("iron_golem")) return 15.0D; 
        if (form.contains("wither")) return 8.0D; 
        if (form.contains("zombie") || form.contains("piglin") || form.contains("husk") || form.contains("drowned")) return 3.0D;
        if (form.contains("spider") || form.contains("cave_spider")) return 2.0D;
        if (form.contains("silverfish")) return 1.0D;
        return 2.0D; 
    }

    private static double getOriginalMobAttackRange(String form) {
        form = form.toLowerCase();
        if (form.contains("human")) return 3.0D; 
        if (form.contains("warden")) return 3.0D; 
        if (form.contains("iron_golem")) return 2.5D; 
        if (form.contains("zombie") || form.contains("skeleton") || form.contains("piglin") || form.contains("enderman")) return 2.0D; 
        if (form.contains("spider") || form.contains("cave_spider")) return 2.0D;
        if (form.contains("chicken") || form.contains("rabbit") || form.contains("frog") || form.contains("silverfish")) return 1.0D;
        return 0.8D; 
    }

    private static double getOriginalMobBlockRange(String form) {
        form = form.toLowerCase();
        if (form.contains("human") || form.contains("villager")) return 4.5D;
        if (form.contains("warden") || form.contains("iron_golem") || form.contains("enderman")) return 4.0D;
        return 3.0D;
    }

    private static double getOriginalMobHealth(String form) {
        form = form.toLowerCase();
        if (form.contains("warden")) return 500.0D;
        if (form.contains("iron_golem")) return 100.0D;
        if (form.contains("wither") && !form.contains("skeleton")) return 300.0D;
        if (form.contains("elder_guardian")) return 80.0D;
        if (form.contains("enderman")) return 40.0D;
        return 20.0D;
    }

    public static void applyFormSpawnLocation(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        String form = currentMobForm.toLowerCase();
        BlockPos spawnPos = player.getRespawnPosition();
        if (spawnPos == null) spawnPos = level.getSharedSpawnPos(); 

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
