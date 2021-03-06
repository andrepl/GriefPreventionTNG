package com.norcode.bukkit.griefprevention.configuration;

import com.norcode.bukkit.griefprevention.GriefPreventionTNG;
import com.norcode.bukkit.griefprevention.messages.TextMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * holds configuration data global to GP as well as 
 * providing accessors to retrieve/create configurations for individual worlds.
 * @author BC_Programming
 *
 */
public class ConfigData {

    private GriefPreventionTNG plugin;

    private String templateFile;
    private boolean debugMode;

	private HashMap<String, WorldConfig> worldCfg = new HashMap<String, WorldConfig>();
    private HashMap<TextMode, ChatColor> colors = new HashMap<TextMode, ChatColor>();

    private PlayerGroups playerGroups;
    private double claimBlocksPurchaseCost = 0.0;
    private double claimBlocksSellValue = 0.0;
    private int maxAccruedBlocks = 5000;
    private int initialBlocks = 100;
    private static File defaultConfigFolder;
    private String databaseUrl;
    private String databaseUserName;
    private String databasePassword;
    private String blockBankAccount;

    /**
     * Constructs a new ConfigData instance from the given core configuration location
     * and the passed in target out configuration.
     * @param coreConfig Configuration (config.yml) source file that contains core configuration information.
     * @param outConfig Target file to save back to.
     */
    public ConfigData(GriefPreventionTNG plugin, FileConfiguration coreConfig, FileConfiguration outConfig) {
        // core configuration is configuration that is Global.
        this.plugin = plugin;
        ConfigData.defaultConfigFolder =  new File(plugin.getDataFolder(), "WorldConfigs");
        this.debugMode = coreConfig.getBoolean("GriefPrevention.DebugMode", false);
        outConfig.set("GriefPrevention.DebugMode", this.debugMode);

        this.playerGroups = new PlayerGroups(coreConfig, "GriefPrevention.Groups");
        this.playerGroups.save(outConfig, "GriefPrevention.Groups");

        // ChatColor's should be configurable too
        colors.put(TextMode.ERROR, ChatColor.valueOf(coreConfig.getString("GriefPrevention.ChatColors.Error", "RED")));
        outConfig.set("GriefPrevention.ChatColors.Error", colors.get(TextMode.ERROR).name());
        colors.put(TextMode.INFO, ChatColor.valueOf(coreConfig.getString("GriefPrevention.ChatColors.Info", "AQUA")));
        outConfig.set("GriefPrevention.ChatColors.Info", colors.get(TextMode.INFO).name());
        colors.put(TextMode.INSTR, ChatColor.valueOf(coreConfig.getString("GriefPrevention.ChatColors.Instruction", "YELLOW")));
        outConfig.set("GriefPrevention.ChatColors.Instruction", colors.get(TextMode.INSTR).name());
        colors.put(TextMode.SUCCESS, ChatColor.valueOf(coreConfig.getString("GriefPrevention.ChatColors.Success", "GREEN")));
        outConfig.set("GriefPrevention.ChatColors.Success", colors.get(TextMode.SUCCESS).name());
        colors.put(TextMode.WARN, ChatColor.valueOf(coreConfig.getString("GriefPrevention.ChatColors.Warning", "GOLD")));
        outConfig.set("GriefPrevention.ChatColors.Warning", colors.get(TextMode.WARN).name());

        // economy / block accrual
        maxAccruedBlocks = coreConfig.getInt("GriefPrevention.Claims.MaxAccruedBlocks", 5000);
        outConfig.set("GriefPrevention.Claims.MaxAccruedBlocks", maxAccruedBlocks);
        claimBlocksPurchaseCost = coreConfig.getDouble("GriefPrevention.Economy.ClaimBlocksPurchaseCost", 0);
        outConfig.set("GriefPrevention.Economy.ClaimBlocksPurchaseCost", claimBlocksPurchaseCost);
        claimBlocksSellValue = coreConfig.getDouble("GriefPrevention.Economy.ClaimBlocksSellValue", 0);
        outConfig.set("GriefPrevention.Economy.ClaimBlocksSellValue", claimBlocksSellValue);
        initialBlocks = coreConfig.getInt("GriefPrevention.Claims.InitialBlocks", 100);
        outConfig.set("GriefPrevention.Claims.InitialBlocks", initialBlocks);
        blockBankAccount = coreConfig.getString("GriefPrevention.Economy.BlockBankAccount", "");
        outConfig.set("GriefPrevention.Economy.BlockBank.Account", blockBankAccount);



        // Database Settings
        databaseUrl = coreConfig.getString("GriefPrevention.Database.URL", "");
        outConfig.set("GriefPrevention.Database.URL", databaseUrl);
        databaseUserName = coreConfig.getString("GriefPrevention.Database.UserName", "");
        outConfig.set("GriefPrevention.Database.UserName", databaseUserName);
        databasePassword = coreConfig.getString("GriefPrevention.Database.Password", "");
        outConfig.set("GriefPrevention.Database.Password", databasePassword);

        // we try to avoid these now. Normally the primary interest is the
        // GriefPrevention.WorldConfigFolder setting.
        File defaultTemplateFile = new File(ConfigData.defaultConfigFolder, "_template.cfg");

        // Configurable template file.
        templateFile = coreConfig.getString("GriefPrevention.WorldConfig.TemplateFile", defaultTemplateFile.getPath());

        if (!(new File(templateFile).exists())) {
            templateFile = defaultTemplateFile.getPath();
        }
        outConfig.set("GriefPrevention.WorldConfig.TemplateFile", templateFile);

        // check for appropriate configuration in given FileConfiguration. Note we also save out this configuration information.
        // configurable World Configuration folder.
        // save the configuration.
        String configFolder = coreConfig.getString("GriefPrevention.WorldConfigFolder");

        if (configFolder == null || configFolder.length() == 0) {
            configFolder = defaultConfigFolder.getPath();
        }

        File configLocation = new File(configFolder);
        if (!configLocation.exists()) {
            // if not found, create the directory.
            plugin.getLogger().log(Level.INFO, "mkdirs() on " + configLocation.getAbsolutePath());
            configLocation.mkdirs();
        }

        if (configLocation.exists() && configLocation.isDirectory()) {
            for(File lookfile: configLocation.listFiles()) {
                if(lookfile.isFile()) {
                    String extension = lookfile.getName().substring(lookfile.getName().indexOf('.')+1);
                    String baseName = extension.length()==0?
                            lookfile.getName():
                            lookfile.getName().substring(0,lookfile.getName().length()-extension.length()-1);
                    if(baseName.startsWith("_")) continue; // configs starting with underscore are templates. Normally just _template.cfg.
                    // if baseName is an existing world...
                    // read it in...
                    plugin.getLogger().info(lookfile.getAbsolutePath());
                    FileConfiguration source = YamlConfiguration.loadConfiguration(new File(lookfile.getAbsolutePath()));
                    FileConfiguration target = new YamlConfiguration();
                    // load in the WorldConfig...
                    WorldConfig wc = new WorldConfig(plugin, baseName, source, target);
                    try {
                        target.save(lookfile);
                    } catch (IOException iex) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to save to " + lookfile.getAbsolutePath());
                    }
                }
            }
        } else if (configLocation.exists() && configLocation.isFile()) {
            plugin.getLogger().log(Level.SEVERE, "World Configuration Folder found, but it's a File. Double-check your GriefPrevention configuration files, and try again.");
        }
    }

    public Map<String, WorldConfig> getWorldConfigs() {
		return Collections.unmodifiableMap(worldCfg);
	}

    public String getTemplateFile() {
        return templateFile;
    }

    public List<WorldConfig> getCreativeRulesConfigs() {
		ArrayList<WorldConfig> buildList = new ArrayList<WorldConfig>();
		for(WorldConfig wcon: worldCfg.values()) {
			if(wcon.getCreativeRules()) {
                buildList.add(wcon);
            }
		}
		return buildList;
	}
    
	public WorldConfig getWorldConfig(World forWorld) {
		return getWorldConfig(forWorld.getName());
	}
    
	/**
	 * retrieves the WorldConfiguration for the given world name.
	 * If the world is not valid, a log entry will be posted, but the config should still be loaded and returned.
	 * @param worldName Name of world to get configuration of.
	 * @return WorldConfig instance representing the configuration for the given world.
	 */
	public WorldConfig getWorldConfig(String worldName) {
		World world = null;
		// print log message if the passed world is not currently loaded or present.
		// it will still go ahead and try to load the configuration.
		if((world = Bukkit.getWorld(worldName))==null) {
			plugin.getLogger().log(Level.SEVERE, "invalid World:" + worldName);
		}
		
		// if it's not in the hashmap...
		if(!worldCfg.containsKey(worldName)) {
			// special code: it's possible a configuration might already exist for this file, so we'll
			// check
			String checkYamlFile = new File(ConfigData.defaultConfigFolder, worldName + ".cfg").getPath();
			// if it exists...
			if (new File(checkYamlFile).exists()) {
				// attempt to load the configuration from the given file.
				YamlConfiguration existingcfg = YamlConfiguration.loadConfiguration(new File(checkYamlFile));
				YamlConfiguration outConfiguration = new YamlConfiguration();
				// place it in the hashmap.
				worldCfg.put(worldName,new WorldConfig(plugin, worldName, existingcfg, outConfiguration));
				// try to save it. this can error out for who knows what reason. If it does, we'll
				// squirt the issue to the log.
				try {
                    outConfiguration.save(new File(checkYamlFile));
                } catch (IOException iex) {
					plugin.getLogger().log(Level.SEVERE,"Failed to save World Config for world " + worldName);
				}
			} else {
				// if the file doesn't exist, then we will go ahead and create a new configuration.
				// set the input Yaml to default to the template.
				// if the template file exists, load it's configuration and use the result as useSource. 
				// Otherwise, we create a blank configuration.
				FileConfiguration useSource = (new File(templateFile).exists() ? YamlConfiguration.loadConfiguration(new File(templateFile)) : new YamlConfiguration());
				// The target save location.
				FileConfiguration target = new YamlConfiguration();
				// place it in the hashmap.
				worldCfg.put(worldName, new WorldConfig(plugin, world.getName(), useSource, target));
				try {
					target.save(new File(checkYamlFile));
				}catch(IOException ioex) {
					plugin.getLogger().log(Level.SEVERE, "Failed to write world configuration to " + checkYamlFile);
				}		
            }
			// save target
        }
		// after the above logic, we know it's in the hashmap, so return that.
		return worldCfg.get(worldName);
	}
    
	public static FileConfiguration createTargetConfiguration(String sName) {
		return YamlConfiguration.loadConfiguration(new File(sName));
	}
    
	/**
	 * returns the Configuration file location for the given world. Note that this file may or may not exist.
	 * @param sName Name of the world.
	 * @return the path name at which this configuration file will be found if it exists.
	 */
	public static String getWorldConfigLocation(String sName) {
        return new File(defaultConfigFolder, sName + ".cfg").getPath();
	}

    public ChatColor getColor(TextMode color) {
        if (colors.containsKey(color)) {
            return colors.get(color);
        }
        return ChatColor.WHITE;
    }

    public PlayerGroups getPlayerGroups() {
        return playerGroups;
    }

    public double getClaimBlocksSellValue() {
        return claimBlocksSellValue;
    }

    public double getClaimBlocksPurchaseCost() {
        return claimBlocksPurchaseCost;
    }

    public int getMaxAccruedBlocks() {
        return maxAccruedBlocks;
    }

    public int getInitialBlocks() {
        return initialBlocks;
    }

    public String getDatabaseUrl() {
        return databaseUrl;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public String getDatabaseUserName() {
        return databaseUserName;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getBlockBankAccount() {
        return blockBankAccount;
    }
}