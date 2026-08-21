package com.alka.mines.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Ponte opcional com o ItemsAdder - resolve ITENS CUSTOM (ex: "myitems:crystal") pra usar
 * nas skins da picareta e no GUI. Tudo via reflexao (mesmo motivo do AlkaEconomyHook: um
 * import direto de dev.lone.itemsadder.api.CustomStack aqui causaria NoClassDefFoundError
 * sem o ItemsAdder instalado). Softdepend.
 */
public final class ItemsAdderHook {

    private static ItemsAdderHook instance;

    private final Method customStackGetInstance;
    private final Method getItemStack;

    private ItemsAdderHook(Method customStackGetInstance, Method getItemStack) {
        this.customStackGetInstance = customStackGetInstance;
        this.getItemStack = getItemStack;
    }

    public static void tryHook(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return;
        }
        try {
            Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStack.getMethod("getInstance", String.class);
            Method getItemStack = customStack.getMethod("getItemStack");
            instance = new ItemsAdderHook(getInstance, getItemStack);
            plugin.getLogger().info("Hook do ItemsAdder habilitado (itens custom nas skins/GUI).");
        } catch (Throwable e) {
            plugin.getLogger().warning("ItemsAdder encontrado mas a API nao carregou via reflexao: " + e.getMessage());
        }
    }

    /** Resolve um item custom do ItemsAdder pelo ID (namespace:id). Vazio se ausente/falhou. */
    public static Optional<ItemStack> getCustomItem(String id) {
        if (instance == null || id == null || id.isEmpty()) {
            return Optional.empty();
        }
        try {
            Object customStack = instance.customStackGetInstance.invoke(null, id);
            if (customStack == null) {
                return Optional.empty();
            }
            return Optional.of((ItemStack) instance.getItemStack.invoke(customStack));
        } catch (Throwable e) {
            return Optional.empty();
        }
    }
}
