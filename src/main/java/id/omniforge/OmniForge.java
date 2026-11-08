package id.omniforge;

import org.bukkit.plugin.java.JavaPlugin;

public final class OmniForge extends JavaPlugin {
    public Hooks hooks;
    public ItemRegistry items;
    public Runner runner;
    public Prompts prompts;
    public Gui gui;
    public PassiveTask passive;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hooks = new Hooks(this);
        items = new ItemRegistry(this);
        runner = new Runner(this);
        prompts = new Prompts(this);
        gui = new Gui(this);

        // Hook dijalankan 1 tick setelah server start supaya semua plugin lain sudah selesai load.
        getServer().getScheduler().runTask(this, () -> {
            hooks.init();
            int n = items.load();
            getLogger().info(n + " item dimuat.");
            passive.registerXp();
        });

        passive = new PassiveTask(this);
        passive.runTaskTimer(this, 40L, 20L);

        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(gui, this);

        Cmd cmd = new Cmd(this);
        var c = getCommand("omniforge");
        if (c != null) {
            c.setExecutor(cmd);
            c.setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (passive != null) {
            passive.cancel();
            passive.clearAll();
        }
    }

    public void reload() {
        reloadConfig();
        passive.clearAll();
        items.load();
    }
}
