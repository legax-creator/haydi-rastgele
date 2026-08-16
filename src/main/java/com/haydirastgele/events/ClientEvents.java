package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

// 'value = Dist.CLIENT' sayesinde bu sınıf sadece oyuncunun bilgisayarında yüklenir, sunucuyu çökertmez.
@Mod.EventBusSubscriber(modid = "haydirastgele", value = Dist.CLIENT)
public class ClientEvents {

    // Mobların çizim (render) kopyalarını sürekli sıfırdan yaratıp oyunu kastırmamak için burada önbelleğe alıyoruz
    private static final Map<String, Entity> renderEntityCache = new HashMap<>();

    // --- FORM GÜNLÜĞÜ TUŞU (varsayılan: O) ---
    public static final KeyMapping FORM_LOG_KEY = new KeyMapping(
            "key.haydirastgele.formlog", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_O), "key.categories.haydirastgele");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(FORM_LOG_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (FORM_LOG_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new FormLogScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = MobManager.getForm(player.getUUID()).toLowerCase();

        // Eğer oyuncu insan formunda değilse Steve/Alex modelini gizleyip yerine mob modelini çizeriz
        if (!form.equals("human")) {
            event.setCanceled(true); // Normal insan modelini çizme, iptal et!
            
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            
            // Performans için mobu önbellekten çek, yoksa oluştur
            Entity mobEntity = renderEntityCache.computeIfAbsent(form, f -> {
                EntityType<?> type = EntityType.byString(f).orElse(EntityType.PIG);
                return type.create(player.level());
            });
            
            if (mobEntity != null) {
                // Mobun konumunu ve bakış açılarını oyuncununkiyle birebir eşitle
                mobEntity.setPos(player.getX(), player.getY(), player.getZ());
                mobEntity.setYRot(player.getYRot());
                mobEntity.setXRot(player.getXRot());
                mobEntity.setYHeadRot(player.getYHeadRot());
                mobEntity.yRotO = player.yRotO;
                mobEntity.xRotO = player.xRotO;
                
                // Mobun orijinal göz yüksekliğine göre kamera açısını ve model konumunu hafifçe dengeler
                float eyeHeightOffset = mobEntity.getEyeHeight() - player.getEyeHeight();
                
                // Mobu tam oyuncunun olduğu yere çiz
                event.getPoseStack().pushPose();
                // Küçük mobların (tavuk, somon vb.) zemine sıfırlanması için konum kaydırma uyguluyoruz
                event.getPoseStack().translate(0, Math.min(0, eyeHeightOffset), 0);
                
                dispatcher.render(
                    mobEntity, 
                    0, 0, 0, 
                    player.getYRot(), 
                    event.getPartialTick(), 
                    event.getPoseStack(), 
                    event.getMultiBufferSource(), 
                    event.getPackedLight()
                );
                event.getPoseStack().popPose();
            }
        }
    }

    // --- EL GİZLEME (FIRST-PERSON HAND RENDER) ---
    // Bir moba dönüşmüşken 1. şahıs görünümünde hiçbir el/kol çizilmesin istendiği için
    // burada artık mob modeli el yerine çizilmiyor, event sadece iptal ediliyor (tamamen gizleniyor).
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String form = MobManager.getForm(mc.player.getUUID()).toLowerCase();

        if (!form.equals("human")) {
            event.setCanceled(true); // Standart Steve/Alex elini (veya başka bir mob modelini) hiç render etme!
        }
    }

    // --- WARDEN / YARASA: SES YÖNÜ OKU (HUD) ---
    // Not: Bu event/API adları (RenderGuiOverlayEvent, VanillaGuiOverlay, GuiGraphics.pose(), Axis)
    // Forge 1.20.1 için doğru olmalı, ama gerçek bir derleme ile test edilemedi. Eğer derlerken
    // bu metotlardan biri bulunamazsa (IDE'nin önerdiği) en yakın eşdeğerine güncellemen yeterli olur.
    @SubscribeEvent
    public static void onRenderCrosshair(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String form = MobManager.getForm(mc.player.getUUID()).toLowerCase();
        if (!form.equals("warden")) return; // Ok artık sadece Warden'da - Yarasa'nın kendi ayrı sistemi var

        net.minecraft.world.phys.Vec3 source = MobManager.lastSoundSourcePos;
        if (source == null) return;

        net.minecraft.world.phys.Vec3 playerPos = mc.player.position();
        double dx = source.x - playerPos.x;
        double dz = source.z - playerPos.z;
        if (dx * dx + dz * dz < 0.04D) return; // çok yakınsa (üstündeyse) ok gösterme

        // Kaynağa giden dünya açısı ile oyuncunun baktığı yön (yaw) arasındaki fark = ekrandaki ok yönü
        double targetAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D;
        double relativeAngle = targetAngle - mc.player.getYRot();
        relativeAngle = ((relativeAngle + 180.0D) % 360.0D + 360.0D) % 360.0D - 180.0D;

        net.minecraft.client.gui.GuiGraphics graphics = event.getGuiGraphics();
        int centerX = event.getWindow().getGuiScaledWidth() / 2;
        int centerY = event.getWindow().getGuiScaledHeight() / 2;
        int radius = 34; // nişangahtan ne kadar uzakta duracağı

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) relativeAngle));
        pose.translate(0, -radius, 0);

        // Basit bir "ok ucu" (birkaç daralan çubuktan oluşan üçgen görünümü) - sarı renk
        int color = 0xFFFFF200;
        graphics.fill(-1, -9, 1, -6, color);
        graphics.fill(-2, -6, 2, -4, color);
        graphics.fill(-3, -4, 3, -2, color);
        graphics.fill(-4, -2, 4, 1, color);

        pose.popPose();
    }

    // --- YARASA: TÜM DÜNYA SİYAH-BEYAZ (GRİ TON) + SANİYEDE 1 "SONAR" PARLAMASI ---
    // Not: Bu, gerçek bir GLSL post-processing shader (tam/doğru gri tonlama) DEĞİL — performans ve
    // karmaşıklık nedeniyle yarı saydam gri bir katman ekrana kaplanarak yaklaşık bir "siyah-beyazımsı"
    // görünüm elde ediliyor. Her saniyenin başında (20 tick'te bir) kısa bir süre bu katman incelip
    // "sonar parlaması" hissi veriyor, sonra tekrar koyulaşıyor. Gerçek/tam gri tonlama istersen ayrı bir
    // özel shader dosyası (.json + .fsh) yazmamız gerekir - bu şimdilik pratik bir yaklaşım.
    @SubscribeEvent
    public static void onRenderBatOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!MobManager.getForm(mc.player.getUUID()).equalsIgnoreCase("bat")) return;

        int cycle = mc.player.tickCount % 20; // 20 tick = 1 saniyelik döngü
        boolean pulsePhase = cycle < 5; // döngünün ilk 5 tick'i (0.25sn) "sonar parlaması"

        int alpha = pulsePhase ? 40 : 160; // parlama anında daha şeffaf (daha net görünür), aksi halde koyu gri
        int grayColor = (alpha << 24) | 0x707070;

        net.minecraft.client.gui.GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        graphics.fill(0, 0, width, height, grayColor);
    }

    // --- HUD: KARMA BARI + GÖREV KUTUSU (sol üst köşe, sarı çerçeveli) ---
    @SubscribeEvent
    public static void onRenderKarmaHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        net.minecraft.client.gui.GuiGraphics graphics = event.getGuiGraphics();
        int x = 6, y = 6;

        // Karma Barı (basit dolgu çubuğu)
        int barWidth = 100, barHeight = 6;
        int karma = Math.max(0, Math.min(100, MobManager.karmaBar));
        graphics.fill(x, y, x + barWidth, y + barHeight, 0xFF202020); // arka plan
        graphics.fill(x, y, x + (barWidth * karma / 100), y + barHeight, 0xFFE8D400); // dolu kısım (sarı/altın)
        graphics.renderOutline(x - 1, y - 1, barWidth + 2, barHeight + 2, 0xFFFFFFFF);
        graphics.drawString(mc.font, "Karma: " + karma, x, y + barHeight + 2, 0xFFFFFF);

        // Görev Kutusu (sarı ince çizgili kutu, alt alta iki satıra kadar görev metni)
        String form = MobManager.getForm(mc.player.getUUID()).toLowerCase();
        String task = MobManager.getFormTaskText(form);
        if (task != null) {
            int boxY = y + barHeight + 14;
            int boxWidth = Math.max(120, mc.font.width(task) + 8);
            int boxHeight = 24;
            graphics.fill(x, boxY, x + boxWidth, boxY + boxHeight, 0x99000000); // yarı saydam siyah zemin
            graphics.renderOutline(x, boxY, boxWidth, boxHeight, 0xFFF2C200); // sarı ince çerçeve
            graphics.drawString(mc.font, "§6GÖREV:", x + 4, boxY + 3, 0xFFFFFF);
            graphics.drawString(mc.font, task, x + 4, boxY + 13, 0xFFFFFF);
        }
    }
}
