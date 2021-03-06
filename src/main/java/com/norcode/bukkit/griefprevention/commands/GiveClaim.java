package com.norcode.bukkit.griefprevention.commands;

import com.norcode.bukkit.griefprevention.GriefPreventionTNG;
import com.norcode.bukkit.griefprevention.data.Claim;
import com.norcode.bukkit.griefprevention.messages.Messages;
import com.norcode.bukkit.griefprevention.messages.TextMode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;

public class GiveClaim extends BaseClaimCommand {

    public GiveClaim(GriefPreventionTNG plugin) {
        super(plugin, "giveclaim", Messages.ClaimMissing);
    }

    @Override
    public boolean onCommand(Player player, Claim claim, Command cmd, String label, LinkedList<String> args) {
        //gives a claim to another player. get the source player first.
        if (args.size() == 0) return false;
        Player source = player;
        Player target = Bukkit.getPlayer(args.peek());
        if (target == null) {
            plugin.sendMessage(source, TextMode.ERROR, Messages.PlayerNotFound, args.peek());
            return true;
        }

        //if it's not null, make sure they have either have giveclaim permission or adminclaims permission.
        if (source.hasPermission("griefprevention.giveclaims") || source.hasPermission("griefprevention.adminclaims")) {
            //find the claim at the players location.
            Claim claimToGive = plugin.getDataStore().getClaimAt(source.getLocation(), true, null);
            //if the owner is not the source, they have to have adminclaims permission too.
            if (!claimToGive.getFriendlyOwnerName().equalsIgnoreCase(source.getName())) {
                //if they don't have adminclaims permission, deny it.
                if (!source.hasPermission("griefprevention.adminclaims")) {
                    plugin.sendMessage(source, TextMode.ERROR, Messages.NoAdminClaimsPermission);
                    return true;
                }
            }
            //transfer ownership.
            claimToGive.setOwnerName(target.getName());

            String originalOwner = claimToGive.getFriendlyOwnerName();
            try {
                plugin.getDataStore().changeClaimOwner(claimToGive, target.getName());
                //message both players.
                plugin.sendMessage(source, TextMode.SUCCESS, Messages.GiveSuccessSender, originalOwner, target.getName());
                if (target.isOnline()) {
                    plugin.sendMessage(target, TextMode.SUCCESS, Messages.GiveSuccessTarget, originalOwner);
                }
            } catch (Exception exx) {
                plugin.sendMessage(source, TextMode.ERROR, "Failed to transfer Claim.");
            }
        } else {
            plugin.sendMessage(source, TextMode.ERROR, Messages.NoGiveClaimsPermission);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        return null;
    }
}
