package net.distrocraft.plugin.command;

import com.google.gson.JsonObject;
import net.distrocraft.plugin.coordinator.PluginConnectedClient;
import net.distrocraft.plugin.coordinator.PluginCoordinator;
import net.distrocraft.plugin.task.PluginTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DistrocraftCommand implements CommandExecutor, TabCompleter {

    private final PluginCoordinator coordinator;

    public DistrocraftCommand(PluginCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("distrocraft.admin")) {
            sender.sendMessage("\u00a7cYou don't have permission to use this command.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        return switch (args[0].toLowerCase()) {
            case "status" -> {
                sender.sendMessage("\u00a76[Distrocraft] \u00a7fCoordinator status:");
                sender.sendMessage("\u00a77  Connected clients : \u00a7a" + coordinator.getClientCount());
                sender.sendMessage("\u00a77  Pending tasks     : \u00a7e" + coordinator.getPendingCount());
                yield true;
            }
            case "list" -> {
                Collection<PluginConnectedClient> clients = coordinator.getClients();
                if (clients.isEmpty()) {
                    sender.sendMessage("\u00a76[Distrocraft] \u00a77No clients connected.");
                } else {
                    sender.sendMessage("\u00a76[Distrocraft] \u00a7fConnected clients (" + clients.size() + "):");
                    for (PluginConnectedClient c : clients) {
                        String name = c.getPlayerName() != null ? c.getPlayerName() : "(app)";
                        sender.sendMessage(String.format("\u00a77  %-20s threads=\u00a7a%d\u00a77 active=\u00a7e%d",
                                name, c.getMaxThreads(), c.getActiveTasks().size()));
                    }
                }
                yield true;
            }
            case "submit" -> {
                if (args.length < 3) { sender.sendMessage("\u00a7cUsage: /dc submit <kind> <key>=<value> ..."); yield true; }
                handleSubmit(sender, args);
                yield true;
            }
            case "help" -> { sendHelp(sender); yield true; }
            default -> { sendHelp(sender); yield false; }
        };
    }

    private void handleSubmit(CommandSender sender, String[] args) {
        String kind = args[1].toUpperCase();
        JsonObject payload = new JsonObject();
        for (int i = 2; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            if (kv.length == 2) {
                try { payload.addProperty(kv[0], Long.parseLong(kv[1])); }
                catch (NumberFormatException e1) {
                    try { payload.addProperty(kv[0], Double.parseDouble(kv[1])); }
                    catch (NumberFormatException e2) { payload.addProperty(kv[0], kv[1]); }
                }
            }
        }
        PluginTask task = PluginTask.of(kind, payload);
        coordinator.submitTask(task);
        sender.sendMessage("\u00a76[Distrocraft] \u00a7fQueued " + kind + " id=" + task.getId());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("\u00a76[Distrocraft] \u00a7fCommands:");
        sender.sendMessage("\u00a77  /dc status                           \u00a7f\u2014 overall stats");
        sender.sendMessage("\u00a77  /dc list                             \u00a7f\u2014 list clients");
        sender.sendMessage("\u00a77  /dc submit <KIND> <k>=<v> ...        \u00a7f\u2014 queue any task kind");
        sender.sendMessage("\u00a77    e.g. /dc submit CHUNK_GEN cx=0 cz=0 seed=42");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List.of("status", "list", "submit", "help").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .forEach(completions::add);
        }
        return completions;
    }
}
