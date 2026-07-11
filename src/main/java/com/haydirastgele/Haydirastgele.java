
package com.haydirastgele;

import com.haydirastgele.events.GameEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("haydirastgele")
public class Haydirastgele {

    public Haydirastgele() {
        // Ortak kurulum olayını dinlemeye alıyoruz
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        // Yazdığımız tüm GameEvents olaylarını Minecraft'ın ana olay yöneticisine kaydediyoruz
        MinecraftForge.EVENT_BUS.register(GameEvents.class);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Mod ilk açıldığında yapılması gerekenler buraya eklenebilir
    }
}
