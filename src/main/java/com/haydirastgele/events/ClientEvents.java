package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

// 'value = Dist.CLIENT' sayesinde bu sınıf sadece oyuncunun bilgisayarında yüklenir, sunucuyu çökertmez.
@Mod.EventBusSubscriber(modid = "haydirastgele", value = Dist.CLIENT)
public class ClientEvents {

    // Mobların çizim (render) kopyalarını sürekli sıfırdan yaratıp oyunu kastırmamak için burada önbelleğe alıyoruz
    private static final Map<String, Entity> renderEntityCache = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = MobManager.currentMobForm.toLowerCase();

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

    // --- 9. MADDE: GÖRSEL EL MODELLERİ (FIRST-PERSON HAND RENDER) ---
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        String form = MobManager.currentMobForm.toLowerCase();

        // Eğer oyuncu bir moba dönüşmüşse, normal insan elinin çizilmesini iptal ediyoruz
        if (!form.equals("human")) {
            event.setCanceled(true); // Standart Steve/Alex elini render etme!

            // Önbellekten dönüştüğümüz mobu çekiyoruz
            Entity mobEntity = renderEntityCache.computeIfAbsent(form, f -> {
                EntityType<?> type = EntityType.byString(f).orElse(EntityType.PIG);
                return type.create(mc.player.level());
            });

            if (mobEntity != null) {
                EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
                
                event.getPoseStack().pushPose();
                // Birinci şahıs kamerasında mobun elinin/kolunun ekranda doğru pozisyonda durması için ince ayar kaydırması
                event.getPoseStack().translate(0.35D, -0.6D, -0.5D);
                event.getPoseStack().scale(0.8F, 0.8F, 0.8F);
                
                // Mobun kendi modelini el niyetine ekrana çizdiriyoruz
                dispatcher.render(
                    mobEntity, 
                    0, 0, 0, 
                    0.0F, 
                    event.getPartialTick(), 
                    event.getPoseStack(), 
                    event.getMultiBufferSource(), 
                    event.getPackedLight()
                );
                event.getPoseStack().popPose();
            }
        }
    }
}
