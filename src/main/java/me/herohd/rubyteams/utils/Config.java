package me.herohd.rubyteams.utils;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class Config {

    private final File file;

    private YamlConfiguration config;
    private Consumer<Exception> exceptionHandler;

    public Config(JavaPlugin plugin, String configName) {
        file = new File(plugin.getDataFolder(), configName + ".yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(configName + ".yml", false);
        }

        reload();
    }

    public Config(File file) {
        this.file = file;

        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }

        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            if (exceptionHandler == null)
                Bukkit.getLogger().severe(String.format("C'è stato un errore durante il salvataggio di %s.yml", file.getName()));
            else
                exceptionHandler.accept(e);
        }
    }

    public String getName() {
        return file.getName().replace(".yml", "");
    }

    public void setExceptionHandler(Consumer<Exception> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }


    public Object get(String path) {
        return config.get(path);
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public String getColoredString(String path) {
        return Utils.colora(getString(path));
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public short getShort(String path) {
        return Integer.valueOf(getInt(path)).shortValue();
    }

    public long getLong(String path) {
        return config.getLong(path);
    }

    public double getDouble(String path) {
        return config.getDouble(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public ConfigurationSection getSection(String path) {
        return asBukkitConfig().getConfigurationSection(path);
    }

    public YamlConfiguration asBukkitConfig() {
        return this.config;
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }
}
