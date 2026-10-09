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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;

public class AuroraChatTags implements ModInitializer {
    private static final Properties TAGS = new Properties();
    private static Path savePath;
    private static final String DEFAULT_COLOR = "aqua";
    // Minecraft's standard chat colors (RGB).
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    static {
        COLORS.put("black", 0x000000);
        COLORS.put("dark_blue", 0x0000AA);
        COLORS.put("dark_green", 0x00AA00);
        COLORS.put("dark_aqua", 0x00AAAA);
        COLORS.put("dark_red", 0xAA0000);
        COLORS.put("dark_purple", 0xAA00AA);
        COLORS.put("gold", 0xFFAA00);
        COLORS.put("gray", 0xAAAAAA);
        COLORS.put("dark_gray", 0x555555);
        COLORS.put("blue", 0x5555FF);
        COLORS.put("green", 0x55FF55);
        COLORS.put("aqua", 0x55FFFF);
        COLORS.put("red", 0xFF5555);
        COLORS.put("light_purple", 0xFF55FF);
        COLORS.put("yellow", 0xFFFF55);
        COLORS.put("white", 0xFFFFFF);
    }

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
            String colorName = TAGS.getProperty(sender.getUUID() + ".color", DEFAULT_COLOR);
            int rgb = COLORS.getOrDefault(colorName, COLORS.get(DEFAULT_COLOR));
            MutableComponent prefix = Component.literal("[" + tag + "] ").withColor(rgb);
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
                                StringArgumentType.getString(ctx, "tag"), DEFAULT_COLOR))
                            .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String name : COLORS.keySet()) {
                                        if (name.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                                            builder.suggest(name);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setTag(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"),
                                    StringArgumentType.getString(ctx, "tag"),
                                    StringArgumentType.getString(ctx, "color")))))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> removeTag(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("view")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> viewTag(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
        });
    }

    private static int setTag(CommandSourceStack source, ServerPlayer target, String tag, String color) {
        if (!tag.matches("[A-Za-z0-9_-]{1,20}")) {
            source.sendFailure(Component.literal("Tags must be 1-20 letters, numbers, _ or -."));
            return 0;
        }
        String normalizedColor = color.toLowerCase(Locale.ROOT);
        if (!COLORS.containsKey(normalizedColor)) {
            source.sendFailure(Component.literal("Unknown color: " + color + ". Use: " + String.join(", ", COLORS.keySet())));
            return 0;
        }
        TAGS.setProperty(target.getUUID().toString(), tag);
        TAGS.setProperty(target.getUUID() + ".color", normalizedColor);
        save();
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString() + "'s chat tag to ")
            .append(Component.literal("[" + tag + "]").withColor(COLORS.get(normalizedColor)))
            .append(Component.literal(" (" + normalizedColor + ").")), false);
        return 1;
    }

    private static int removeTag(CommandSourceStack source, ServerPlayer target) {
        TAGS.remove(target.getUUID().toString());
        TAGS.remove(target.getUUID() + ".color");
        save();
        source.sendSuccess(() -> Component.literal("Removed " + target.getName().getString() + "'s chat tag."), false);
        return 1;
    }

    private static int viewTag(CommandSourceStack source, ServerPlayer target) {
        String tag = TAGS.getProperty(target.getUUID().toString());
        if (tag == null) {
            source.sendSuccess(() -> Component.literal(target.getName().getString() + " has no tag."), false);
            return 1;
        }
        String color = TAGS.getProperty(target.getUUID() + ".color", DEFAULT_COLOR);
        source.sendSuccess(() -> Component.literal(target.getName().getString() + " tag: ")
            .append(Component.literal("[" + tag + "]").withColor(COLORS.getOrDefault(color, COLORS.get(DEFAULT_COLOR))))
            .append(Component.literal(" (" + color + ")")), false);
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

