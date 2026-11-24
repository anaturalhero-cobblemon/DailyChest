package com.anaturalhero.dailychest;

import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavePath;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public class DailyChestMod implements ModInitializer {
    private static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> CHESTS = new HashSet<>();
    private static final Map<String,String> CLAIMS = new HashMap<>();
    private static Path file;

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            file = server.getSavePath(WorldSavePath.ROOT).resolve("dailychest.json");
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> save());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(literal("dailychest").requires(s -> s.hasPermissionLevel(2))
                .then(literal("create").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    BlockPos pos = target(p);
                    if (pos == null) { p.sendMessage(Text.literal("Look at a single chest within 5 blocks first."), false); return 0; }
                    if (CHESTS.add(key(p.getServerWorld(), pos))) {
                        save(); p.sendMessage(Text.literal("Daily reward chest registered."), false);
                    } else p.sendMessage(Text.literal("That chest is already registered."), false);
                    return 1;
                }))
                .then(literal("remove").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    BlockPos pos = target(p);
                    if (pos == null) { p.sendMessage(Text.literal("Look at a chest within 5 blocks first."), false); return 0; }
                    if (CHESTS.remove(key(p.getServerWorld(), pos))) {
                        save(); p.sendMessage(Text.literal("Daily chest registration removed."), false);
                    } else p.sendMessage(Text.literal("That chest is not registered."), false);
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    load(); ctx.getSource().sendFeedback(() -> Text.literal("DailyChest data reloaded."), true); return 1;
                }))
            )
        );

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            String chestKey = key(world, pos);
            if (!CHESTS.contains(chestKey)) return ActionResult.PASS;

            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof ChestBlockEntity chest)) {
                CHESTS.remove(chestKey); save(); return ActionResult.FAIL;
            }

            String claimKey = p.getUuidAsString() + "|" + chestKey;
            String today = LocalDate.now(ZONE).toString();
            if (today.equals(CLAIMS.get(claimKey))) {
                p.sendMessage(Text.literal("You've already opened this today! Come back after midnight Pacific Time."), true);
                return ActionResult.SUCCESS;
            }

            // Save the claim before awarding items so a restart cannot duplicate a reward.
            CLAIMS.put(claimKey, today);
            save();

            ServerWorld sw = (ServerWorld) world;
            for (int i = 0; i < chest.size(); i++) {
                ItemStack reward = chest.getStack(i).copy();
                if (reward.isEmpty()) continue;
                boolean fullyInserted = p.getInventory().insertStack(reward);
                if (!fullyInserted && !reward.isEmpty()) {
                    sw.spawnEntity(new ItemEntity(sw, pos.getX()+0.5, pos.getY()+1.0, pos.getZ()+0.5, reward));
                }
            }
            p.sendMessage(Text.literal("Daily chest claimed!"), true);
            return ActionResult.SUCCESS;
        });
    }

    private static BlockPos target(ServerPlayerEntity p) {
        HitResult r = p.raycast(5.0, 0.0F, false);
        if (!(r instanceof BlockHitResult b)) return null;
        BlockPos pos = b.getBlockPos();
        return p.getWorld().getBlockEntity(pos) instanceof ChestBlockEntity ? pos : null;
    }
    private static String key(World w, BlockPos p) {
        return w.getRegistryKey().getValue() + "|" + p.getX() + "," + p.getY() + "," + p.getZ();
    }
    private static void load() {
        CHESTS.clear(); CLAIMS.clear();
        if (file == null || !Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (root.has("chests")) for (JsonElement e : root.getAsJsonArray("chests")) CHESTS.add(e.getAsString());
            if (root.has("claims")) for (var e : root.getAsJsonObject("claims").entrySet()) CLAIMS.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception e) { System.err.println("[DailyChest] Failed to load data: " + e.getMessage()); }
    }
    private static void save() {
        if (file == null) return;
        JsonObject root = new JsonObject(); JsonArray chests = new JsonArray();
        CHESTS.forEach(chests::add); root.add("chests", chests);
        JsonObject claims = new JsonObject(); CLAIMS.forEach(claims::addProperty); root.add("claims", claims);
        try { Files.createDirectories(file.getParent()); Files.writeString(file, GSON.toJson(root)); }
        catch (IOException e) { System.err.println("[DailyChest] Failed to save data: " + e.getMessage()); }
    }
}
