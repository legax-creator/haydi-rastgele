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
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderSet;
// import net.minecraft.data.worldgen.BuiltinStructures; -> KALDIRILDI: bu class datagen'e özel,
// normal (main) derlemede bulunmuyordu. Onun yerine aşağıda doğrudan ResourceKey.create + string ID kullanılıyor.
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.EntityDimensions;

import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobManager {
    public static int karmaBar = 50;
    // --- ÖNEMLİ MİMARİ DÜZELTME ---
    // Eskiden "currentMobForm" TEK bir global String'di - yani TÜM sunucudaki oyuncular aynı formu
    // paylaşıyordu (çoklu oyunculu senaryoda ciddi bir hataydı). Artık her oyuncunun formu kendi
    // UUID'siyle ayrı ayrı tutuluyor. Tek oyunculu oyunda hiçbir fark hissetmezsin (haritada sadece
    // 1 kayıt olur), ama çoklu oyunculuda artık her oyuncu gerçekten bağımsız kendi formuna sahip.
    private static final Map<UUID, String> playerForms = new HashMap<>();

    public static String getForm(UUID uuid) {
        return playerForms.getOrDefault(uuid, "human");
    }

    public static void setForm(UUID uuid, String form) {
        playerForms.put(uuid, form);
    }
    private static final Random random = new Random();

    private static final Map<UUID, Integer> waterTicks = new HashMap<>();
    private static final Map<UUID, Integer> desertTicks = new HashMap<>();
    private static final Map<UUID, Integer> snowTicks = new HashMap<>();

    // --- GÖREV MÜZİKLERİ KAYITLARI ---
    public static final net.minecraft.sounds.SoundEvent MUSIC_SALMON = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
            new net.minecraft.resources.ResourceLocation("haydirastgele", "salmon_quest_music"));
    public static final net.minecraft.sounds.SoundEvent MUSIC_SHEEP = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
            new net.minecraft.resources.ResourceLocation("haydirastgele", "sheep_quest_music"));
    public static final net.minecraft.sounds.SoundEvent MUSIC_WITHER = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(
            new net.minecraft.resources.ResourceLocation("haydirastgele", "wither_quest_music"));

    // --- KAOS GÖREV SİSTEMİ DEĞİŞKENLERİ ---
    // 30 dakika = 30 * 60 saniye * 20 tick = 36000 tick
    private static final int QUEST_INTERVAL_TICKS = 36000;
    public static String activeQuestType = "NONE"; 
    public static int questTimer = 0; 
    public static int nextQuestTriggerTicks = QUEST_INTERVAL_TICKS; 
    private static int timeSinceLastQuestTicks = 0;

    private static final List<String> TIER_0 = Arrays.asList("salmon", "cod", "villager", "strider", "frog");
    private static final List<String> TIER_10 = Arrays.asList("pufferfish", "silverfish", "horse", "donkey", "parrot", "sniffer", "wandering_trader", "bat", "bee");
    private static final List<String> TIER_20 = Arrays.asList("hoglin", "zombified_piglin", "piglin", "llama");
    private static final List<String> TIER_30 = Arrays.asList("zombie", "skeleton", "wither_skeleton");
    private static final List<String> TIER_40 = Arrays.asList("phantom", "camel", "axolotl", "magma_cube", "slime");
    private static final List<String> TIER_50 = Arrays.asList("chicken", "cow", "sheep", "mooshroom", "snow_golem", "pig", "rabbit", "wolf", "cat");
    private static final List<String> TIER_60 = Arrays.asList("ghast");
    private static final List<String> TIER_70 = Arrays.asList("spider", "cave_spider");
    private static final List<String> TIER_80 = Arrays.asList("enderman");
    private static final List<String> TIER_90 = Arrays.asList("iron_golem");
    private static final List<String> TIER_100 = Arrays.asList("warden", "wither", "elder_guardian");

    // --- MOB YETENEK/KISIT TABLOLARI ---
    // Vurma (melee saldırı) yapamayan, doğası gereği pasif/kaçan moblar
    private static final Set<String> CANNOT_ATTACK = Set.of(
            "chicken", "cow", "sheep", "pig", "rabbit", "horse", "donkey", "llama",
            "villager", "wandering_trader", "salmon", "cod", "pufferfish", "bat",
            "parrot", "snow_golem", "mooshroom", "sniffer", "camel", "axolotl", "frog", "strider", "cat"
    );

    // Gerçek/serbest uçuş yeteneği olan formlar (tam "mayfly")
    // Not: "wither" -> Wither Boss'u ifade eder, "wither_skeleton" ile karışmasın diye tam eşleşme (equals) kullanılıyor.
    private static final Set<String> FLYING_FORMS = Set.of("ghast", "wither", "bat", "bee", "parrot");

    // Ateşe/lavaya karşı tamamen bağışık formlar
    private static final Set<String> FIRE_IMMUNE_FORMS = Set.of("blaze", "strider");

    // Suda sınırsız nefes alabilen (boğulmayan) formlar
    // (salmon/cod/pufferfish ayrı bir mantıkla -tam tersi- zaten yönetiliyor, o yüzden burada değiller)
    private static final Set<String> WATER_BREATHING_FORMS = Set.of("drowned", "elder_guardian", "guardian", "axolotl", "frog");

    // Elle (space tuşuyla) zıplayabilen "yüksek zıplama" formları. Bunların dışındaki hiçbir mob
    // (insan hariç) manuel zıplayamaz, onun yerine bir engele çarpınca otomatik/AI tarzı ufak bir sıçrama yapar.
    private static final Set<String> HIGH_JUMP_FORMS = Set.of("rabbit", "goat", "spider", "cave_spider", "frog");

    // --- PHANTOM SÜZÜLME (ELYTRA GLIDE) SİSTEMİ ---
    private static final Map<UUID, Integer> phantomGlideTicks = new HashMap<>();
    private static final Map<UUID, Boolean> phantomWasOnGround = new HashMap<>();
    private static final int PHANTOM_MAX_GLIDE_TICKS = 300; // ~15 saniye kesintisiz süzülme sonra zorla iniş
    private static final double PHANTOM_BOUNCE_VELOCITY = 2.2D; // yaklaşık 30 blok yükseklik veren dikey hız

    // --- AİLE/TÜR BAZLI SALDIRMAZLIK SİSTEMİ ---
    // Bir mobun bu setlerden birine dahil olması, o türün "kendi ailesine" doğal olarak saldırmadığı anlamına gelir.
    private static final Set<String> FAMILY_UNDEAD = Set.of("zombie", "husk", "drowned", "zombified_piglin");
    private static final Set<String> FAMILY_SKELETON = Set.of("skeleton", "stray", "wither_skeleton");
    private static final Set<String> FAMILY_PIGLIN = Set.of("piglin", "zombified_piglin");
    private static final Set<String> FAMILY_SPIDER = Set.of("spider", "cave_spider");
    private static final Set<String> FAMILY_GUARDIAN = Set.of("elder_guardian", "guardian");

    // Oyuncu tarafından bir kez vurulmuş (tahrik edilmiş) mobların UUID'leri - bunlar artık misilleme yapabilir
    private static final Set<UUID> aggroedByPlayer = new HashSet<>();

    private static String familyOf(String form) {
        if (FAMILY_UNDEAD.contains(form)) return "undead";
        if (FAMILY_SKELETON.contains(form)) return "skeleton";
        if (FAMILY_PIGLIN.contains(form)) return "piglin";
        if (FAMILY_SPIDER.contains(form)) return "spider";
        if (FAMILY_GUARDIAN.contains(form)) return "guardian";
        if (form.equals("slime")) return "slime";
        if (form.equals("magma_cube")) return "magma_cube";
        if (form.equals("silverfish")) return "silverfish";
        return null; // ailesi/türdaşı olmayan tekil form
    }

    // Doğal av-avcı ilişkileri (aynı aileden olmasa da vanilla'da doğal düşmandır, bu istisnalar saldırmazlık kuralını geçersiz kılar)
    private static boolean isNaturalPredator(EntityType<?> mobType, String playerForm) {
        String family = familyOf(playerForm);
        if (mobType == EntityType.IRON_GOLEM && "undead".equals(family)) return true; // Iron Golem -> Zombi ailesi
        if (mobType == EntityType.WOLF && "skeleton".equals(family)) return true; // Kurt -> İskelet ailesi
        if (mobType == EntityType.PIGLIN && playerForm.equals("hoglin")) return true; // Piglin -> Hoglin
        return false;
    }

    // --- WARDEN / YARASA SES ALGILAMA SİSTEMİ ---
    public static volatile Vec3 lastSoundSourcePos = null;

    // --- DOĞAL SPAWN LOKASYON SİSTEMİ ---
    private static final Set<String> NETHER_FORMS = Set.of(
            "piglin", "zombified_piglin", "hoglin", "blaze", "strider", "wither_skeleton", "magma_cube", "ghast"
    );

    @SubscribeEvent
    public static void onPlayerSize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            String form = getForm(player.getUUID()).toLowerCase();
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
                nextQuestTriggerTicks = QUEST_INTERVAL_TICKS; 
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
            setForm(player.getUUID(), "salmon");
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Somon Balığısın ve etrafındaki tüm sular aniden kuruyor! 2 dakika hayatta kal!"));
            
            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    MUSIC_SALMON, net.minecraft.sounds.SoundSource.MUSIC, 1.0F, 1.0F);

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
            setForm(player.getUUID(), "sheep");
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Koyunsun ve etrafında 10 aç kurt belirdi! Koş ve kaç, 2 dakika hayatta kal!"));

            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    MUSIC_SHEEP, net.minecraft.sounds.SoundSource.MUSIC, 1.0F, 1.0F);

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
            setForm(player.getUUID(), "pufferfish");
            applyFormRestrictions(player);
            player.sendSystemMessage(Component.literal("§c§l[GÖREV BAŞLADI] §eBir Kirpi Balığısın ve yanına Wither çağrıldı! Patlamalardan 2 dakika kaç!"));

            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    MUSIC_WITHER, net.minecraft.sounds.SoundSource.MUSIC, 1.0F, 1.0F);

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
            karmaBar = Math.max(0, karmaBar - 5);
            player.sendSystemMessage(Component.literal("§c§l[ELENDİN!] §eMücadeleyi kaybettin. §4-5 Karma kaybettin."));
        }
        activeQuestType = "NONE";
        questTimer = 0;
        applyFormRestrictions(player);
    }

    // --- "HAYATTA KAL" GÖREVİ (pasif/kaçan moblar için) ---
    // Her 5 dakikada (6000 tick) bir: hayattaysa +3 Karma, öldüyse -2 Karma. Görev kendini yeniler.
    private static final Map<UUID, Integer> survivalTicks = new HashMap<>();
    private static final int SURVIVAL_TASK_INTERVAL = 6000; // 5 dakika

    public static void handlePlayerDeath(ServerPlayer player) {
        if (!activeQuestType.equals("NONE")) {
            completeQuest(player, false);
        }
        // Ölüm anında "Hayatta Kal" görevi aktifse (pasif/kaçan formdaysa) -2 Karma ver ve sayacı sıfırla
        if (CANNOT_ATTACK.contains(getForm(player.getUUID()).toLowerCase())) {
            karmaBar = Math.max(0, karmaBar - 2);
            player.sendSystemMessage(Component.literal("§c[GÖREV BAŞARISIZ] §7Hayatta Kal görevini tamamlayamadın: §4-2 Karma"));
        }
        survivalTicks.put(player.getUUID(), 0);
    }

    // Bu, GameEvents.onPlayerTick içinden her tick çağrılmalı
    public static void tickSurvivalTask(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
        UUID uuid = player.getUUID();
        if (!CANNOT_ATTACK.contains(form)) {
            survivalTicks.remove(uuid);
            return;
        }
        int ticks = survivalTicks.getOrDefault(uuid, 0) + 1;
        if (ticks >= SURVIVAL_TASK_INTERVAL) {
            karmaBar = Math.min(100, karmaBar + 3);
            player.sendSystemMessage(Component.literal("§a[GÖREV TAMAMLANDI] §7Hayatta Kal: §2+3 Karma"));
            ticks = 0;
        }
        survivalTicks.put(uuid, ticks);
    }

        public static void applyGlobalFormRestrictions(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
        UUID uuid = player.getUUID();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (form.contains("phantom")) {
            if (player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            }
        } else {
            if (!form.equals("human") && player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem() == Items.ELYTRA) {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        }

        if (form.contains("zombie") || form.equals("husk") || form.equals("drowned")) {
            if (player.hasEffect(MobEffects.HUNGER)) {
                player.removeEffect(MobEffects.HUNGER);
            }
        }

        if (form.contains("warden") || (form.contains("wither") && !form.contains("skeleton"))) {
            player.getFoodData().setFoodLevel(20);
        }

        if ((form.contains("skeleton") || form.equals("zombie") || form.equals("husk")) && level.isDay() && level.canSeeSky(pos)) {
            player.setSecondsOnFire(8);
        }

        // --- ZIPLAMA KISITLAMASI ---
        // "İnsan" ve HIGH_JUMP_FORMS listesindeki moblar elle (space tuşuyla) zıplayabilir.
        // Diğer tüm formlar manuel zıplayamaz; bunun yerine bir engele çarptıklarında
        // otomatik/AI tarzı ufak bir sıçrama yaparlar (aşağı adım/basamak da buna göre ayarlanır).
        if (form.equals("human") || HIGH_JUMP_FORMS.contains(form)) {
            player.setMaxUpStep(0.6F);
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(getManualJumpStrength(form));
            }
        } else {
            player.setMaxUpStep(1.0F);
            if (player.getAttribute(Attributes.JUMP_STRENGTH) != null) {
                player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.0D);
            }
            // Otomatik zıplama: bir engele/duvara çarparsa (ve yerdeyse) kendiliğinden hafif sıçrar
            if (player.horizontalCollision && player.onGround()) {
                Vec3 autoJumpMotion = player.getDeltaMovement();
                player.setDeltaMovement(autoJumpMotion.x, 0.42D, autoJumpMotion.z);
                player.hurtMarked = true;
            }
        }

        // Örümcek/Mağara Örümceği duvara tırmanma hissi (yukarıdaki genel zıplama mantığından bağımsız, ekstra)
        if (form.contains("spider") || form.contains("cave_spider")) {
            if (player.horizontalCollision) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.15D, motion.z);
            }
        }

        // --- PHANTOM: ELYTRA TARZI SÜZÜLME + İNİŞTE FIRLATMA ---
        if (form.equals("phantom")) {
            UUID puuid = player.getUUID();
            if (player.isFallFlying()) {
                int glideTicks = phantomGlideTicks.getOrDefault(puuid, 0) + 1;
                phantomGlideTicks.put(puuid, glideTicks);
                if (glideTicks > PHANTOM_MAX_GLIDE_TICKS) {
                    // Belirli bir süre süzüldükten sonra zorla indiriyoruz (creative uçuş değil)
                    player.stopFallFlying();
                    phantomGlideTicks.put(puuid, 0);
                }
            } else {
                phantomGlideTicks.put(puuid, 0);
            }

            boolean onGroundNow = player.onGround();
            boolean wasOnGround = phantomWasOnGround.getOrDefault(puuid, true);
            if (onGroundNow && !wasOnGround) {
                // Yere yeni temas etti -> 30 blok kadar yukarı fırlat
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, PHANTOM_BOUNCE_VELOCITY, motion.z);
                player.hurtMarked = true;
            }
            phantomWasOnGround.put(puuid, onGroundNow);
        }

        // --- SU ALTINDA SINIRSIZ NEFES ---
        if (WATER_BREATHING_FORMS.contains(form) && player.isInWater()) {
            player.setAirSupply(player.getMaxAirSupply());
        }

        // --- ATEŞE/LAVAYA TAM BAĞIŞIKLIK (performans: her tick değil, ~0.5 saniyede bir yenileniyor) ---
        if (FIRE_IMMUNE_FORMS.contains(form)) {
            if (player.tickCount % 10 == 0 || !player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 30, 0, false, false));
            }
        }

        // --- WARDEN: DİĞER MOBLARI KORKUTUP KAÇIRMA (performans: her tick değil, ~0.5 saniyede bir taranıyor) ---
        if (form.equals("warden") && player.tickCount % 10 == 0) {
            for (Mob nearbyMob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0D))) {
                if (nearbyMob.distanceToSqr(player) < 400.0D) {
                    Vec3 diff = nearbyMob.position().subtract(player.position());
                    if (diff.lengthSqr() > 0.0001D) {
                        Vec3 flee = diff.normalize().scale(0.4D);
                        nearbyMob.setDeltaMovement(nearbyMob.getDeltaMovement().add(flee.x, 0.05D, flee.z));
                        nearbyMob.hurtMarked = true;
                    }
                }
            }
        }

        // --- WARDEN: KÖRLÜK + TİTREŞİM (SES) ALGILAMA + OK ---
        // (Yarasa artık burada değil - Yarasa'nın kendi ayrı gri-dünya/sonar sistemi var, aşağıda.)
        if (form.equals("warden")) {
            if (player.tickCount % 10 == 0 || !player.hasEffect(MobEffects.BLINDNESS)) {
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 15, 0, false, false));
            }
            if (player.tickCount % 10 == 0) { // performans: ses taraması her tick değil, ~0.5 saniyede bir
                updateSoundDetection(player, level);
            }
        } else {
            lastSoundSourcePos = null;
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
            // Ateş bağışıklığı artık yukarıda FIRE_IMMUNE_FORMS ile her zaman uygulanıyor.
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
                    setForm(player.getUUID(), "drowned");
                    player.sendSystemMessage(Component.literal("§b[!] Suda çok kaldın ve BOĞUK formuna evrildin!"));
                    applyFormRestrictions(player);
                    waterTicks.put(uuid, 0);
                }
            } else if (level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("").contains("desert")) {
                desertTicks.put(uuid, desertTicks.getOrDefault(uuid, 0) + 1);
                if (desertTicks.get(uuid) > 1200) {
                    setForm(player.getUUID(), "husk");
                    player.sendSystemMessage(Component.literal("§6[!] Çölde çok kaldın ve HUSK formuna evrildin!"));
                    applyFormRestrictions(player);
                    desertTicks.put(uuid, 0);
                }
            }
        }

        if (form.equals("skeleton") && level.getBiome(pos).unwrapKey().map(key -> key.location().getPath()).orElse("").contains("snow")) {
            snowTicks.put(uuid, snowTicks.getOrDefault(uuid, 0) + 1);
            if (snowTicks.get(uuid) > 1200) {
                setForm(player.getUUID(), "stray");
                player.sendSystemMessage(Component.literal("§f[!] Karlı alanda donarak KUTUP İSKELETİ (STRAY) formuna evrildin!"));
                applyFormRestrictions(player);
                snowTicks.put(uuid, 0);
            }
        }
    }
    
    public static boolean hasSpecialAbility(String form) {
        form = form.toLowerCase();
        return form.contains("ghast") || form.contains("warden") || form.contains("wither") || 
               form.contains("enderman") || form.contains("llama") || form.contains("snow_golem") || form.contains("pufferfish")
               || form.equals("elder_guardian");
    }

    public static void triggerFormAbility(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
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
            // DÜZELTME: Artık kendine değil, etraftaki düşmanlara zehir/hasar veriyor
            level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.0D)).forEach(entity -> {
                if (entity != player) {
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                    entity.hurt(level.damageSources().magic(), 4.0F);
                }
            });
            player.getCooldowns().addCooldown(Items.GOAT_HORN, 120);
        } else if (form.equals("elder_guardian")) {
            // Gerçek vanilla lazer davranışına yakın: baktığın en yakın canlıya doğrudan hasar +
            // Madenci Yorgunluğu (Mining Fatigue) efekti verir (gerçek Elder Guardian'ın periyodik etkisi)
            HitResult hit = player.pick(16.0D, 0.0F, false);
            LivingEntity target = null;
            double closestDist = 16.0D;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16.0D))) {
                if (entity == player) continue;
                Vec3 toEntity = entity.position().subtract(player.getEyePosition()).normalize();
                double dot = toEntity.dot(look);
                double dist = entity.distanceTo(player);
                if (dot > 0.95D && dist < closestDist) { // bakış çizgisine yakın (dar bir "lazer" konisi)
                    target = entity;
                    closestDist = dist;
                }
            }
            if (target != null) {
                target.hurt(level.damageSources().indirectMagic(player, player), 8.0F);
                target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 2, false, false));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                        target.getX(), target.getY() + 1.0D, target.getZ(), 15, 0.3D, 0.5D, 0.3D, 0.02D);
            }
            player.getCooldowns().addCooldown(Items.GOAT_HORN, 60);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        String form = getForm(player.getUUID()).toLowerCase();
        ItemStack stack = event.getItemStack();
        
        if (form.contains("iron_golem") && stack.is(Items.IRON_INGOT)) {
            if (player.getFoodData().needsFood() || player.getHealth() < player.getMaxHealth()) {
                stack.shrink(1);
                player.getFoodData().eat(5, 0.8F);
                player.heal(12.0F);
                player.playSound(net.minecraft.sounds.SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 1.0F);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
        else if (form.contains("snow_golem") && (stack.is(Items.SNOWBALL) || stack.is(Blocks.ICE.asItem()) || stack.is(Blocks.PACKED_ICE.asItem()))) {
            if (player.getFoodData().needsFood()) {
                stack.shrink(1);
                player.getFoodData().eat(3, 0.3F);
                player.playSound(net.minecraft.sounds.SoundEvents.SNOW_BREAK, 1.0F, 1.2F);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void handleInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        String form = getForm(player.getUUID()).toLowerCase();

        if (form.contains("frog") && event.getTarget().getType() == EntityType.MAGMA_CUBE) {
            event.getTarget().discard();
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 2);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }

        // --- BİNME (RIDING) ---
        // Hedef, binilebilir bir moba (At/Eşek/Lama/Domuz) dönüşmüş başka bir oyuncuysa,
        // ve interactor eli boşken sağ tıklarsa, üstüne biner.
        if (event.getTarget() instanceof ServerPlayer targetPlayer && player instanceof ServerPlayer riderPlayer
                && targetPlayer != riderPlayer && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            String targetForm = getForm(targetPlayer.getUUID()).toLowerCase();
            if (RIDEABLE_FORMS.contains(targetForm) && riderPlayer.getVehicle() == null && targetPlayer.getPassengers().isEmpty()) {
                riderPlayer.startRiding(targetPlayer, true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }

    // Binilebilir formlar (basitleştirme: evcilleştirme/eyer şartı henüz kodlanmadı, doğrudan binilebiliyor)
    private static final Set<String> RIDEABLE_FORMS = Set.of("horse", "donkey", "llama", "pig");

    // --- GERÇEK WASD KONTROLÜ (yolcu, bindiği oyuncuyu kendi tuşlarıyla yönlendirir) ---
    // ⚠️ RİSK NOTU: "xxa"/"zza" alanları LivingEntity içinde PROTECTED olduğu için normal şekilde
    // erişilemiyor; burada Java reflection ile zorla erişiliyor. Bu, Mojang'ın resmi (official)
    // mapping'lerinde bu iki alanın GERÇEKTEN bu isimlerle var olduğu varsayımına dayanıyor - bu,
    // gerçek bir derleme ile doğrulanamadı. Eğer bu alan isimleri bu sürümde farklıysa, reflection
    // sessizce başarısız olur ve mount kendi normal (kontrolsüz) hareketine devam eder - oyunu
    // ÇÖKERTMEZ, sadece yönlendirme çalışmaz. Derlerken/oynarken çalışmazsa haber ver, alternatif
    // bir yöntem (örn. ServerboundPlayerInputPacket'i doğrudan dinlemek) deneriz.
    private static final java.lang.reflect.Field XXA_FIELD;
    private static final java.lang.reflect.Field ZZA_FIELD;
    private static final java.lang.reflect.Field JUMPING_FIELD;
    static {
        java.lang.reflect.Field xf = null, zf = null, jf = null;
        try {
            xf = LivingEntity.class.getDeclaredField("xxa");
            xf.setAccessible(true);
            zf = LivingEntity.class.getDeclaredField("zza");
            zf.setAccessible(true);
            jf = LivingEntity.class.getDeclaredField("jumping");
            jf.setAccessible(true);
        } catch (Exception ignored) {
            // Alan isimleri bulunamadı - WASD yönlendirme devre dışı kalır, mod yine de çalışmaya devam eder.
        }
        XXA_FIELD = xf;
        ZZA_FIELD = zf;
        JUMPING_FIELD = jf;
    }

    public static void applyRiderControlToMount(ServerPlayer rider, ServerPlayer mount) {
        if (XXA_FIELD == null || ZZA_FIELD == null) return;
        try {
            float strafe = XXA_FIELD.getFloat(rider);
            float forward = ZZA_FIELD.getFloat(rider);

            // Mount'un baktığı yönü sürücününkiyle eşitle (sürücü nereye bakarsa mount oraya döner)
            mount.setYRot(rider.getYRot());
            mount.setYHeadRot(rider.getYRot());
            mount.setXRot(rider.getXRot() * 0.5F); // dikey bakışın bir kısmı da yansısın (aşırıya kaçmadan)

            Vec3 inputVec = new Vec3(strafe, 0.0D, forward);
            if (inputVec.lengthSqr() > 0.0001D) {
                inputVec = inputVec.normalize();
            }
            Vec3 worldMotion = inputVec.yRot((float) -Math.toRadians(mount.getYRot()));
            double speed = getOriginalMobSpeed(getForm(mount.getUUID())) * 2.2D; // binek hissi için biraz hızlandırılmış

            mount.setDeltaMovement(worldMotion.x * speed, mount.getDeltaMovement().y, worldMotion.z * speed);
            mount.hurtMarked = true; // client'a senkronize et

            boolean isJumping = JUMPING_FIELD != null && JUMPING_FIELD.getBoolean(rider);
            if (isJumping && mount.onGround()) {
                mount.setDeltaMovement(mount.getDeltaMovement().x, 0.42D, mount.getDeltaMovement().z);
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    @SubscribeEvent
    public static void handleAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player player) {
            String form = getForm(player.getUUID()).toLowerCase();
            if (!canMobDealDamage(form)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void handleLivingHurt(LivingHurtEvent event) {
        // Oyuncu bir moba vurduysa, o mob artık "tahrik edilmiş" sayılır ve misilleme yapabilir
        if (event.getSource().getEntity() instanceof ServerPlayer && event.getEntity() instanceof Mob hitMob) {
            aggroedByPlayer.add(hitMob.getUUID());
        }
    }

    // Aynı aileden/türden moblar (tahrik edilmedikçe) birbirine saldırmaz.
    // Doğal av-avcı ilişkileri (örn. Iron Golem -> Zombi) bu kuralın istisnasıdır.
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewTarget() instanceof ServerPlayer targetPlayer)) return;
        if (!(event.getEntity() instanceof Mob mob)) return;

        if (aggroedByPlayer.contains(mob.getUUID())) return; // tahrik edilmiş, saldırmasına izin ver

        // Piglinler, altın zırh giyen (en az bir parça) oyuncuya doğal olarak saldırmaz
        String mobIdCheck = EntityType.getKey(mob.getType()).getPath();
        if (mobIdCheck.equals("piglin") && wearsAnyGoldArmor(targetPlayer)) {
            event.setCanceled(true);
            return;
        }

        String playerForm = getForm(targetPlayer.getUUID()).toLowerCase();
        String playerFamily = familyOf(playerForm);
        String mobId = EntityType.getKey(mob.getType()).getPath();
        String mobFamily = familyOf(mobId);

        boolean sameFamily = playerFamily != null && playerFamily.equals(mobFamily);
        boolean predator = isNaturalPredator(mob.getType(), playerForm);

        if (sameFamily && !predator) {
            event.setCanceled(true);
        }
    }

    public static boolean isInteractionRestricted(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
        return form.contains("salmon") || form.contains("cod") || form.contains("pufferfish") || form.contains("bat");
    }

    // Oyuncunun herhangi bir zırh parçasında altın (Golden Armor) olup olmadığını kontrol eder
    private static boolean wearsAnyGoldArmor(ServerPlayer player) {
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.is(Items.GOLDEN_HELMET) || piece.is(Items.GOLDEN_CHESTPLATE)
                    || piece.is(Items.GOLDEN_LEGGINGS) || piece.is(Items.GOLDEN_BOOTS)) {
                return true;
            }
        }
        return false;
    }

    // --- DETAYLI, MOB-ÖZEL BLOK KIRMA/YERLEŞTİRME YETENEK SİSTEMİ ---
    // Vanilla'da moblar neredeyse hiç blok kıramaz; burada gerçekçi birkaç istisna dışında
    // (insan hariç) hiçbir form serbestçe blok kıramaz/koyamaz.
    public static boolean canBreakBlock(String form, BlockState state) {
        form = form.toLowerCase();
        if (form.equals("human")) return true;

        // Wither: neredeyse her bloğu yok edebilir (patlamaz olanlar hariç)
        if (form.equals("wither")) {
            return !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER) && !state.is(Blocks.END_PORTAL_FRAME)
                    && !state.is(Blocks.OBSIDIAN) && !state.is(Blocks.CRYING_OBSIDIAN) && !state.is(Blocks.RESPAWN_ANCHOR)
                    && !state.is(Blocks.END_GATEWAY) && !state.is(Blocks.END_PORTAL) && !state.is(Blocks.COMMAND_BLOCK);
        }

        // Silverfish: sadece taş-türevi bloklara girebilir (infest davranışı)
        if (form.equals("silverfish")) {
            return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                    || state.is(Blocks.STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.CRACKED_STONE_BRICKS)
                    || state.is(Blocks.CHISELED_STONE_BRICKS) || state.is(Blocks.MOSSY_COBBLESTONE);
        }

        // Enderman: sadece taşıyabildiği (kazı benzeri) blok türlerini "kırabilir"
        if (form.equals("enderman")) {
            return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.SAND)
                    || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                    || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.PUMPKIN)
                    || state.is(Blocks.MELON) || state.is(BlockTags.LEAVES);
        }

        // Zombi ailesi: sadece ahşap kapıları kırabilir (vanilla: zor modda kapı kırma davranışı)
        if (FAMILY_UNDEAD.contains(form)) {
            return state.getBlock() instanceof DoorBlock && !state.is(Blocks.IRON_DOOR);
        }

        // Geri kalan TÜM diğer formlar (hayvanlar, balıklar, uçanlar, iskelet ailesi, örümcekler,
        // piglin/hoglin, golemler, blaze, slime, magma cube, warden vb.) hiçbir bloğu kıramaz/koyamaz.
        return false;
    }

    public static boolean isBlockInteractionRestricted(ServerPlayer player, BlockPos pos) {
        String form = getForm(player.getUUID()).toLowerCase();
        if (form.equals("human")) return false; 

        net.minecraft.world.level.block.state.BlockState state = player.serverLevel().getBlockState(pos);

        if (form.contains("chicken")) {
            return !(state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN));
        }
        if (form.contains("cow") || form.contains("sheep")) {
            return !state.is(Blocks.WHEAT);
        }
        if (form.contains("enderman")) {
            return !(state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER));
        }
        if (form.contains("iron_golem")) {
            return !(state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE) || state.is(Blocks.IRON_BLOCK) || state.is(Blocks.RAW_IRON_BLOCK));
        }
        if (form.contains("snow_golem")) {
            return !(state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE));
        }
        if (form.contains("bat") || form.contains("parrot")) {
            return !state.is(Blocks.SWEET_BERRY_BUSH);
        }
        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish")) {
            return !(state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT));
        }
        if (form.contains("villager") || form.contains("wandering_trader")) {
            // Köylü/Gezgin Tüccar kapı, kapak ve sandık açabilir
            return !(state.getBlock() instanceof DoorBlock
                    || state.is(BlockTags.WOODEN_TRAPDOORS)
                    || state.is(BlockTags.WOODEN_DOORS)
                    || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock);
        }

        return true; 
    }

    public static boolean canEatFood(String form, String foodName) {
        form = form.toLowerCase();
        foodName = foodName.toLowerCase();
        
        if (form.equals("human")) return true;
        if (form.contains("warden") || (form.contains("wither") && !form.contains("skeleton"))) return false;

        if (form.contains("cow") || form.contains("sheep")) {
            return foodName.contains("wheat") || foodName.contains("bread");
        }
        if (form.contains("chicken")) {
            return foodName.contains("seed");
        }
        if (form.contains("zombie") || form.equals("husk") || form.equals("drowned")) {
            return foodName.contains("rotten") || foodName.contains("beef") || foodName.contains("chicken") || foodName.contains("pork") || foodName.contains("mutton");
        }
        if (form.contains("spider") || form.contains("cave_spider")) {
            return foodName.contains("spider_eye") || foodName.contains("beef") || foodName.contains("chicken") || foodName.contains("mutton");
        }
        if (form.contains("piglin")) {
            return foodName.contains("gold") || foodName.contains("pork");
        }
        if (form.contains("enderman")) {
            return foodName.contains("chorus");
        }
        if (form.contains("bat") || form.contains("parrot")) {
            return foodName.contains("berry") || foodName.contains("melon") || foodName.contains("seed");
        }
        if (form.contains("salmon") || form.contains("cod") || form.contains("pufferfish")) {
            return foodName.contains("kelp");
        }
        
        return true; 
    }

    public static void handlePlayerRespawn(ServerPlayer player) {
        assignNewMob(player);
        applyFormSpawnLocation(player);
    }

    // Girdiğin tüm formları kaydeden basit "Form Günlüğü" (ödülsüz, sadece takip amaçlı)
    private static final Map<UUID, Set<String>> formLog = new HashMap<>();

    public static void assignNewMob(ServerPlayer player) {
        // /mod başlat sistemi: komutu hiç kullanan olmadıysa herkes eskisi gibi rastgele dönüşür.
        // Ama en az bir kişi komutu kullandıysa, SADECE komutu yazanlar rastgele moba dönüşür;
        // yazmayanlar "insan" formunda kalır (diğer tüm sistemlerden yine de etkilenmeye devam eder).
        if (!isModFullyActiveFor(player.getUUID())) {
            setForm(player.getUUID(), "human");
            return;
        }

        double roll = random.nextDouble() * 100;
        String chosen = (roll < karmaBar) ? getMobFromExactTier(karmaBar) : getLowerTierEqualShareMob(karmaBar);
        
        setForm(player.getUUID(), chosen);
        player.sendSystemMessage(Component.literal("§e[!] Yeni Formunuz: §a" + chosen.toUpperCase() + " (Tier Havuzundan)"));

        // Form Günlüğü'ne kaydet
        formLog.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(chosen);

        // Dönüşüm anı: forma özel ses + basit parçacık/flaş efekti
        playTransformSound(player, chosen);

        applyFormRestrictions(player);
        giveFormItems(player, chosen);
        player.refreshDimensions();
    }

    // Dönüşüm anında çalacak sesi forma göre seçer (mümkün olduğunca o mobun kendi vanilla sesi)
    private static void playTransformSound(ServerPlayer player, String form) {
        net.minecraft.sounds.SoundEvent sound;
        if (form.contains("zombie") || form.equals("husk") || form.equals("drowned")) sound = net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT;
        else if (form.contains("skeleton")) sound = net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;
        else if (form.equals("spider") || form.equals("cave_spider")) sound = net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
        else if (form.equals("enderman")) sound = net.minecraft.sounds.SoundEvents.ENDERMAN_AMBIENT;
        else if (form.equals("cow") || form.equals("mooshroom")) sound = net.minecraft.sounds.SoundEvents.COW_AMBIENT;
        else if (form.equals("sheep")) sound = net.minecraft.sounds.SoundEvents.SHEEP_AMBIENT;
        else if (form.equals("pig")) sound = net.minecraft.sounds.SoundEvents.PIG_AMBIENT;
        else if (form.equals("chicken")) sound = net.minecraft.sounds.SoundEvents.CHICKEN_AMBIENT;
        else if (form.equals("wolf")) sound = net.minecraft.sounds.SoundEvents.WOLF_AMBIENT;
        else if (form.equals("cat")) sound = net.minecraft.sounds.SoundEvents.CAT_AMBIENT;
        else if (form.equals("villager") || form.equals("wandering_trader")) sound = net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT;
        else if (form.equals("ghast")) sound = net.minecraft.sounds.SoundEvents.GHAST_AMBIENT;
        else if (form.equals("wither")) sound = net.minecraft.sounds.SoundEvents.WITHER_AMBIENT;
        else if (form.equals("warden")) sound = net.minecraft.sounds.SoundEvents.WARDEN_AMBIENT;
        else if (form.equals("piglin") || form.equals("zombified_piglin")) sound = net.minecraft.sounds.SoundEvents.PIGLIN_AMBIENT;
        else if (form.equals("blaze")) sound = net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT;
        else if (form.equals("bat")) sound = net.minecraft.sounds.SoundEvents.BAT_AMBIENT;
        else if (form.equals("iron_golem")) sound = net.minecraft.sounds.SoundEvents.IRON_GOLEM_STEP;
        else if (form.equals("human")) sound = net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP;
        else sound = net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT; // genel "dönüşüm" hissi veren varsayılan ses

        player.level().playSound(null, player.blockPosition(), sound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        // Basit görsel efekt: dönüşüm noktasında bir parçacık patlaması (client'a otomatik senkronize olur)
        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    player.getX(), player.getY() + 1.0D, player.getZ(), 25, 0.4D, 0.6D, 0.4D, 0.02D);
        }
    }

    public static Set<String> getFormLog(UUID uuid) {
        return formLog.getOrDefault(uuid, Collections.emptySet());
    }

    // /mob komutu için: tüm bilinen (tier havuzlarındaki) form isimlerinin birleşik listesi
    public static List<String> getAllKnownForms() {
        List<String> all = new ArrayList<>();
        all.addAll(TIER_0); all.addAll(TIER_10); all.addAll(TIER_20); all.addAll(TIER_30);
        all.addAll(TIER_40); all.addAll(TIER_50); all.addAll(TIER_60); all.addAll(TIER_70);
        all.addAll(TIER_80); all.addAll(TIER_90); all.addAll(TIER_100);
        all.add("human");
        return all;
    }

    // /mob <isim> komutu: rastgele seçim yapmadan, doğrudan istenen forma zorla geçiş
    // /mob komutu VE Form Günlüğü (O tuşu) ekranı için ortak 2 dakikalık bekleme süresi
    private static final Map<UUID, Long> lastFormChangeMillis = new HashMap<>();
    private static final long FORM_CHANGE_COOLDOWN_MS = 120_000L; // 2 dakika

    public static boolean forceSetForm(ServerPlayer player, String form) {
        form = form.toLowerCase();
        if (!getAllKnownForms().contains(form)) return false;

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = lastFormChangeMillis.get(uuid);
        if (last != null && (now - last) < FORM_CHANGE_COOLDOWN_MS) {
            long remainingSec = (FORM_CHANGE_COOLDOWN_MS - (now - last)) / 1000L;
            player.sendSystemMessage(Component.literal("§c[!] Form değiştirmek için beklemen gerekiyor: §e" + remainingSec + " sn"));
            return false;
        }
        lastFormChangeMillis.put(uuid, now);

        setForm(uuid, form);
        formLog.computeIfAbsent(uuid, u -> new HashSet<>()).add(form);
        playTransformSound(player, form);
        applyFormRestrictions(player);
        giveFormItems(player, form);
        player.refreshDimensions();
        player.sendSystemMessage(Component.literal("§e[/mob] Formunuz zorla değiştirildi: §a" + form.toUpperCase()));
        return true;
    }

    // --- /mod başlat SİSTEMİ ---
    // Çoklu oyunculu sunucuda modun kimler için "aktif" olduğunu tutar. Boşsa (hiç kimse /mod başlat
    // yazmadıysa) mod tek oyunculu senaryolarda olduğu gibi HERKES için aktif kabul edilir (geriye dönük uyumluluk).
    private static final Set<UUID> modActiveFor = new HashSet<>();
    private static boolean modActivationUsed = false;

    public static void activateModFor(ServerPlayer player) {
        modActivationUsed = true;
        modActiveFor.add(player.getUUID());
    }

    // Bir oyuncu için mod tamamen aktif mi (yani rastgele moba dönüşebilir mi)?
    public static boolean isModFullyActiveFor(UUID uuid) {
        if (!modActivationUsed) return true; // hiç kimse komutu kullanmadıysa eski (herkes aktif) davranış
        return modActiveFor.contains(uuid);
    }

    // Forma özel ölüm mesajı. Örn: "X (bir ZOMBI olarak) ... öldü"
    public static void applyCustomDeathMessage(net.minecraftforge.event.entity.living.LivingDeathEvent event, ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
        if (form.equals("human")) return; // insan formundaysa vanilla mesajı olduğu gibi kalsın

        Component original = player.getCombatTracker().getDeathMessage();
        Component custom = Component.literal("§7[bir " + form.toUpperCase() + " olarak] §f").append(original);
        for (net.minecraft.server.level.ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(custom);
        }
    }

    // --- GÖREV METİNLERİ (HUD'da sol üstte sarı kutuda gösterilecek) ---
    // NOT: Bu metinler şimdilik SADECE görsel/bilgilendirme amaçlıdır. Her mobun görevi puanla
    // (görev tamamlanırsa +5/-5, hayatta kalma +3/-2, üretim +3/0, çiftleşme +5/-10 vb.) tam olarak
    // OTOMATİK ölçülmüyor - bu, her mob için ayrı "davranış algılama" mantığı gerektiren çok büyük bir
    // sistem ve tek turda tamamlanamadı. Şu an sadece "Hayatta Kal" (5 dk) ve "Sağıl" (10 dk, sadece
    // inek/mooshroom) görevleri gerçekten puanlanıyor; diğerleri sadece bilgi amaçlı metin olarak duruyor.
    public static String getFormTaskText(String form) {
        switch (form) {
            case "zombie": case "husk": case "drowned": return "KÖYLÜLERE VE DEMİR GOLEMLERE SALDIR";
            case "skeleton": case "stray": return "UZAKTAN OK AT, GÜNEŞTEN KAÇ";
            case "wither_skeleton": return "NETHER KALESİNİ KORU, SALDIR";
            case "spider": return "GECE SALDIR, GÜNDÜZ SAKİN OL";
            case "cave_spider": return "HER ZAMAN SALDIR, ZEHİRLE";
            case "silverfish": return "TAŞA GİZLEN, PUSUYA DÜŞÜR";
            case "piglin": return "BASTION'U KORU, ALTINSIZLARA SALDIR";
            case "zombified_piglin": return "SAKİN KAL, SALDIRIYA MİSİLLEME YAP";
            case "hoglin": return "SALDIRGAN OL, PIGLIN'DEN KAÇ";
            case "ghast": return "UZAKTAN ATEŞ TOPU AT";
            case "blaze": return "NETHER KALESİNİ KORU, ATEŞ TOPU AT";
            case "wither": return "HER ŞEYE SALDIR, BLOK KIR";
            case "warden": return "SES DUY, EZİCİ GÜÇLE SALDIR";
            case "elder_guardian": return "OCEAN MONUMENT'İ KORU, LAZERLE SALDIR";
            case "slime": case "magma_cube": return "ZIPLAYARAK SALDIR";
            case "iron_golem": return "KÖYÜ KORU, SALDIRANA VUR";
            case "wolf": return "SAHİBİNİ KORU, SALDIRANA SALDIR";
            case "cat": return "CREEPER/PHANTOM KAÇIR, HEDİYE GETİR";
            case "cow": case "mooshroom": return "OTLA, SAĞILMAYA İZİN VER, KAÇ";
            case "sheep": return "OTLA, KIRKILMAYA İZİN VER, KAÇ";
            case "pig": return "OTLA, EYERLENMEYE İZİN VER";
            case "chicken": return "YUMURTLA, KAÇ";
            case "rabbit": return "KAÇ, SEBZE TARLASINA SALDIR";
            case "horse": case "donkey": return "EVCİLLEŞTİRİLMEYE İZİN VER";
            case "llama": return "KERVAN OLUŞTUR, TÜKÜREREK SAVUN";
            case "camel": return "SÜRÜLMEYE İZİN VER, YÜKSEK ZIPLA";
            case "villager": return "TİCARET YAP, GECE UYU";
            case "wandering_trader": return "GEZ, TİCARET YAP";
            case "salmon": case "cod": return "SUDA YÜZ, KAÇ";
            case "pufferfish": return "TEHDİT ALGILA, ŞİŞİP ZEHİRLE";
            case "frog": return "KÜÇÜK MOB YE";
            case "bee": return "ÇİÇEK TOZLA, KOVANA DÖN";
            case "parrot": return "OMUZDA OTUR, SES TAKLİT ET";
            case "bat": return "MAĞARADA AS, GECE UÇ";
            case "axolotl": return "SUDA AVLAN";
            case "strider": return "LAVDA YÜRÜ, SÜRÜLMEYE İZİN VER";
            case "sniffer": return "TOPRAĞI KOKLA, NADİR TOHUM BUL";
            case "snow_golem": return "KARTOPU AT";
            case "phantom": return "UYUMAYANLARIN ÜSTÜNDE UÇ, DALIŞ SALDIRISI";
            case "enderman": return "SAKİN KAL, GÖZ TEMASINDA SALDIR";
            default: return null; // insan / bilinmeyen form -> görev yok
        }
    }

    private static void giveFormItems(ServerPlayer player, String form) {
        form = form.toLowerCase();
        player.getInventory().clearContent();

        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.isArmor()) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
        }

        if (form.contains("phantom")) {
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        } else if (form.contains("skeleton") && !form.contains("wither")) {
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
        return !CANNOT_ATTACK.contains(form);
    }

    private static double getManualJumpStrength(String form) {
        switch (form) {
            case "rabbit": return 0.75D;
            case "goat": return 0.7D;
            case "frog": return 0.65D;
            case "spider":
            case "cave_spider": return 0.5D;
            default: return 0.42D; // insan / varsayılan vanilla zıplama gücü
        }
    }

    public static void applyFormRestrictions(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();

        // "wither" burada bilerek tam eşleşme (equals) ile kontrol ediliyor ki Wither Skeleton
        // ("wither_skeleton") bu koşula yanlışlıkla girip uçma yeteneği kazanmasın.
        if (FLYING_FORMS.contains(form)) {
            player.getAbilities().mayfly = true;
            if (form.equals("bat")) {
                player.getAbilities().setFlyingSpeed(0.09F);
            } else if (form.equals("bee") || form.equals("parrot")) {
                player.getAbilities().setFlyingSpeed(0.07F);
            } else {
                player.getAbilities().setFlyingSpeed(0.025F); 
            }
        } else {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.getAbilities().setFlyingSpeed(0.05F); 
        }
        player.getAbilities();

        if (form.contains("chicken")) {
            if (!player.hasEffect(MobEffects.SLOW_FALLING)) { // performans: efekt zaten aktifse tekrar paket gönderme
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Integer.MAX_VALUE, 0, false, false));
            }
        } else if (player.hasEffect(MobEffects.SLOW_FALLING)) {
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

        // Gerçek mob hızı (vanilla'daki movement_speed değerlerine yakın)
        if (player.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            player.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(getOriginalMobSpeed(form));
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

    public static float getMobWidth(String form) {
        form = form.toLowerCase();
        if (form.contains("ghast")) return 4.0F;
        if (form.contains("warden")) return 0.9F;
        if (form.contains("iron_golem")) return 1.4F;
        if (form.contains("spider") || form.contains("cave_spider")) return 1.4F;
        if (form.contains("chicken") || form.contains("rabbit") || form.contains("salmon") || form.contains("cod")) return 0.4F;
        if (form.contains("silverfish")) return 0.4F;
        if (form.contains("pig")) return 0.6F;
        if (form.equals("wolf")) return 0.6F;
        if (form.equals("cat")) return 0.6F;
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
        if (form.equals("wolf")) return 0.85F;
        if (form.equals("cat")) return 0.7F;
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
        if (form.equals("wolf")) return 0.68F;
        if (form.equals("cat")) return 0.35F;
        return 1.62F;
    }

    private static double getOriginalMobHealth(String form) {
        if (form.contains("warden")) return 500.0D;
        if (form.contains("wither")) return 300.0D;
        if (form.contains("iron_golem")) return 100.0D;
        if (form.contains("elder_guardian")) return 80.0D;
        if (form.contains("pig") || form.contains("chicken") || form.contains("salmon") || form.contains("cod") || form.contains("rabbit")) return 10.0D;
        if (form.equals("wolf")) return 40.0D; // evcil kurt gerçek can değeri (yabani 8'dir, evcilken 40 olur)
        if (form.equals("cat")) return 10.0D;
        return 20.0D;
    }

    private static double getOriginalMobDamage(String form) {
        if (form.contains("warden")) return 30.0D;
        if (form.contains("iron_golem")) return 15.0D;
        if (form.contains("wither_skeleton") || form.contains("spider")) return 4.0D;
        if (form.equals("wolf")) return 4.0D;
        if (form.equals("cat")) return 2.0D;
        return 2.0D;
    }

    // Vanilla'daki gerçek movement_speed temel değerlerine (yakın) karşılık gelen tablo.
    // İnsan varsayılanı 0.1D'dir; diğerleri kendi türlerine göre ayarlanıyor.
    private static double getOriginalMobSpeed(String form) {
        if (form.equals("human")) return 0.1D;
        if (form.contains("warden")) return 0.3D;
        if (form.contains("iron_golem")) return 0.25D;
        if (form.contains("enderman")) return 0.3D;
        if (form.contains("spider") || form.contains("cave_spider")) return 0.3D;
        if (form.contains("skeleton") || form.contains("stray") || form.contains("wither_skeleton")) return 0.25D;
        if (form.contains("zombie") || form.contains("husk") || form.contains("drowned")) return 0.23D;
        if (form.contains("chicken")) return 0.25D;
        if (form.contains("pig")) return 0.25D;
        if (form.contains("cow") || form.contains("mooshroom")) return 0.2D;
        if (form.contains("sheep")) return 0.23D;
        if (form.contains("rabbit")) return 0.3D;
        if (form.contains("horse") || form.contains("donkey")) return 0.225D;
        if (form.contains("llama")) return 0.175D;
        if (form.contains("camel")) return 0.09D;
        if (form.contains("wolf")) return 0.3D;
        if (form.contains("cat")) return 0.3D;
        if (form.contains("piglin") || form.contains("zombified_piglin")) return 0.25D;
        if (form.contains("hoglin")) return 0.3D;
        if (form.contains("blaze")) return 0.23D;
        if (form.contains("silverfish")) return 0.25D;
        if (form.contains("slime") || form.contains("magma_cube")) return 0.2D;
        if (form.contains("villager") || form.contains("wandering_trader")) return 0.3D;
        if (form.contains("strider")) return 0.175D;
        if (form.contains("frog")) return 0.1D;
        if (form.contains("axolotl")) return 0.1D;
        if (form.contains("sniffer")) return 0.1D;
        if (form.contains("bat")) return 0.6D; // uçuş hızı ayrı yönetiliyor, bu sadece kara hızı
        if (form.contains("parrot")) return 0.4D;
        return 0.1D;
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

    public static void applyFormSpawnLocation(ServerPlayer player) {
        String form = getForm(player.getUUID()).toLowerCase();
        if (form.equals("human")) return;

        boolean hasBed = player.getRespawnPosition() != null;
        // Yatak varsa %75 yatakta, yoksa (0% olduğu için) her zaman doğal konumda.
        boolean useBed = hasBed && random.nextDouble() * 100.0D < 75.0D;
        if (useBed) return; // Vanilla zaten oyuncuyu yatağına ışınlıyor; ekstra işlem yapmıyoruz.

        // ÖNEMLİ: Aşağıdaki hiçbir metot player.setRespawnPosition(...) ÇAĞIRMIYOR.
        // Bu yüzden burada seçilen konum, oyuncunun respawn/yatak noktası olarak KAYDEDİLMİYOR.
        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (NETHER_FORMS.contains(form)) {
            findNetherSpawn(server, player, form);
        } else if (form.equals("warden")) {
            findStructureSpawn(server, player, Level.OVERWORLD,
                    net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, new net.minecraft.resources.ResourceLocation("minecraft:ancient_city")), 100000);
        } else if (form.equals("elder_guardian")) {
            findStructureSpawn(server, player, Level.OVERWORLD,
                    net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, new net.minecraft.resources.ResourceLocation("minecraft:monument")), 10000);
        } else if (form.equals("villager") || form.equals("wandering_trader")) {
            findVillageSpawn(server, player);
        } else {
            findOverworldBiomeSpawn(server, player, form);
        }
    }

    // --- Nether mobları: boyut geçişi + gerçekçi (Bastion/açık biyom) oran yaklaşımı ---
    private static void findNetherSpawn(MinecraftServer server, ServerPlayer player, String form) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) return;

        net.minecraft.Util.backgroundExecutor().execute(() -> {
            BlockPos origin = new BlockPos((int) player.getX(), 64, (int) player.getZ());
            BlockPos found = null;

            // Piglin'in gerçek vanilla'da Bastion Kalıntısı içinde doğma olasılığı yaklaşık %25-30 civarıdır,
            // geri kalanı açık Nether biyomlarında doğar. Diğer Nether mobları için Bastion özel bir durum değildir.
            if (form.equals("piglin") && random.nextDouble() < 0.28D) {
                found = findNearestStructureByKey(nether,
                        net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, new net.minecraft.resources.ResourceLocation("minecraft:bastion_remnant")), origin, 10000);
            }

            if (found == null) {
                List<net.minecraft.resources.ResourceKey<Biome>> pool;
                if (form.equals("strider")) {
                    pool = List.of(Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.BASALT_DELTAS);
                } else if (form.equals("blaze") || form.equals("wither_skeleton")) {
                    pool = List.of(Biomes.NETHER_WASTES); // Nether Fortress genelde Nether Wastes/Soul Sand Valley'de oluşur, biyom yaklaşımı yeterli
                } else if (form.equals("hoglin")) {
                    pool = List.of(Biomes.CRIMSON_FOREST, Biomes.NETHER_WASTES);
                } else if (form.equals("magma_cube")) {
                    pool = List.of(Biomes.NETHER_WASTES, Biomes.BASALT_DELTAS, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.SOUL_SAND_VALLEY);
                } else {
                    // piglin / zombified_piglin / ghast -> tüm Nether biyomlarında eşit ağırlıklı, gerçekçi bir yaklaşım
                    pool = List.of(Biomes.NETHER_WASTES, Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.SOUL_SAND_VALLEY, Biomes.BASALT_DELTAS);
                }

                var pair = nether.findClosestBiome3d(
                        holder -> holder.unwrapKey().map(pool::contains).orElse(false),
                        origin, 6400, 32, 64
                );
                found = (pair != null) ? pair.getFirst() : origin;
            }

            teleportOnMainThread(server, player, nether, found);
        });
    }

    // --- Yapı bazlı moblar (Warden -> Ancient City, Elder Guardian -> Ocean Monument): gerçek yapı arama ---
    private static void findStructureSpawn(MinecraftServer server, ServerPlayer player, net.minecraft.resources.ResourceKey<Level> dimensionKey,
                                            net.minecraft.resources.ResourceKey<Structure> structureKey, int radius) {
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) return;

        net.minecraft.Util.backgroundExecutor().execute(() -> {
            BlockPos origin = player.blockPosition();
            BlockPos found = findNearestStructureByKey(level, structureKey, origin, radius);
            if (found == null) found = origin; // hiç bulunamazsa mevcut konumda kal
            teleportOnMainThread(server, player, level, found);
        });
    }

    private static BlockPos findNearestStructureByKey(ServerLevel level, net.minecraft.resources.ResourceKey<Structure> structureKey, BlockPos origin, int radius) {
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var holderOpt = registry.getHolder(structureKey);
        if (holderOpt.isEmpty()) return null;
        HolderSet<Structure> holderSet = HolderSet.direct(holderOpt.get());
        var pair = level.getChunkSource().getGenerator().findNearestMapStructure(level, holderSet, origin, radius, false);
        return (pair != null) ? pair.getFirst() : null;
    }

    // --- Köylü / Gezgin Tüccar: Köy yapısı arama (StructureTags ile daha basit/güvenilir yol) ---
    private static void findVillageSpawn(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        net.minecraft.Util.backgroundExecutor().execute(() -> {
            BlockPos origin = player.blockPosition();
            BlockPos found = overworld.findNearestMapStructure(StructureTags.VILLAGE, origin, 10000, false);
            if (found == null) found = origin;
            teleportOnMainThread(server, player, overworld, found);
        });
    }

    // --- Diğer tüm Overworld formları: gerçekçi doğal biyom havuzuna ışınlama ---
    private static void findOverworldBiomeSpawn(MinecraftServer server, ServerPlayer player, String form) {
        List<net.minecraft.resources.ResourceKey<Biome>> pool = getBiomePoolFor(form);
        if (pool.isEmpty()) return; // Bu formun net bir doğal biyom şartı yok (örn: İnsan yakınlarında her yerde doğan moblar), konumu değiştirme

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        net.minecraft.Util.backgroundExecutor().execute(() -> {
            BlockPos origin = player.blockPosition();
            var pair = overworld.findClosestBiome3d(
                    holder -> holder.unwrapKey().map(pool::contains).orElse(false),
                    origin, 10000, 32, 64
            );
            BlockPos found = (pair != null) ? pair.getFirst() : origin;
            teleportOnMainThread(server, player, overworld, found);
        });
    }

    private static void teleportOnMainThread(MinecraftServer server, ServerPlayer player, ServerLevel targetLevel, BlockPos pos) {
        server.execute(() -> player.teleportTo(targetLevel, pos.getX() + 0.5D, pos.getY() + 1, pos.getZ() + 0.5D, player.getYRot(), player.getXRot()));
    }

    // Her formun gerçek vanilla'ya en yakın doğal biyom havuzu (basit/eşit ağırlıklı yaklaşım).
    // Boş liste dönenler: vanilla'da net bir "doğal biyomu" olmayan formlar (Enderman, Silverfish, Phantom,
    // Sniffer -yumurtadan çıkar-, Iron/Snow Golem -doğal spawn'ı yok-, Wither vb.) -> konum değiştirilmez.
    private static List<net.minecraft.resources.ResourceKey<Biome>> getBiomePoolFor(String form) {
        switch (form) {
            case "husk": return List.of(Biomes.DESERT);
            case "stray": return List.of(Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA, Biomes.ICE_SPIKES, Biomes.FROZEN_PEAKS);
            case "salmon": return List.of(Biomes.RIVER, Biomes.FROZEN_RIVER);
            case "cod": return List.of(Biomes.OCEAN, Biomes.COLD_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_OCEAN);
            case "pufferfish": return List.of(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN);
            case "horse": case "donkey": return List.of(Biomes.PLAINS, Biomes.SAVANNA);
            case "parrot": return List.of(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);
            case "bee": return List.of(Biomes.PLAINS, Biomes.FLOWER_FOREST, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW);
            case "camel": return List.of(Biomes.DESERT);
            case "axolotl": return List.of(Biomes.LUSH_CAVES);
            case "frog": return List.of(Biomes.SWAMP, Biomes.MANGROVE_SWAMP);
            case "chicken": case "cow": case "sheep": case "pig": case "rabbit":
                return List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.SAVANNA, Biomes.SNOWY_PLAINS);
            case "llama": return List.of(Biomes.WINDSWEPT_HILLS, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU);
            case "mooshroom": return List.of(Biomes.MUSHROOM_FIELDS);
            case "spider": case "cave_spider": return List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.DESERT);
            case "skeleton": return List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.DESERT);
            case "zombie": return List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.SWAMP);
            case "silverfish": return List.of(Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_FOREST);
            case "sniffer": return List.of(Biomes.MEADOW, Biomes.PLAINS); // gerçek vanilla'da doğal spawn'ı yok (sadece yumurtadan), yaklaşık değer
            default:
                return List.of();
        }
    }

    // --- SES/TİTREŞİM ALGILAMA (Warden'ın gerçek Sculk Vibration sistemine yakın pratik bir yaklaşım) ---
    // Not: Ham vanilla GameEvent/Vibration sistemine (Sculk Sensor'ün kullandığı) doğrudan bir Forge event
    // hook'u bulunmadığı için, "Warden'a ses olarak giden" davranışları (hareket etme, hasar alma/verme,
    // blok kırma vb.) yakın bir yaklaşımla kendimiz tespit ediyoruz.
    private static void updateSoundDetection(ServerPlayer player, ServerLevel level) {
        double range = 30.0D; // Warden'ın gerçek algılama menzili duruma göre 16-90 blok arası değişir, ortalama bir değer
        LivingEntity loudest = null;
        double loudestScore = -1.0D;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
            if (entity == player) continue;

            boolean moving = entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D;
            boolean recentlyHurt = entity.hurtTime > 0;
            boolean recentlyDamaged = entity.getDeltaMovement().horizontalDistanceSqr() > 0.0025D; // hareket hızı - kaba bir "ses çıkarıyor" göstergesi

            if (!moving && !recentlyHurt && !recentlyDamaged) continue;

            double dist = Math.sqrt(entity.distanceToSqr(player));
            if (dist < 0.01D) dist = 0.01D;
            // Daha yakın VE daha "gürültülü" (vurulmuş/hareketli) olan öncelikli olsun
            double score = (recentlyHurt ? 100.0D : 0.0D) + (1.0D / dist) * 10.0D;

            if (score > loudestScore) {
                loudestScore = score;
                loudest = entity;
            }
        }

        lastSoundSourcePos = (loudest != null) ? loudest.position() : null;
    }
}
