package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// "O" tuşuyla açılan Form Günlüğü ekranı: bugüne kadar girdiğin tüm formları listeler,
// birine tıklayınca (2 dakikalık ortak bekleme süresine tabi) o forma anında geçersin.
// NOT: Form Günlüğü şu an sadece TEK OYUNCULU'da tam doğru çalışır - çünkü liste,
// sunucu tarafındaki veriyi ağ üzerinden client'a senkronize eden bir sistem YOK,
// bunun yerine sunucu ve client'ın aynı JVM'i paylaştığı (single-player) varsayımına
// dayanıyor. Gerçek çoklu oyunculu (dedicated server) senaryoda bu liste boş gelebilir -
// bunu düzeltmek için ayrı bir ağ paketi (network packet) sistemi kurmamız gerekir.
public class FormLogScreen extends Screen {

    public FormLogScreen() {
        super(Component.literal("Form Günlüğü"));
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<String> forms = new ArrayList<>(MobManager.getFormLog(mc.player.getUUID()));
        forms.sort(String::compareTo);

        int columns = 3;
        int buttonWidth = 100, buttonHeight = 20, gap = 4;
        int startX = this.width / 2 - (columns * (buttonWidth + gap)) / 2;
        int startY = 40;

        for (int i = 0; i < forms.size(); i++) {
            String form = forms.get(i);
            int col = i % columns;
            int row = i / columns;
            int bx = startX + col * (buttonWidth + gap);
            int by = startY + row * (buttonHeight + gap);

            this.addRenderableWidget(Button.builder(Component.literal(form.toUpperCase()), btn -> {
                        if (mc.player != null) {
                            mc.player.connection.sendCommand("mob " + form);
                        }
                        this.onClose();
                    })
                    .bounds(bx, by, buttonWidth, buttonHeight)
                    .build());
        }

        // İnsan formuna dönme kısayolu her zaman listede olsun
        this.addRenderableWidget(Button.builder(Component.literal("İNSAN"), btn -> {
                    if (mc.player != null) {
                        mc.player.connection.sendCommand("mob human");
                    }
                    this.onClose();
                })
                .bounds(this.width / 2 - buttonWidth / 2, this.height - 30, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, "§6Form Günlüğü §7(girdiğin tüm formlar - form değiştirmenin 2 dk bekleme süresi var)",
                this.width / 2, 15, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // tek oyunculuda oyunu duraklatma, sadece bir envanter ekranı gibi davransın
    }
}
