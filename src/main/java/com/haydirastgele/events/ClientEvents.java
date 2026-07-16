package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// 'value = Dist.CLIENT' sayesinde bu sınıf sadece oyuncunun bilgisayarında yüklenir, sunucuyu çökertmez.
@Mod.EventBusSubscriber(modid = "haydirastgele", value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        String form = MobManager.currentMobForm.toLowerCase();

        // Eğer oyuncu insan formunda değilse Steve/Alex modelini gizleyip yerine mob modelini çizeriz
        if (!form.equals("human")) {
            event.setCanceled(true); // Normal insan modelini çizme, iptal et!
            
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            // Dönüşülen mobu bul, bulamazsa varsayılan olarak tatlı bir domuzcuk yap :)
            EntityType<?> type = EntityType.byString(form).orElse(EntityType.PIG); 
            Entity mobEntity = type.create(player.level());
            
            if (mobEntity != null) {
                // Mobun konumunu ve bakış açılarını oyuncununkiyle birebir eşitle
                mobEntity.setPos(player.getX(), player.getY(), player.getZ());
                mobEntity.setYRot(player.getYRot());
                mobEntity.setXRot(player.getXRot());
                mobEntity.setYHeadRot(player.getYHeadRot());
                mobEntity.yRotO = player.yRotO;
                mobEntity.xRotO = player.xRotO;
                
                // Mobu tam oyuncunun olduğu yere çiz
                event.getPoseStack().pushPose();
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
}

