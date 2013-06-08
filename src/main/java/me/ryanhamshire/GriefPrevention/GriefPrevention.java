/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import me.ryanhamshire.GriefPrevention.Configuration.ClaimMetaHandler;
import me.ryanhamshire.GriefPrevention.Configuration.ConfigData;
import me.ryanhamshire.GriefPrevention.Configuration.WorldConfig;
import me.ryanhamshire.GriefPrevention.tasks.CleanupUnusedClaimsTask;
import me.ryanhamshire.GriefPrevention.tasks.DeliverClaimBlocksTask;
import me.ryanhamshire.GriefPrevention.tasks.EntityCleanupTask;
import me.ryanhamshire.GriefPrevention.tasks.RestoreNatureProcessingTask;
import me.ryanhamshire.GriefPrevention.tasks.SendPlayerMessageTask;
import me.ryanhamshire.GriefPrevention.tasks.TreeCleanupTask;
import me.ryanhamshire.GriefPrevention.visualization.Visualization;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class GriefPrevention extends JavaPlugin {
    // for convenience, a reference to the instance of this plugin
    public static GriefPrevention instance;

    // for logging to the console and log file
    private static Logger log = Logger.getLogger("Minecraft");
    public ConfigData Configuration = null;
    // this handles data storage, like player and region data
    public DataStore dataStore;
    public PlayerGroups config_player_groups = null;
    // configuration variables, loaded/saved from a config.yml

    public int config_claims_initialBlocks;                         // the number of claim blocks a new player starts with
    public int config_claims_maxAccruedBlocks;                      // the limit on accrued blocks (over time).  doesn't limit purchased or admin-gifted blocks
    // start removal....
    // reference to the economy plugin, if economy integration is enabled
    public static Economy economy = null;

    double config_economy_claimBlocksPurchaseCost;            // cost to purchase a claim block.  set to zero to disable purchase.

    double config_economy_claimBlocksSellValue;               // return on a sold claim block.  set to zero to disable sale.

    // how far away to search from a tree trunk for its branch blocks
    public static final int TREE_RADIUS = 5;

    // how long to wait before deciding a player is staying online or staying offline, for notification messages
    public static final int NOTIFICATION_SECONDS = 20;

    private CommandHandler commandHandler;

    // adds a server log entry
    public static void AddLogEntry(String entry) {
        log.info("GriefPrevention: " + entry);
    }

    /**
     * Retrieves a World Configuration given the World. if the World Configuration is not loaded,
     * it will be loaded from the plugins/GriefPreventionData/WorldConfigs folder. If a file is not present for the world,
     * the template file will be used. The template file is configured in config.yml, and defaults to _template.cfg in the given folder.
     * if no template is found, a default, empty configuration is created and returned.
     *
     * @param world World to retrieve configuration for.
     * @return WorldConfig representing the configuration of the given world.
     */
    public WorldConfig getWorldCfg(World world) {
        return Configuration.getWorldConfig(world);
    }

    /**
     * Retrieves a World Configuration given the World Name. If the World Configuration is not loaded, it will be loaded
     * from the plugins/GriefPreventionData/WorldConfigs folder. If a file is not present, the template will be used and a new file will be created for
     * the given name.
     *
     * @param worldname Name of world to get configuration for.
     * @return WorldConfig representing the configuration of the given world.
     */
    public WorldConfig getWorldCfg(String worldname) {
        return Configuration.getWorldConfig(worldname);
    }

    private ClaimMetaHandler MetaHandler = null;

    /**
     * Retrieves the Claim Metadata handler. Unused by GP itself, this is useful for
     * Plugins that which to create Claim-based data. A prime example is a plugin like GriefPreventionFlags, which
     * adds Claim-based flag information to claims. Many plugins use their own special methods of indexing per-claim,
     * so I thought it made sense to add a sort of "official" API to it, so that they are all consistent.
     *
     * @return ClaimMetaHandler object.
     */
    public ClaimMetaHandler getMetaHandler() {
        return MetaHandler;
    }

    private static boolean eventsRegistered = false;

    // initializes well...   everything
    public void onEnable() {
        AddLogEntry("Grief Prevention enabled.");

        instance = this;
        GriefPrevention.AddLogEntry(new File(DataStore.configFilePath).getAbsolutePath());
        GriefPrevention.AddLogEntry("File Exists:" + new File(DataStore.configFilePath).exists());
        // load the config if it exists
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
        FileConfiguration outConfig = new YamlConfiguration();
        Configuration = new ConfigData(config, outConfig);
        // read configuration settings (note defaults)
        commandHandler = new CommandHandler(this);

        // load player groups.
        this.config_player_groups = new PlayerGroups(config, "GriefPrevention.Groups");
        this.config_player_groups.Save(outConfig, "GriefPrevention.Groups");
        // optional database settings
        String databaseUrl = config.getString("GriefPrevention.Database.URL", "");
        String databaseUserName = config.getString("GriefPrevention.Database.UserName", "");
        String databasePassword = config.getString("GriefPrevention.Database.Password", "");
        // sea level


        outConfig.set("GriefPrevention.Database.URL", databaseUrl);
        outConfig.set("GriefPrevention.Database.UserName", databaseUserName);
        outConfig.set("GriefPrevention.Database.Password", databasePassword);

        this.config_economy_claimBlocksPurchaseCost = config.getDouble("GriefPrevention.Economy.ClaimBlocksPurchaseCost", 0);
        this.config_economy_claimBlocksSellValue = config.getDouble("GriefPrevention.Economy.ClaimBlocksSellValue", 0);
        this.config_claims_maxAccruedBlocks = config.getInt("GriefPrevention.Claims.MaxAccruedBlocks", 5000);
        outConfig.set("GriefPrevention.Claims.MaxAccruedBlocks", config_claims_maxAccruedBlocks);

        this.config_claims_initialBlocks = config.getInt("GriefPrevention.Claims.InitialBlocks", 100);

        outConfig.set("GriefPrevention.Economy.ClaimBlocksPurchaseCost", this.config_economy_claimBlocksPurchaseCost);
        outConfig.set("GriefPrevention.Economy.ClaimBlocksSellValue", this.config_economy_claimBlocksSellValue);
        outConfig.set("GriefPrevention.Claims.InitialBlocks", config_claims_initialBlocks);


        // when datastore initializes, it loads player and claim data, and posts some stats to the log
        if (databaseUrl.length() > 0) {
            try {
                DatabaseDataStore databaseStore = new DatabaseDataStore(databaseUrl, databaseUserName, databasePassword);

                if (FlatFileDataStore.hasData()) {
                    GriefPrevention.AddLogEntry("There appears to be some data on the hard drive.  Migrating those data to the database...");
                    FlatFileDataStore flatFileStore = new FlatFileDataStore();
                    flatFileStore.migrateData(databaseStore);
                    GriefPrevention.AddLogEntry("Data migration process complete.  Reloading data from the database...");
                    databaseStore.close();
                    databaseStore = new DatabaseDataStore(databaseUrl, databaseUserName, databasePassword);
                }

                this.dataStore = databaseStore;
            } catch (Exception e) {
                GriefPrevention.AddLogEntry("Because there was a problem with the database, GriefPrevention will not function properly.  Either update the database config settings resolve the issue, or delete those lines from your config.yml so that GriefPrevention can use the file system to store data.");
                return;
            }
        }

        // if not using the database because it's not configured or because there was a problem, use the file system to store data
        // this is the preferred method, as it's simpler than the database scenario
        if (this.dataStore == null) {
            try {
                this.dataStore = new FlatFileDataStore();
            } catch (Exception e) {
                GriefPrevention.AddLogEntry("Unable to initialize the file system data store.  Details:");
                GriefPrevention.AddLogEntry(e.getMessage());
            }
        }
        boolean claimblockaccrual = false;
        for (WorldConfig wconfig : this.Configuration.getWorldConfigs().values()) {
            if (wconfig.getClaimBlocksAccruedPerHour() > 0) {
                claimblockaccrual = true;
                break;
            }
        }
        // unless claim block accrual is disabled, start the recurring per 5 minute event to give claim blocks to online players
        // 20L ~ 1 second
        if (claimblockaccrual) {
            DeliverClaimBlocksTask task = new DeliverClaimBlocksTask();
            this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 20L * 60 * 5, 20L * 60 * 5);
        }

        // start the recurring cleanup event for entities in creative worlds, if enabled.

        // start recurring cleanup scan for unused claims belonging to inactive players
        // if the option is enabled.
        // look through all world configurations.
        boolean claimcleanupOn = false;
        boolean entitycleanupEnabled = false;
        for (WorldConfig wconfig : Configuration.getWorldConfigs().values()) {
            if (wconfig.getClaimCleanupEnabled())
                claimcleanupOn = true;
            if (wconfig.getEntityCleanupEnabled())
                entitycleanupEnabled = true;
        }

        if (entitycleanupEnabled) {
            EntityCleanupTask task = new EntityCleanupTask(0);
            this.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L);
        }

        if (claimcleanupOn) {
            CleanupUnusedClaimsTask task2 = new CleanupUnusedClaimsTask();
            this.getServer().getScheduler().scheduleSyncRepeatingTask(this, task2, 20L * 60 * 2, 20L * 60 * 5);
        }

        // register for events
        if (!eventsRegistered) {
            eventsRegistered = true;
            PluginManager pluginManager = this.getServer().getPluginManager();

            // player events
            PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
            pluginManager.registerEvents(playerEventHandler, this);

            // block events
            BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
            pluginManager.registerEvents(blockEventHandler, this);

            // entity events
            EntityEventHandler entityEventHandler = new EntityEventHandler(this.dataStore);
            pluginManager.registerEvents(entityEventHandler, this);
        }

        // if economy is enabled
        if (this.config_economy_claimBlocksPurchaseCost > 0 || this.config_economy_claimBlocksSellValue > 0) {
            // try to load Vault
            GriefPrevention.AddLogEntry("GriefPrevention requires Vault for economy integration.");
            GriefPrevention.AddLogEntry("Attempting to load Vault...");
            RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            GriefPrevention.AddLogEntry("Vault loaded successfully!");

            // ask Vault to hook into an economy plugin
            GriefPrevention.AddLogEntry("Looking for a Vault-compatible economy plugin...");
            if (economyProvider != null) {
                GriefPrevention.economy = economyProvider.getProvider();

                // on success, display success message
                if (GriefPrevention.economy != null) {
                    GriefPrevention.AddLogEntry("Hooked into economy: " + GriefPrevention.economy.getName() + ".");
                    GriefPrevention.AddLogEntry("Ready to buy/sell claim blocks!");
                }

                // otherwise error message
                else {
                    GriefPrevention.AddLogEntry("ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero.");
                }
            }

            // another error case
            else {
                GriefPrevention.AddLogEntry("ERROR: Vault was unable to find a supported economy plugin.  Either install a Vault-compatible economy plugin, or set both of the economy config variables to zero.");
            }
        }
        MetaHandler = new ClaimMetaHandler();
        try {
            new File(DataStore.configFilePath).delete();
            outConfig.save(new File(DataStore.configFilePath).getAbsolutePath());
        } catch (IOException exx) {
            this.log.log(Level.SEVERE, "Failed to save primary configuration file:" + DataStore.configFilePath);
        }
    }

    void handleClaimClean(Claim c, MaterialInfo source, MaterialInfo target, Player player) {
        Location lesser = c.getLesserBoundaryCorner();
        Location upper = c.getGreaterBoundaryCorner();
        System.out.println("handleClaimClean:" + source.typeID + " to " + target.typeID);

        for (int x = lesser.getBlockX(); x <= upper.getBlockX(); x++) {
            for (int y = 0; y <= 255; y++) {
                for (int z = lesser.getBlockZ(); z <= upper.getBlockZ(); z++) {
                    Location createloc = new Location(lesser.getWorld(), x, y, z);
                    Block acquired = lesser.getWorld().getBlockAt(createloc);
                    if (acquired.getTypeId() == source.typeID && acquired.getData() == source.data) {
                        acquired.setTypeIdAndData(target.typeID, target.data, true);
                    }
                }
            }
        }
    }

    // handles slash commands
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        return commandHandler.onCommand(sender, cmd, commandLabel, args);
    }


    /**
     * transfers a number of claim blocks from a source player to a  target player.
     *
     * @param source player name.
     * @param target Player name.
     * @return number of claim blocks transferred.
     */
    synchronized int transferClaimBlocks(String source, String target, int DesiredAmount) {
        // TODO Auto-generated method stub

        // transfer claim blocks from source to target, return number of claim blocks transferred.
        PlayerData playerData = this.dataStore.getPlayerData(source);
        PlayerData receiverData = this.dataStore.getPlayerData(target);
        if (playerData != null && receiverData != null) {
            int xferamount = Math.min(playerData.accruedClaimBlocks, DesiredAmount);
            playerData.accruedClaimBlocks -= xferamount;
            receiverData.accruedClaimBlocks += xferamount;
            return xferamount;
        }
        return 0;
  }


    /**
     * Creates a friendly Location string for the given Location.
     *
     * @param location Location to retrieve a string for.
     * @return a formatted String to be shown to a user or for a log file depicting the approximate given location.
     */
    public static String getfriendlyLocationString(Location location) {
        return location.getWorld().getName() + "(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ")";
    }

    boolean abandonClaimHandler(Player player, boolean deleteTopLevelClaim) {
        PlayerData playerData = this.dataStore.getPlayerData(player.getName());

        WorldConfig wc = getWorldCfg(player.getWorld());

        // which claim is being abandoned?
        Claim claim = this.dataStore.getClaimAt(player.getLocation(), true /*ignore height*/, null);
        if (claim == null) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.AbandonClaimMissing);
            return true;
        }
        int claimarea = claim.getArea();
        // retrieve (1-abandonclaimration)*totalarea to get amount to subtract from the accrued claim blocks
        // after we delete the claim.
        int costoverhead = (int) Math.floor((double) claimarea * (1 - wc.getClaimsAbandonReturnRatio()));
        System.out.println("costoverhead:" + costoverhead);


        // verify ownership
        if (claim.allowEdit(player) != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NotYourClaim);
        }

        // don't allow abandon of claims if not configured to allow.
        else if (!wc.getAllowUnclaim()) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoCreativeUnClaim);
        }

        // warn if has children and we're not explicitly deleting a top level claim
        else if (claim.children.size() > 0 && !deleteTopLevelClaim) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.DeleteTopLevelClaim);
            return true;
        }

        // if the claim is locked, let's warn the player and give them a chance to back out
        else if (!playerData.warnedAboutMajorDeletion && claim.neverdelete) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.ConfirmAbandonLockedClaim);
            playerData.warnedAboutMajorDeletion = true;
        }
        // if auto-restoration is enabled,
        else if (!playerData.warnedAboutMajorDeletion && wc.getClaimsAbandonNatureRestoration()) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.AbandonClaimRestoreWarning);
            playerData.warnedAboutMajorDeletion = true;
        } else if (!playerData.warnedAboutMajorDeletion && costoverhead != claimarea) {
            playerData.warnedAboutMajorDeletion = true;
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.AbandonCostWarning, String.valueOf(costoverhead));
        }
        // if the claim has lots of surface water or some surface lava, warn the player it will be cleaned up
        else if (!playerData.warnedAboutMajorDeletion && claim.hasSurfaceFluids() && claim.parent == null) {
            GriefPrevention.sendMessage(player, TextMode.Warn, Messages.ConfirmFluidRemoval);
            playerData.warnedAboutMajorDeletion = true;
        } else {
            // delete it
            // Only do water/lava cleanup when it's a top level claim.
            if (claim.parent == null) {
                claim.removeSurfaceFluids(null);
            }
            // retrieve area of this claim...


            if (!this.dataStore.deleteClaim(claim, player)) {
                // cancelled!
                // assume the event called will show an appropriate message...
                return false;
            }

            // if in a creative mode world, restore the claim area
            // CHANGE: option is now determined by configuration options.
            // if we are in a creative world and the creative Abandon Nature restore option is enabled,
            // or if we are in a survival world and the creative Abandon Nature restore option is enabled,
            // then perform the restoration.
            if ((wc.getClaimsAbandonNatureRestoration())) {

                GriefPrevention.AddLogEntry(player.getName() + " abandoned a claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.UnclaimCleanupWarning);
                GriefPrevention.instance.restoreClaim(claim, 20L * 60 * 2);
            }
            // remove the interest cost, and message the player.
            if (costoverhead > 0) {
                playerData.accruedClaimBlocks -= costoverhead;
                //
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.AbandonCost, 0, String.valueOf(costoverhead));
            }
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            // tell the player how many claim blocks he has left
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AbandonSuccess, 0, String.valueOf(remainingBlocks));

            // revert any current visualization
            Visualization.Revert(player);

            playerData.warnedAboutMajorDeletion = false;
        }
        return true;
    }


    // helper method to resolve a player by name
    public OfflinePlayer resolvePlayer(String name) {
        // try online players first
        Player player = this.getServer().getPlayer(name);
        if (player != null) return player;

        // then search offline players
        OfflinePlayer[] offlinePlayers = this.getServer().getOfflinePlayers();
        for (int i = 0; i < offlinePlayers.length; i++) {
            if (offlinePlayers[i].getName().equalsIgnoreCase(name)) {
                return offlinePlayers[i];
            }
        }

        // if none found, return null
        return null;
    }

    public void onDisable() {
        // save data for any online players
        Player[] players = this.getServer().getOnlinePlayers();
        for (int i = 0; i < players.length; i++) {
            Player player = players[i];
            String playerName = player.getName();
            PlayerData playerData = this.dataStore.getPlayerData(playerName);
            this.dataStore.savePlayerData(playerName, playerData);
        }
        // cancel ALL pending tasks.
        Bukkit.getScheduler().cancelTasks(this);
        this.dataStore.close();

        AddLogEntry("GriefPrevention disabled.");
    }

    // called when a player spawns, applies protection for that player if necessary
    public void checkPvpProtectionNeeded(Player player) {
        WorldConfig wc = GriefPrevention.instance.getWorldCfg(player.getWorld());
        // if pvp is disabled, do nothing
        if (!player.getWorld().getPVP()) return;

        // if player is in creative mode, do nothing
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // if anti spawn camping feature is not enabled, do nothing
        if (!wc.getProtectFreshSpawns()) return;

        // if the player has the damage any player permission enabled, do nothing
        if (player.hasPermission("griefprevention.nopvpimmunity")) return;

        // check inventory for well, anything
        PlayerInventory inventory = player.getInventory();
        ItemStack[] armorStacks = inventory.getArmorContents();

        // check armor slots, stop if any items are found
        for (int i = 0; i < armorStacks.length; i++) {
            if (!(armorStacks[i] == null || armorStacks[i].getType() == Material.AIR)) return;
        }

        // check other slots, stop if any items are found
        ItemStack[] generalStacks = inventory.getContents();
        for (int i = 0; i < generalStacks.length; i++) {
            if (!(generalStacks[i] == null || generalStacks[i].getType() == Material.AIR)) return;
        }

        // otherwise, apply immunity
        PlayerData playerData = this.dataStore.getPlayerData(player.getName());
        playerData.pvpImmune = true;

        // inform the player
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.PvPImmunityStart);
    }

    // checks whether players can create claims in a world
    public boolean claimsEnabledForWorld(World world) {
        return this.getWorldCfg(world).getClaimsEnabled();
    }

    // checks whether players siege in a world
    public boolean siegeEnabledForWorld(World world) {
        return this.getWorldCfg(world).getSeigeEnabled();
    }

    // processes broken log blocks to automatically remove floating treetops
    void handleLogBroken(Block block) {
        // find the lowest log in the tree trunk including this log
        Block rootBlock = this.getRootBlock(block);

        // null indicates this block isn't part of a tree trunk
        if (rootBlock == null) return;

        // next step: scan for other log blocks and leaves in this tree

        // set boundaries for the scan
        int min_x = rootBlock.getX() - GriefPrevention.TREE_RADIUS;
        int max_x = rootBlock.getX() + GriefPrevention.TREE_RADIUS;
        int min_z = rootBlock.getZ() - GriefPrevention.TREE_RADIUS;
        int max_z = rootBlock.getZ() + GriefPrevention.TREE_RADIUS;
        int max_y = rootBlock.getWorld().getMaxHeight() - 1;

        // keep track of all the examined blocks, and all the log blocks found
        ArrayList<Block> examinedBlocks = new ArrayList<Block>();
        ArrayList<Block> treeBlocks = new ArrayList<Block>();

        // queue the first block, which is the block immediately above the player-chopped block
        ConcurrentLinkedQueue<Block> blocksToExamine = new ConcurrentLinkedQueue<Block>();
        blocksToExamine.add(rootBlock);
        examinedBlocks.add(rootBlock);

        boolean hasLeaves = false;

        while (!blocksToExamine.isEmpty()) {
            // pop a block from the queue
            Block currentBlock = blocksToExamine.remove();

            // if this is a log block, determine whether it should be chopped
            if (currentBlock.getType() == Material.LOG) {
                boolean partOfTree = false;

                // if it's stacked with the original chopped block, the answer is always yes
                if (currentBlock.getX() == block.getX() && currentBlock.getZ() == block.getZ()) {
                    partOfTree = true;
                }

                // otherwise find the block underneath this stack of logs
                else {
                    Block downBlock = currentBlock.getRelative(BlockFace.DOWN);
                    while (downBlock.getType() == Material.LOG) {
                        downBlock = downBlock.getRelative(BlockFace.DOWN);
                    }

                    // if it's air or leaves, it's okay to chop this block
                    // this avoids accidentally chopping neighboring trees which are close enough to touch their leaves to ours
                    if (downBlock.getType() == Material.AIR || downBlock.getType() == Material.LEAVES) {
                        partOfTree = true;
                    }

                    // otherwise this is a stack of logs which touches a solid surface
                    // if it's close to the original block's stack, don't clean up this tree (just stop here)
                    else {
                        if (Math.abs(downBlock.getX() - block.getX()) <= 1 && Math.abs(downBlock.getZ() - block.getZ()) <= 1)
                            return;
                    }
                }

                if (partOfTree) {
                    treeBlocks.add(currentBlock);
                }
            }

            // if this block is a log OR a leaf block, also check its neighbors
            if (currentBlock.getType() == Material.LOG || currentBlock.getType() == Material.LEAVES) {
                if (currentBlock.getType() == Material.LEAVES) {
                    hasLeaves = true;
                }

                Block[] neighboringBlocks = new Block[] {
                    currentBlock.getRelative(BlockFace.EAST),
                    currentBlock.getRelative(BlockFace.WEST),
                    currentBlock.getRelative(BlockFace.NORTH),
                    currentBlock.getRelative(BlockFace.SOUTH),
                    currentBlock.getRelative(BlockFace.UP),
                    currentBlock.getRelative(BlockFace.DOWN)
                };

                for (int i = 0; i < neighboringBlocks.length; i++) {
                    Block neighboringBlock = neighboringBlocks[i];

                    // if the neighboringBlock is out of bounds, skip it
                    if (neighboringBlock.getX() < min_x || neighboringBlock.getX() > max_x || neighboringBlock.getZ() < min_z || neighboringBlock.getZ() > max_z || neighboringBlock.getY() > max_y)
                        continue;

                    // if we already saw this block, skip it
                    if (examinedBlocks.contains(neighboringBlock)) continue;

                    // mark the block as examined
                    examinedBlocks.add(neighboringBlock);

                    // if the neighboringBlock is a leaf or log, put it in the queue to be examined later
                    if (neighboringBlock.getType() == Material.LOG || neighboringBlock.getType() == Material.LEAVES) {
                        blocksToExamine.add(neighboringBlock);
                    }

                    // if we encounter any player-placed block type, bail out (don't automatically remove parts of this tree, it might support a treehouse!)
                    else if (this.isPlayerBlock(neighboringBlock)) {
                        return;
                    }
                }
            }
        }

        // if it doesn't have leaves, it's not a tree, so don't clean it up
        if (hasLeaves) {
            // schedule a cleanup task for later, in case the player leaves part of this tree hanging in the air
            TreeCleanupTask cleanupTask = new TreeCleanupTask(block, rootBlock, treeBlocks, rootBlock.getData());

            // 20L ~ 1 second, so 2 mins = 120 seconds ~ 2400L
            GriefPrevention.instance.getServer().getScheduler().scheduleSyncDelayedTask(GriefPrevention.instance, cleanupTask, 2400L);
        }
    }

    // helper for above, finds the "root" of a stack of logs
    // will return null if the stack is determined to not be a natural tree
    private Block getRootBlock(Block logBlock) {
        if (logBlock.getType() != Material.LOG) return null;

        // run down through log blocks until finding a non-log block
        Block underBlock = logBlock.getRelative(BlockFace.DOWN);
        while (underBlock.getType() == Material.LOG) {
            underBlock = underBlock.getRelative(BlockFace.DOWN);
        }

        // if this is a standard tree, that block MUST be dirt
        if (underBlock.getType() != Material.DIRT) return null;

        // run up through log blocks until finding a non-log block
        Block aboveBlock = logBlock.getRelative(BlockFace.UP);
        while (aboveBlock.getType() == Material.LOG) {
            aboveBlock = aboveBlock.getRelative(BlockFace.UP);
        }

        // if this is a standard tree, that block MUST be air or leaves
        if (aboveBlock.getType() != Material.AIR && aboveBlock.getType() != Material.LEAVES) return null;

        return underBlock.getRelative(BlockFace.UP);
    }

    // for sake of identifying trees ONLY, a cheap but not 100% reliable method for identifying player-placed blocks
    private boolean isPlayerBlock(Block block) {
        Material material = block.getType();

        // list of natural blocks which are OK to have next to a log block in a natural tree setting
        if (material == Material.AIR ||
                material == Material.LEAVES ||
                material == Material.LOG ||
                material == Material.DIRT ||
                material == Material.GRASS ||
                material == Material.STATIONARY_WATER ||
                material == Material.BROWN_MUSHROOM ||
                material == Material.RED_MUSHROOM ||
                material == Material.RED_ROSE ||
                material == Material.LONG_GRASS ||
                material == Material.SNOW ||
                material == Material.STONE ||
                material == Material.VINE ||
                material == Material.WATER_LILY ||
                material == Material.YELLOW_FLOWER ||
                material == Material.CLAY) {
            return false;
        } else {
            return true;
        }
    }

    // moves a player from the claim he's in to a nearby wilderness location
    public Location ejectPlayer(Player player) {
        // look for a suitable location
        Location candidateLocation = player.getLocation();
        while (true) {
            Claim claim = null;
            claim = GriefPrevention.instance.dataStore.getClaimAt(candidateLocation, false, null);

            // if there's a claim here, keep looking
            if (claim != null) {
                candidateLocation = new Location(claim.lesserBoundaryCorner.getWorld(), claim.lesserBoundaryCorner.getBlockX() - 1, claim.lesserBoundaryCorner.getBlockY(), claim.lesserBoundaryCorner.getBlockZ() - 1);
                continue;
            }

            // otherwise find a safe place to teleport the player
            else {
                // find a safe height, a couple of blocks above the surface
                GuaranteeChunkLoaded(candidateLocation);
                Block highestBlock = candidateLocation.getWorld().getHighestBlockAt(candidateLocation.getBlockX(), candidateLocation.getBlockZ());
                Location destination = new Location(highestBlock.getWorld(), highestBlock.getX(), highestBlock.getY() + 2, highestBlock.getZ());
                player.teleport(destination);
                return destination;
            }
        }
    }

    // ensures a piece of the managed world is loaded into server memory
    // (generates the chunk if necessary)
    private static void GuaranteeChunkLoaded(Location location) {
        Chunk chunk = location.getChunk();
        while (!chunk.isLoaded() || !chunk.load(true)) ;
    }

    // sends a color-coded message to a player
    public static void sendMessage(Player player, ChatColor color, Messages messageID, String... args) {
        sendMessage(player, color, messageID, 0, args);
    }

    // sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, Messages messageID, long delayInTicks, String... args) {

        String message = GriefPrevention.instance.dataStore.getMessage(messageID, args);
        if (message == null || message.equals("")) return;
        sendMessage(player, color, message, delayInTicks);
    }

    private static String removeColors(String source) {

        for (ChatColor cc : ChatColor.values()) {
            source = source.replace(cc.toString(), "");
        }
        return source;
    }

    // sends a color-coded message to a player
    public static void sendMessage(Player player, ChatColor color, String message) {
        if (player == null) {
            GriefPrevention.AddLogEntry(removeColors(message));
        } else {
            player.sendMessage(color + message);
        }
    }

    static void sendMessage(Player player, ChatColor color, String message, long delayInTicks) {
        SendPlayerMessageTask task = new SendPlayerMessageTask(player, color, message);
        if (delayInTicks > 0) {
            GriefPrevention.instance.getServer().getScheduler().runTaskLater(GriefPrevention.instance, task, delayInTicks);
        } else {
            task.run();
        }
    }

    // determines whether creative anti-grief rules apply at a location
    public boolean creativeRulesApply(Location location) {
        // return this.config_claims_enabledCreativeWorlds.contains(location.getWorld().getName());
        return Configuration.getWorldConfig(location.getWorld()).getCreativeRules();
    }

    public String allowBuild(Player player, Location location) {
        PlayerData playerData = this.dataStore.getPlayerData(player.getName());
        Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
        WorldConfig wc = GriefPrevention.instance.getWorldCfg(player.getWorld());
        // exception: administrators in ignore claims mode and special player accounts created by server mods
        if (playerData.ignoreClaims || wc.getModsIgnoreClaimsAccounts().contains(player.getName())) return null;

        // wilderness rules
        if (claim == null) {
            // no building in the wilderness in creative mode
            if (this.creativeRulesApply(location)) {
                String reason = this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.CreativeBasicsDemoAdvertisement);
                if (player.hasPermission("griefprevention.ignoreclaims"))
                    reason += "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                return reason;
            }

            // no building in survival wilderness when that is configured
            else if (wc.getApplyTrashBlockRules() && wc.getClaimsEnabled()) {
                if (wc.getTrashBlockPlacementBehaviour().Allowed(location, player).Denied())
                    return this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.SurvivalBasicsDemoAdvertisement);
                else
                    return null;
            } else {
                // but it's fine in creative
                return null;
            }
        }

        // if not in the wilderness, then apply claim rules (permissions, etc)
        else {
            // cache the claim for later reference
            playerData.lastClaim = claim;
            return claim.allowBuild(player);
        }
    }

    public String allowBreak(Player player, Location location) {
        PlayerData playerData = this.dataStore.getPlayerData(player.getName());
        Claim claim = this.dataStore.getClaimAt(location, false, playerData.lastClaim);
        WorldConfig wc = GriefPrevention.instance.getWorldCfg(player.getWorld());
        // exception: administrators in ignore claims mode, and special player accounts created by server mods
        if (playerData.ignoreClaims || wc.getModsIgnoreClaimsAccounts().contains(player.getName())) return null;

        // wilderness rules
        if (claim == null) {
            // no building in the wilderness in creative mode
            if (this.creativeRulesApply(location)) {
                String reason = this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.CreativeBasicsDemoAdvertisement);
                if (player.hasPermission("griefprevention.ignoreclaims"))
                    reason += "  " + this.dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                return reason;
            } else if (wc.getApplyTrashBlockRules() && wc.getClaimsEnabled()) {
                return this.dataStore.getMessage(Messages.NoBuildOutsideClaims) + "  " + this.dataStore.getMessage(Messages.SurvivalBasicsDemoAdvertisement);
            }

            // but it's fine in survival mode
            else {
                return null;
            }
        } else {
            // cache the claim for later reference
            playerData.lastClaim = claim;

            // if not in the wilderness, then apply claim rules (permissions, etc)
            return claim.allowBreak(player, location.getBlock());
        }
    }

    // restores nature in multiple chunks, as described by a claim instance
    // this restores all chunks which have ANY number of claim blocks from this claim in them
    // if the claim is still active (in the data store), then the claimed blocks will not be changed (only the area bordering the claim)
    public void restoreClaim(Claim claim, long delayInTicks) {
        // admin claims aren't automatically cleaned up when deleted or abandoned
        if (claim.isAdminClaim()) return;

        // it's too expensive to do this for huge claims
        if (claim.getArea() > 10000) return;

        Chunk lesserChunk = claim.getLesserBoundaryCorner().getChunk();
        Chunk greaterChunk = claim.getGreaterBoundaryCorner().getChunk();

        for (int x = lesserChunk.getX(); x <= greaterChunk.getX(); x++)
            for (int z = lesserChunk.getZ(); z <= greaterChunk.getZ(); z++) {
                Chunk chunk = lesserChunk.getWorld().getChunkAt(x, z);
                this.restoreChunk(chunk, this.getSeaLevel(chunk.getWorld()) - 15, false, delayInTicks, null);
            }
    }

    public void restoreChunk(Chunk chunk, int miny, boolean aggressiveMode, long delayInTicks, Player playerReceivingVisualization) {
        // build a snapshot of this chunk, including 1 block boundary outside of the chunk all the way around
        int maxHeight = chunk.getWorld().getMaxHeight();
        BlockSnapshot[][][] snapshots = new BlockSnapshot[18][maxHeight][18];
        Block startBlock = chunk.getBlock(0, 0, 0);
        Location startLocation = new Location(chunk.getWorld(), startBlock.getX() - 1, 0, startBlock.getZ() - 1);
        for (int x = 0; x < snapshots.length; x++) {
            for (int z = 0; z < snapshots[0][0].length; z++) {
                for (int y = 0; y < snapshots[0].length; y++) {
                    Block block = chunk.getWorld().getBlockAt(startLocation.getBlockX() + x, startLocation.getBlockY() + y, startLocation.getBlockZ() + z);
                    snapshots[x][y][z] = new BlockSnapshot(block.getLocation(), block.getTypeId(), block.getData());
                }
            }
        }

        // create task to process those data in another thread
        Location lesserBoundaryCorner = chunk.getBlock(0, 0, 0).getLocation();
        Location greaterBoundaryCorner = chunk.getBlock(15, 0, 15).getLocation();

        // create task
        // when done processing, this task will create a main thread task to actually update the world with processing results
        RestoreNatureProcessingTask task = new RestoreNatureProcessingTask(snapshots, miny, chunk.getWorld().getEnvironment(), lesserBoundaryCorner.getBlock().getBiome(), lesserBoundaryCorner, greaterBoundaryCorner, this.getSeaLevel(chunk.getWorld()), aggressiveMode, GriefPrevention.instance.creativeRulesApply(lesserBoundaryCorner), playerReceivingVisualization);
        GriefPrevention.instance.getServer().getScheduler().runTaskLaterAsynchronously(GriefPrevention.instance, task, delayInTicks);
    }

    public void parseMaterialListFromConfig(List<String> stringsToParse, MaterialCollection materialCollection) {
        materialCollection.clear();

        // for each string in the list
        for (int i = 0; i < stringsToParse.size(); i++) {
            // try to parse the string value into a material info
            MaterialInfo materialInfo = MaterialInfo.fromString(stringsToParse.get(i));

            // null value returned indicates an error parsing the string from the config file
            if (materialInfo == null) {
                // show error in log
                GriefPrevention.AddLogEntry("ERROR: Unable to read a material entry from the config file.  Please update your config.yml.");

                // update string, which will go out to config file to help user find the error entry
                if (!stringsToParse.get(i).contains("can't")) {
                    stringsToParse.set(i, stringsToParse.get(i) + "     <-- can't understand this entry, see BukkitDev documentation");
                }
            }

            // otherwise store the valid entry in config data
            else {
                materialCollection.Add(materialInfo);
            }
        }
    }

    public int getSeaLevel(World world) {
        int overrideValue = getWorldCfg(world).getSeaLevelOverride();
        return overrideValue;
    }
}