package com.aurora.chattags;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

public class AuroraChatTags implements ModInitializer {
    private static final Properties TAGS = new Properties();
    private static Path savePath;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            savePath = server.getWorldPath(LevelResource.ROOT).resolve("aurora-chat-tags.properties");
            TAGS.clear();
            if (Files.exists(savePath)) {
                try (InputStream in = Files.newInputStream(savePath)) {
                    TAGS.load(in);
                } catch (IOException e) {
                    System.err.println("[AuroraChatTags] Could not load tags: " + e.getMessage());
                }
            }
        });

        // Chat decorations are sent by the server: non-OP clients cannot forge a tag.
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE, (sender, message) -> {
            if (sender == null) return message;
            String tag = TAGS.getProperty(sender.getUUID().toString());
            if (tag == null || tag.isBlank()) return message;
            MutableComponent prefix = Component.literal("[" + tag + "] ").withColor(0x75BFFF);
            return prefix.append(message);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("auroratag")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.literal("set")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tag", StringArgumentType.word())
                            .executes(ctx -> setTag(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "tag"))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> removeTag(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("view")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> viewTag(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
        });
    }

    private static int setTag(CommandSourceStack source, ServerPlayer target, String tag) {
        if (!tag.matches("[A-Za-z0-9_-]{1,20}")) {
            source.sendFailure(Component.literal("Tags must be 1-20 letters, numbers, _ or -."));
            return 0;
        }
        TAGS.setProperty(target.getUUID().toString(), tag);
        save();
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString() + "'s chat tag to [" + tag + "]."), false);
        return 1;
    }

    private static int removeTag(CommandSourceStack source, ServerPlayer target) {
        TAGS.remove(target.getUUID().toString());
        save();
        source.sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + "'s chat tag."), false);
        return 1;
    }

    private static int viewTag(CommandSourceStack source, ServerPlayer target) {
        String tag = TAGS.getProperty(target.getUUID().toString(), "none");
        source.sendSuccess(() -> Component.literal(target.getName().getString() + " tag: " + tag), false);
        return 1;
    }

    private static void save() {
        if (savePath == null) return;
        try {
            Files.createDirectories(savePath.getParent());
            Path temp = savePath.resolveSibling(savePath.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                TAGS.store(out, "Aurora Chat Tags - UUID keyed");
            }
            Files.move(temp, savePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("[AuroraChatTags] Failed to save tags: " + e.getMessage());
        }
    }
}
