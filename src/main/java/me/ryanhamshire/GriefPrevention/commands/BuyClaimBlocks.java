package me.ryanhamshire.GriefPrevention.commands;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;

public class BuyClaimBlocks extends BaseCommand {

    public BuyClaimBlocks(GriefPrevention plugin) {
        super(plugin, "buyclaimblocks");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        if (!(sender instanceof Player)) {
            GriefPrevention.sendMessage(sender, TextMode.Err, Messages.CommandRequiresPlayer);
            return true;
        }

        Player player = (Player) sender;

        //if economy is disabled, don't do anything
        if (GriefPrevention.economy == null) {
            GriefPrevention.sendMessage(sender, TextMode.Err, Messages.BuySellNotConfigured);
            return true;
        }

        if (!player.hasPermission("griefprevention.buysellclaimblocks")) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
            return true;
        }

        //if purchase disabled, send error message
        if (plugin.config_economy_claimBlocksPurchaseCost == 0) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.OnlySellBlocks);
            return true;
        }

        //if no parameter, just tell player cost per block and balance
        if (args.size() != 1) {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.BlockPurchaseCost, String.valueOf(plugin.config_economy_claimBlocksPurchaseCost), String.valueOf(GriefPrevention.economy.getBalance(player.getName())));
            return false;
        } else {
            //determine max purchasable blocks
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getName());
            int maxPurchasable = plugin.config_claims_maxAccruedBlocks - playerData.accruedClaimBlocks;

            //if the player is at his max, tell him so
            if (maxPurchasable <= 0) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimBlockLimit);
                return true;
            }

            //try to parse number of blocks
            int blockCount;
            try {
                blockCount = Integer.parseInt(args.peek());
            } catch (NumberFormatException numberFormatException) {
                return false;  //causes usage to be displayed
            }

            if (blockCount <= 0) {
                return false;
            }

            //correct block count to max allowed
            if (blockCount > maxPurchasable) {
                blockCount = maxPurchasable;
            }

            //if the player can't afford his purchase, send error message
            double balance = plugin.economy.getBalance(player.getName());
            double totalCost = blockCount * plugin.config_economy_claimBlocksPurchaseCost;
            if (totalCost > balance) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.InsufficientFunds, String.valueOf(totalCost), String.valueOf(balance));
            }
            //otherwise carry out transaction
            else {
                //withdraw cost
                plugin.economy.withdrawPlayer(player.getName(), totalCost);

                //add blocks
                playerData.accruedClaimBlocks += blockCount;
                plugin.dataStore.savePlayerData(player.getName(), playerData);

                //inform player
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.PurchaseConfirmation, String.valueOf(totalCost), String.valueOf(playerData.getRemainingClaimBlocks()));
            }
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
