package com.example.headswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class HeadSwapClient implements ClientModInitializer {
    private static final int HEAD_SLOT = 5;        // слот шлема в PlayerScreenHandler
    private static final int FIRST_INV_SLOT = 9;   // инвентарь 9..35, хотбар 36..44
    private static final int LAST_INV_SLOT = 44;

    private static KeyBinding swapKey;

    // Настройки (файл config/headswap.properties)
    private static List<String> keywords = List.of("шар", "сфера", "sphere");
    private static boolean playerHeadIsSphere = true;

    // Куда ушёл шлем при последнем обмене, чтобы вернуть обратно
    private static int lastSlot = -1;

    @Override
    public void onInitializeClient() {
        loadConfig();
        swapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.headswap.swap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                KeyBinding.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (swapKey.wasPressed()) {
                swap(client);
            }
        });
    }

    private static boolean isSphere(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (playerHeadIsSphere && stack.isOf(Items.PLAYER_HEAD)) return true;
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            if (!k.isEmpty() && name.contains(k)) return true;
        }
        return false;
    }

    private static void swap(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null || client.currentScreen != null) return;

        var handler = player.playerScreenHandler;
        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        int slot;

        if (isSphere(head)) {
            // Шар уже надет: возвращаем шлем
            slot = -1;
            if (lastSlot >= FIRST_INV_SLOT && lastSlot <= LAST_INV_SLOT) {
                ItemStack s = handler.getSlot(lastSlot).getStack();
                if (!s.isEmpty() && !isSphere(s)) slot = lastSlot;
            }
            if (slot == -1) {
                for (int i = FIRST_INV_SLOT; i <= LAST_INV_SLOT; i++) {
                    ItemStack s = handler.getSlot(i).getStack();
                    if (!s.isEmpty() && !isSphere(s)
                            && player.getPreferredEquipmentSlot(s) == EquipmentSlot.HEAD) {
                        slot = i;
                        break;
                    }
                }
            }
            if (slot == -1) return;
        } else {
            // Ищем шар в инвентаре
            slot = -1;
            for (int i = FIRST_INV_SLOT; i <= LAST_INV_SLOT; i++) {
                if (isSphere(handler.getSlot(i).getStack())) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) return;
        }

        int sync = handler.syncId;
        var im = client.interactionManager;
        // 1) берём предмет из слота инвентаря, 2) кладём на голову (шлем уходит в курсор),
        // 3) кладём курсор в освободившийся слот. Если на голову нельзя — предмет вернётся в слот.
        im.clickSlot(sync, slot, 0, SlotActionType.PICKUP, player);
        im.clickSlot(sync, HEAD_SLOT, 0, SlotActionType.PICKUP, player);
        im.clickSlot(sync, slot, 0, SlotActionType.PICKUP, player);
        lastSlot = slot;
    }

    private static void loadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("headswap.properties");
        Properties p = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            } else {
                p.setProperty("keywords", "шар,сфера,sphere");
                p.setProperty("playerHead", "true");
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    p.store(w, "keywords: слова в названии предмета (шар). playerHead: считать любую голову игрока шаром");
                }
            }
        } catch (IOException ignored) {
        }
        List<String> list = new ArrayList<>();
        for (String s : p.getProperty("keywords", "шар,сфера,sphere").split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) list.add(t);
        }
        keywords = list;
        playerHeadIsSphere = Boolean.parseBoolean(p.getProperty("playerHead", "true"));
    }
}
