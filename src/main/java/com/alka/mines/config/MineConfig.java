package com.alka.mines.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrega toda a config da mina pessoal (tamanhos, expansao, profundidade, composicoes
 * por nivel e valores de venda) do config.yml - nada hardcode.
 */
public class MineConfig {

    private static MineConfig instance;

    private final int maxSize;
    private final int startSize;
    private final int depth;
    private final int expandEveryLevels;
    private final int prestigeLevelBonus;
    private final int surfaceY;
    private final int gridSpacing;
    private final int respawnDelay;
    private final String mineWorld;
    private final Map<Integer, Map<Material, Double>> compositions;
    private final Map<Material, Double> sellValues;
    private final Map<String, String> skinCustomItems;

    public static MineConfig get() {
        return instance;
    }

    public static void load(FileConfiguration cfg) {
        instance = new MineConfig(cfg);
    }

    private MineConfig(FileConfiguration cfg) {
        maxSize = cfg.getInt("personal-mine.max-size", 31);
        startSize = cfg.getInt("personal-mine.start-size", 5);
        depth = cfg.getInt("personal-mine.depth", 50);
        expandEveryLevels = cfg.getInt("personal-mine.expand-every-levels", 20);
        prestigeLevelBonus = cfg.getInt("personal-mine.prestige-level-bonus", 100);
        surfaceY = cfg.getInt("personal-mine.surface-y", 100);
        gridSpacing = cfg.getInt("personal-mine.grid-spacing", 1000);
        respawnDelay = cfg.getInt("personal-mine.respawn-delay-ticks", 40);
        mineWorld = cfg.getString("personal-mine.mine-world", "world");
        compositions = parseCompositions(cfg.getConfigurationSection("personal-mine.compositions"));
        sellValues = parseSellValues(cfg.getConfigurationSection("personal-mine.sell-values"));
        skinCustomItems = parseSkinCustomItems(cfg.getConfigurationSection("skins"));
    }

    private Map<String, String> parseSkinCustomItems(ConfigurationSection section) {
        Map<String, String> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String skin : section.getKeys(false)) {
            result.put(skin, section.getString(skin + ".custom-item", ""));
        }
        return result;
    }

    /** Item custom (ItemsAdder) configurado pra uma skin, ou null/vazio se nao houver. */
    public String getSkinCustomItem(String skinName) {
        return skinCustomItems.getOrDefault(skinName.toUpperCase(), "");
    }

    private Map<Integer, Map<Material, Double>> parseCompositions(ConfigurationSection section) {
        Map<Integer, Map<Material, Double>> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection levelSection = section.getConfigurationSection(key);
            if (levelSection == null) {
                continue;
            }
            Map<Material, Double> comp = new LinkedHashMap<>();
            for (String matKey : levelSection.getKeys(false)) {
                Material mat = Material.matchMaterial(matKey);
                if (mat != null) {
                    comp.put(mat, levelSection.getDouble(matKey));
                }
            }
            try {
                result.put(Integer.parseInt(key), comp);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private Map<Material, Double> parseSellValues(ConfigurationSection section) {
        Map<Material, Double> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String matKey : section.getKeys(false)) {
            Material mat = Material.matchMaterial(matKey);
            if (mat != null) {
                result.put(mat, section.getDouble(matKey));
            }
        }
        return result;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getStartSize() {
        return startSize;
    }

    public int getDepth() {
        return depth;
    }

    public int getExpandEveryLevels() {
        return expandEveryLevels;
    }

    public int getPrestigeLevelBonus() {
        return prestigeLevelBonus;
    }

    public int getSurfaceY() {
        return surfaceY;
    }

    public int getGridSpacing() {
        return gridSpacing;
    }

    public int getRespawnDelay() {
        return respawnDelay;
    }

    public String getMineWorld() {
        return mineWorld;
    }

    /** Composicao do nivel dado, ou null se nao houver. */
    public Map<Material, Double> getCompositionForLevel(int level) {
        Map<Material, Double> best = null;
        for (Map.Entry<Integer, Map<Material, Double>> e : compositions.entrySet()) {
            if (e.getKey() <= level) {
                best = e.getValue();
            }
        }
        return best;
    }

    public Map<Material, Double> getSellValues() {
        return sellValues;
    }
}
