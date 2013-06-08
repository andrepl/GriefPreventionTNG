package me.ryanhamshire.GriefPrevention.commands;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.TextMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;

public class ClaimExplosions extends BaseClaimCommand {

    public ClaimExplosions(GriefPrevention plugin) {
        super(plugin, "claimexplosions", Messages.ClaimMissing);
    }

    @Override
    public boolean onCommand(Player player, Claim claim, Command cmd, String label, LinkedList<String> args) {
        String noBuildReason = claim.allowBuild(player);
        if (noBuildReason != null) {
            GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
            return true;
        }
        if (claim.areExplosivesAllowed) {
            claim.areExplosivesAllowed = false;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.ExplosivesDisabled);
        } else {
            claim.areExplosivesAllowed = true;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.ExplosivesEnabled);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
