package net.atlas.antiProjectileGhosting;

import net.atlas.antiProjectileGhosting.listeners.ProjectilePhaseListener;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiProjectileGhosting extends JavaPlugin {
    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ProjectilePhaseListener(), this);
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }
}
