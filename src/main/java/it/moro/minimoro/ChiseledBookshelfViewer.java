package it.moro.minimoro;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ChiseledBookshelfViewer extends JavaPlugin {
    private Events events;
    @Getter
    private static ChiseledBookshelfViewer instance;

    @Override
    public void onEnable() {
        instance = this;
        generateResource();
        events = new Events(this);
        getServer().getPluginManager().registerEvents(events, this);
        events.startBookshelfScanner();
        getLogger().info("Chiseled Bookshelf Viewer plugin enabled!");
    }

    @Override
    public void onDisable() {
        events.stopBookshelfScanner();
        getLogger().info("Chiseled Bookshelf Viewer plugin disabled!");
    }

    void generateResource() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            boolean folder = dataFolder.mkdirs();
            if(folder){
                getLogger().info("Plugin folder created!");
            }
        }
        File fileConfig = new File(dataFolder, "config.yml");
        if (!fileConfig.exists()) {
            saveResource("config.yml", false);
            getLogger().info("config.yml file created!");

        }
    }


}
