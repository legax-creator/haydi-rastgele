package com.haydirastgele.events;

import com.haydirastgele.utils.MobManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "haydirastgele")
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // /mob <isim> -> anında istenen forma zorla dönüşme
        SuggestionProvider<CommandSourceStack> formSuggestions = (ctx, builder) ->
                SharedSuggestionProvider.suggest(MobManager.getAllKnownForms(), builder);

        dispatcher.register(Commands.literal("mob")
                .then(Commands.argument("form", StringArgumentType.word())
                        .suggests(formSuggestions)
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
                                return 0;
                            }
                            String form = StringArgumentType.getString(ctx, "form");
                            boolean success = MobManager.forceSetForm(player, form);
                            if (!success) {
                                ctx.getSource().sendFailure(Component.literal("§cBilinmeyen form: " + form));
                                return 0;
                            }
                            return 1;
                        })));

        // /mod başlat -> çoklu oyunculu sunucuda modu SADECE komutu yazan kişi için tam aktif eder
        dispatcher.register(Commands.literal("mod")
                .then(Commands.literal("başlat")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
                                return 0;
                            }
                            MobManager.activateModFor(player);
                            player.sendSystemMessage(Component.literal("§a[Haydirastgele] Mod senin için tamamen aktif edildi!"));
                            // Aktivasyon anında hemen bir form ata (bekletmeden başlasın)
                            MobManager.assignNewMob(player);
                            MobManager.applyFormSpawnLocation(player);
                            return 1;
                        })));
    }
}
