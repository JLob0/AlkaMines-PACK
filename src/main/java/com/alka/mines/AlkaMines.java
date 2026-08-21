package com.alka.mines;

import com.alka.mines.ability.PersonalDragonAbility;
import com.alka.mines.command.AdminCommand;
import com.alka.mines.command.PersonalMineCommand;
import com.alka.mines.config.EnchantmentConfig;
import com.alka.mines.config.MineConfig;
import com.alka.mines.enchantment.ExplosionEnchantment;
import com.alka.mines.enchantment.MemorizeSystem;
import com.alka.mines.enchantment.PersonalEnchantmentManager;
import com.alka.mines.entity.FakeEntityRegistry;
import com.alka.mines.gui.PickaxeGui;
import com.alka.mines.listener.PersonalMineListener;
import com.alka.mines.listener.PickaxeGuiListener;
import com.alka.mines.listener.PickaxeProtectionListener;
import com.alka.mines.personal.PersonalMineManager;
import com.alka.mines.pickaxe.PickaxeCommand;
import com.alka.mines.pickaxe.PickaxeLevelManager;
import com.alka.mines.pickaxe.PickaxeManager;
import com.alka.mines.pickaxe.PickaxeProgressDisplay;
import com.alka.mines.pickaxe.PrestigeSystem;
import com.alka.mines.rental.RentalGui;
import com.alka.mines.rental.ToolRentalSystem;
import com.alka.mines.util.DebugLogger;
import com.alka.mines.virtual.VirtualBlockSystem;
import com.alka.mines.visual.FloatingTextManager;
import com.alkacode.core.plugin.AlkaPlugin;

/**
 * AlkaMinesPack - mina pessoal evolutiva (blocos virtuais client-side, anti-lag) com
 * picareta evolutiva, encantamentos, prestigio, skins, aluguel e textos flutuantes.
 *
 * Todo o legado de minas publicas/particulares (MineManager, comandos de criar/editar,
 * menus antigos, hooks de mina) foi removido. O estado da picareta vive no item.
 */
public final class AlkaMines extends AlkaPlugin {

    private PersonalMineManager personalMineManager;
    private VirtualBlockSystem virtualBlockSystem;
    private PickaxeManager pickaxeManager;
    private PickaxeLevelManager pickaxeLevelManager;
    private PickaxeProgressDisplay pickaxeProgressDisplay;
    private PersonalEnchantmentManager personalEnchantmentManager;
    private PrestigeSystem prestigeSystem;
    private ToolRentalSystem toolRentalSystem;
    private FloatingTextManager floatingTextManager;
    private FakeEntityRegistry fakeEntityRegistry;
    private PersonalDragonAbility personalDragonAbility;

    @Override
    protected void onPluginEnable() {
        DebugLogger.setEnabled(getConfig().getBoolean("debug", false));
        com.alka.mines.config.MenuConfig.init(this);
        com.alka.mines.config.MessagesConfig.init(this);
        EnchantmentConfig.load(getConfig());
        MineConfig.load(getConfig());

        com.alka.mines.hook.ItemsAdderHook.tryHook(this);

        initPersonalMineSystem();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new com.alka.mines.hook.PlaceholderHook(pickaxeManager).register();
            getLogger().info("Placeholders do AlkaMinesPack registrados (PlaceholderAPI).");
        }

        getLogger().info("AlkaMinesPack habilitado (mina pessoal evolutiva).");
    }

    /** Inicializa o sistema de mina pessoal evolutiva (blocos virtuais, anti-lag). */
    private void initPersonalMineSystem() {
        MineConfig mc = MineConfig.get();

        this.personalMineManager = new PersonalMineManager(this);

        // picareta evolutiva (nivel por blocos minerados; mina expande com o nivel)
        this.pickaxeManager = new PickaxeManager();
        double base = getConfig().getDouble("pickaxe-evolution.base", 5.0);
        double exponent = getConfig().getDouble("pickaxe-evolution.exponent", 1.0);
        int maxLevel = getConfig().getInt("pickaxe-evolution.max-level", 100);
        this.pickaxeLevelManager = new PickaxeLevelManager(base, exponent, maxLevel);
        this.pickaxeProgressDisplay = new PickaxeProgressDisplay(pickaxeLevelManager);

        this.floatingTextManager = new FloatingTextManager(this);

        this.virtualBlockSystem = new VirtualBlockSystem(this, mc.getRespawnDelay(), pickaxeManager, floatingTextManager);

        // prestigio, skins e aluguel
        this.prestigeSystem = new PrestigeSystem(this, pickaxeManager, pickaxeLevelManager, maxLevel);
        this.toolRentalSystem = new ToolRentalSystem(this);

        // encantamentos da picareta (mina pessoal)
        ExplosionEnchantment explosion = new ExplosionEnchantment(virtualBlockSystem);
        MemorizeSystem memorize = new MemorizeSystem();

        // habilidade do dragao (entidade fake que quebra blocos virtuais)
        this.fakeEntityRegistry = new FakeEntityRegistry();
        getServer().getPluginManager().registerEvents(fakeEntityRegistry, this);
        this.personalDragonAbility = new PersonalDragonAbility(this, personalMineManager, virtualBlockSystem,
                pickaxeManager, pickaxeLevelManager, pickaxeProgressDisplay, floatingTextManager, fakeEntityRegistry);

        this.personalEnchantmentManager = new PersonalEnchantmentManager(
                virtualBlockSystem, explosion, memorize, personalDragonAbility);

        PersonalMineListener listener = new PersonalMineListener(this, personalMineManager,
                virtualBlockSystem, mc.getMineWorld(), pickaxeManager, pickaxeLevelManager,
                pickaxeProgressDisplay, personalEnchantmentManager, floatingTextManager);
        listener.register();

        getCommand("mina").setExecutor(new PersonalMineCommand(personalMineManager));
        getCommand("minapessoal").setExecutor(new PersonalMineCommand(personalMineManager));
        getCommand("picareta").setExecutor(new PickaxeCommand(pickaxeManager, prestigeSystem, this));
        getCommand("encantamentos").setExecutor((s, c, l, a) -> {
            if (s instanceof org.bukkit.entity.Player p) {
                new PickaxeGui(this, p, pickaxeManager, prestigeSystem).open();
            }
            return true;
        });
        getCommand("alkaminesadmin").setExecutor(new AdminCommand(pickaxeManager, personalMineManager));
        getCommand("prestigio").setExecutor((s, c, l, a) -> {
            if (s instanceof org.bukkit.entity.Player p) {
                prestigeSystem.openPrestigeGui(p);
            }
            return true;
        });
        getCommand("alugar").setExecutor((s, c, l, a) -> {
            if (s instanceof org.bukkit.entity.Player p) {
                new RentalGui(this, p, toolRentalSystem).open();
            }
            return true;
        });

        getServer().getPluginManager().registerEvents(new PickaxeGuiListener(pickaxeManager, prestigeSystem, this), this);
        // protecao da picareta (drop exige confirmar 2x) - se perder, recomeca do zero
        getServer().getPluginManager().registerEvents(new PickaxeProtectionListener(this), this);
        getLogger().info("Mina pessoal evolutiva (blocos virtuais) inicializada.");
    }

    @Override
    protected void onPluginDisable() {
        if (floatingTextManager != null) {
            floatingTextManager.cleanupAll();
        }
    }

    public PersonalMineManager getPersonalMineManager() {
        return personalMineManager;
    }
}
