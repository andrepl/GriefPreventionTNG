package com.norcode.bukkit.griefprevention.commands;

import com.norcode.bukkit.griefprevention.GriefPreventionTNG;
import com.norcode.bukkit.griefprevention.data.Claim;
import com.norcode.bukkit.griefprevention.messages.Messages;
import com.norcode.bukkit.griefprevention.messages.TextMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;

public class ClaimExplosions extends BaseClaimCommand {

    public ClaimExplosions(GriefPreventionTNG plugin) {
        super(plugin, "claimexplosions", Messages.ClaimMissing);
    }

    @Override
    public boolean onCommand(Player player, Claim claim, Command cmd, String label, LinkedList<String> args) {
        String noBuildReason = claim.allowBuild(player);
        if (noBuildReason != null) {
            plugin.sendMessage(player, TextMode.ERROR, noBuildReason);
            return true;
        }
        if (claim.isExplosivesAllowed()) {
            claim.setExplosivesAllowed(false);
            plugin.sendMessage(player, TextMode.SUCCESS, Messages.ExplosivesDisabled);
        } else {
            claim.setExplosivesAllowed(true);
            plugin.sendMessage(player, TextMode.SUCCESS, Messages.ExplosivesEnabled);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        return null;
    }
}
