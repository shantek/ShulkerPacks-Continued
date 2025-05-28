package io.shantek;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandReload implements CommandExecutor {

    private ShulkerPacksContinued main = ShulkerPacksContinued.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandlabel, String[] args) {
        if (sender.isOp()) {
            if (args.length == 0) {
                sender.sendMessage("Invalid arguments. Usage: /shulkerpacks reload");
            } else {
                if (args[0].equalsIgnoreCase("reload")) {
                    ConfigHandler.loadConfig(main);
                    sender.sendMessage(main.prefix + "Reloaded config.");
                }
            }
        }
        return true;
    }
}
