package me.ryanhamshire.GriefPrevention.commands;

import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.*;

public class CommandHandler implements TabExecutor {
    GriefPrevention plugin;

    private HashMap<String, BaseCommand> commandMap = new HashMap<String, BaseCommand>();

    AbandonClaim cmdAbandonClaim;
    Help cmdHelp;
    ClaimInfo cmdClaimInfo;
    CleanClaim cmdCleanClaim;
    ClearManagers cmdClearManagers;
    IgnoreClaims cmdIgnoreClaims;
    Reload cmdReload;
    GiveClaimBlocks cmdGiveClaimBlocks;
    TransferClaimBlocks cmdTransferClaimBlocks;
    AbandonAllClaims cmdAbandonAllClaims;
    RestoreNature cmdRestoreNature;
    Trust cmdTrust;
    ContainerTrust cmdContainerTrust;
    AccessTrust cmdAccessTrust;
    PermissionTrust cmdPermissionTrust;
    LockClaim cmdLockClaim;
    UnlockClaim cmdUnlockClaim;
    GiveClaim cmdGiveClaim;
    TrustList cmdTrustList;
    Untrust cmdUntrust;
    BuyClaimBlocks cmdBuyClaimBlocks;
    SellClaimBlocks cmdSellClaimBlocks;
    DeleteClaim cmdDeleteClaim;
    DeleteAllClaims cmdDeleteAllClaims;
    ClaimExplosions cmdClaimExplosions;
    ClaimsList cmdClaimsList;
    AdminClaims cmdAdminClaims;
    BasicClaims cmdBasicClaims;
    SubdivideClaims cmdSubdivideClaims;
    Deathblow cmdDeathblow;
    DeleteAllAdminClaims cmdDeleteAllAdminClaims;
    AdjustBonusClaimBlocks cmdAdjustBonusClaimBlocks;
    Trapped cmdTrapped;
    Siege cmdSiege;

    public CommandHandler(GriefPrevention plugin) {
        this.plugin = plugin;

        cmdAbandonClaim = new AbandonClaim(plugin);
        cmdHelp = new Help(plugin);
        cmdClaimInfo = new ClaimInfo(plugin);
        cmdCleanClaim = new CleanClaim(plugin);
        cmdClearManagers = new ClearManagers(plugin);
        cmdIgnoreClaims = new IgnoreClaims(plugin);
        cmdReload = new Reload(plugin);
        cmdGiveClaimBlocks = new GiveClaimBlocks(plugin);
        cmdTransferClaimBlocks = new TransferClaimBlocks(plugin);
        cmdAbandonAllClaims = new AbandonAllClaims(plugin);
        cmdRestoreNature = new RestoreNature(plugin);
        cmdTrust = new Trust(plugin);
        cmdContainerTrust = new ContainerTrust(plugin);
        cmdAccessTrust = new AccessTrust(plugin);
        cmdLockClaim = new LockClaim(plugin);
        cmdUnlockClaim = new UnlockClaim(plugin);
        cmdGiveClaim = new GiveClaim(plugin);
        cmdTrustList = new TrustList(plugin);
        cmdUntrust = new Untrust(plugin);
        cmdBuyClaimBlocks = new BuyClaimBlocks(plugin);
        cmdSellClaimBlocks = new SellClaimBlocks(plugin);
        cmdDeleteClaim = new DeleteClaim(plugin);
        cmdDeleteAllClaims = new DeleteAllClaims(plugin);
        cmdClaimExplosions = new ClaimExplosions(plugin);
        cmdClaimsList = new ClaimsList(plugin);
        cmdAdminClaims = new AdminClaims(plugin);
        cmdBasicClaims = new BasicClaims(plugin);
        cmdSubdivideClaims = new SubdivideClaims(plugin);
        cmdDeathblow = new Deathblow(plugin);
        cmdDeleteAllAdminClaims = new DeleteAllAdminClaims(plugin);
        cmdAdjustBonusClaimBlocks = new AdjustBonusClaimBlocks(plugin);
        cmdTrapped = new Trapped(plugin);
        cmdSiege = new Siege(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        LinkedList<String> params = new LinkedList<String>();
        params.addAll(Arrays.asList(args));
        if (params.size() == 0) {
            return cmdHelp.onCommand(sender, cmdHelp.getPluginCommand(), commandLabel, params);
        }
        BaseCommand bc = commandMap.get(params.peek().toLowerCase());
        if (bc == null) {
            GriefPrevention.sendMessage(sender, TextMode.Err, Messages.UnknownCommand, params.peek());
            return true;
        }
        return bc.onCommand(sender, bc.getPluginCommand(), commandLabel + " " + params.pop(), params);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        LinkedList<String> params = new LinkedList<String>();
        params.addAll(Arrays.asList(args));
        if (args.length == 1) {
            LinkedList<String> results = new LinkedList<String>();
            String permNode = null;
            for (String s: commandMap.keySet()) {
                if (s.startsWith(params.peek().toLowerCase())) {
                    BaseCommand bc = commandMap.get(s);
                    if (sender.hasPermission(bc.getPermissionNode())) {
                        if (s.startsWith(params.peek().toLowerCase())) {
                            results.add(s);
                        }
                    }
                }
            }
            return results;
        } else {
            BaseCommand bc = commandMap.get(params.peek());
            if (bc == null) return null;
            if (sender.hasPermission(bc.getPermissionNode())) {
                return bc.onTabComplete(sender, bc.getPluginCommand(), label + " " + params.peek(), params);
            }
        }
        return null;
    }

    public void registerCommand(BaseCommand baseCommand, String name) {
        this.plugin.getServer().getPluginCommand(name).setExecutor(this);
        if (name.startsWith("gp")) {
            name = name.substring(2);
        }
        commandMap.put(name, baseCommand);
        String[] aliases = baseCommand.getAliases();
        if (aliases != null) {
            for (String alias: aliases) {
                commandMap.put(alias, baseCommand);
            }
        }
    }
}