package me.ryanhamshire.GriefPrevention.commands;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.messages.Messages;
import me.ryanhamshire.GriefPrevention.data.PlayerData;
import me.ryanhamshire.GriefPrevention.messages.TextMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.List;


public class IgnoreClaims extends BaseCommand {
    public IgnoreClaims(GriefPrevention plugin) {
        super(plugin, "ignoreclaims");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, TextMode.ERROR, Messages.CommandRequiresPlayer);
            return true;
        }
        Player player = (Player) sender;

        PlayerData playerData = plugin.getDataStore().getPlayerData(player.getName());
        playerData.setIgnoreClaims(!playerData.isIgnoreClaims());

        //toggle ignore claims mode on or off
        if (!playerData.isIgnoreClaims()) {
            plugin.sendMessage(player, TextMode.SUCCESS, Messages.RespectingClaims);
        } else {
            plugin.sendMessage(player, TextMode.SUCCESS, Messages.IgnoringClaims);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, LinkedList<String> args) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
