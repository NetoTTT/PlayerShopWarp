package PlayerShopWarp.playerShopWarp.commands;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import PlayerShopWarp.playerShopWarp.gui.ShopWarpGUI;
import PlayerShopWarp.playerShopWarp.models.ShopWarp;
import PlayerShopWarp.playerShopWarp.utils.MessageUtils;
import PlayerShopWarp.playerShopWarp.utils.SafetyScanner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShopWarpCommand implements CommandExecutor, TabCompleter {

    private final PlayerShopWarp plugin;

    public ShopWarpCommand(PlayerShopWarp plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando só pode ser executado por jogadores.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Abre a GUI principal
            plugin.getShopWarpGUI().openMainGUI(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                showHelp(player);
                break;

            case "create":
            case "set":
                if (!player.hasPermission("playershopwarp.player.create")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.sendMessage(player, "messages.create-usage");
                    return true;
                }

                String name = args[1];
                String description = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : "Loja de " + player.getName(); // Descrição padrão

                // Verifica se categorias estão habilitadas
                if (plugin.getConfigManager().areCategoriesEnabled()) {
                    // Inicia o processo pela GUI para selecionar categoria
                    ShopWarpGUI gui = plugin.getShopWarpGUI(); // Obter instância da GUI uma vez
                    UUID playerUUID = player.getUniqueId();

                    // Limpa qualquer processo anterior para evitar conflitos
                    gui.clearCreationProcess(playerUUID);

                    // --- CORREÇÃO AQUI: Usa os métodos públicos ---
                    gui.setPendingWarpName(playerUUID, name);
                    gui.setPendingWarpDescription(playerUUID, description);
                    gui.setCreationProcessState(playerUUID, "description"); // Estado inicial pronto para categoria
                    // --- FIM DA CORREÇÃO ---

                    // Abre a GUI de seleção de categoria
                    gui.openWarpCategoryGUI(player);
                    MessageUtils.sendMessage(player, "&eSelecione uma categoria para sua loja na interface.");

                } else {
                    // Categorias desabilitadas, cria diretamente com categoria null
                    plugin.getShopWarpManager().createWarp(player, name, description, null);
                }
                break; // Fim do case "create"

            case "delete":
            case "remove":
                if (!player.hasPermission("playershopwarp.player.delete")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.sendMessage(player, "messages.delete-usage");
                    return true;
                }

                // Se o jogador é admin, pode deletar pelo nome diretamente
                if (player.hasPermission("playershopwarp.admin")) {
                    String warpName = args[1];
                    ShopWarp warpToDelete = null;

                    for (ShopWarp warp : plugin.getShopWarpManager().getAllWarps()) {
                        if (warp.getName().equalsIgnoreCase(warpName)) {
                            warpToDelete = warp;
                            break;
                        }
                    }

                    if (warpToDelete != null) {
                        plugin.getShopWarpManager().deleteWarp(player, warpToDelete.getId());
                    } else {
                        MessageUtils.sendMessage(player, "messages.warp-not-found");
                    }
                } else {
                    // Jogador normal só pode deletar suas próprias warps
                    String warpName = args[1];
                    ShopWarp warpToDelete = null;

                    for (ShopWarp warp : plugin.getShopWarpManager().getPlayerWarps(player.getUniqueId())) {
                        if (warp.getName().equalsIgnoreCase(warpName)) {
                            warpToDelete = warp;
                            break;
                        }
                    }

                    if (warpToDelete != null) {
                        plugin.getShopWarpManager().deleteWarp(player, warpToDelete.getId());
                    } else {
                        MessageUtils.sendMessage(player, "messages.warp-not-found");
                    }
                }
                break;

            case "list":
                if (!player.hasPermission("playershopwarp.player.list")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                // Lista todas as warps do jogador
                List<ShopWarp> playerWarps = plugin.getShopWarpManager().getPlayerWarps(player.getUniqueId());
                if (playerWarps.isEmpty()) {
                    MessageUtils.sendMessage(player, "messages.no-warps");
                    return true;
                }

                MessageUtils.sendMessage(player, "messages.warp-list-header");
                for (ShopWarp warp : playerWarps) {
                    player.sendMessage(
                            MessageUtils.colorize("&e- &f" + warp.getName() + " &7(" + warp.getDescription() + ")"));
                }
                break;

            case "tp":
            case "teleport":
                if (!player.hasPermission("playershopwarp.player.teleport")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.sendMessage(player, "messages.teleport-usage");
                    return true;
                }

                String warpNameToTp = args[1];
                ShopWarp warpToTp = null;

                for (ShopWarp warp : plugin.getShopWarpManager().getAllWarps()) {
                    if (warp.getName().equalsIgnoreCase(warpNameToTp)) {
                        warpToTp = warp;
                        break;
                    }
                }

                if (warpToTp != null) {
                    plugin.getShopWarpManager().teleportToWarp(player, warpToTp.getId());
                } else {
                    MessageUtils.sendMessage(player, "messages.warp-not-found");
                }
                break;

            case "announce":
            case "anunciar":
                if (!player.hasPermission("playershopwarp.player.announce")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                if (args.length < 3) {
                    MessageUtils.sendMessage(player, "messages.announce-usage");
                    return true;
                }

                String warpNameToAnnounce = args[1];
                String announceMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                ShopWarp warpToAnnounce = null;
                for (ShopWarp warp : plugin.getShopWarpManager().getPlayerWarps(player.getUniqueId())) {
                    if (warp.getName().equalsIgnoreCase(warpNameToAnnounce)) {
                        warpToAnnounce = warp;
                        break;
                    }
                }

                if (warpToAnnounce != null) {
                    plugin.getShopWarpManager().announceShop(player, warpToAnnounce.getId(), announceMessage);
                } else {
                    MessageUtils.sendMessage(player, "messages.warp-not-found");
                }
                break;

            case "reload":
                if (!player.hasPermission("playershopwarp.admin.reload")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                plugin.getConfigManager().loadAllConfigs();
                MessageUtils.sendMessage(player, "messages.config-reloaded");
                break;

            case "forcetp":
                if (!player.hasPermission("playershopwarp.admin")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                if (args.length < 2) {
                    MessageUtils.sendMessage(player, "messages.forcetp-usage");
                    return true;
                }

                String warpNameToForceTP = args[1];
                ShopWarp warpToForceTP = null;

                for (ShopWarp warp : plugin.getShopWarpManager().getAllWarps()) {
                    if (warp.getName().equalsIgnoreCase(warpNameToForceTP)) {
                        warpToForceTP = warp;
                        break;
                    }
                }

                if (warpToForceTP != null) {
                    // Teleporta sem verificar segurança
                    player.teleport(warpToForceTP.getLocation());
                    MessageUtils.sendMessage(player, "messages.force-teleported",
                            new String[][] { { "{name}", warpToForceTP.getName() } });
                } else {
                    MessageUtils.sendMessage(player, "messages.warp-not-found");
                }
                break;

            case "scan":
            case "verificar":
                if (!player.hasPermission("playershopwarp.player.scan")) {
                    MessageUtils.sendMessage(player, "messages.no-permission");
                    return true;
                }

                // Cria um scanner e escaneia a área
                SafetyScanner scanner = new SafetyScanner(plugin);
                SafetyScanner.ScanResult result = scanner.scanArea(player);

                // Exibe o resultado
                player.sendMessage("");
                player.sendMessage(MessageUtils.colorize("&8&l[&6&lPlayerShopWarp&8&l] &eEscaneamento de Área:"));
                player.sendMessage(
                        MessageUtils.colorize("&7Escaneando área de 16x16 blocos ao redor da sua posição..."));
                player.sendMessage("");

                if (!result.hasIssues()) {
                    player.sendMessage(MessageUtils
                            .colorize("&a✓ Nenhum problema encontrado! Este local parece seguro para uma warp."));
                } else {
                    // Primeiro exibe os perigos
                    boolean hasDangers = false;
                    for (SafetyScanner.ScanResult.Issue issue : result.getIssues()) {
                        if (issue.getType() == SafetyScanner.ScanResult.IssueType.DANGER) {
                            hasDangers = true;
                            player.sendMessage(
                                    MessageUtils.colorize(issue.getType().getPrefix() + "&c" + issue.getTitle()));
                            player.sendMessage(MessageUtils.colorize("  &7" + issue.getDescription()));
                        }
                    }

                    if (hasDangers) {
                        player.sendMessage("");
                    }

                    // Depois os avisos
                    boolean hasWarnings = false;
                    for (SafetyScanner.ScanResult.Issue issue : result.getIssues()) {
                        if (issue.getType() == SafetyScanner.ScanResult.IssueType.WARNING) {
                            hasWarnings = true;
                            player.sendMessage(
                                    MessageUtils.colorize(issue.getType().getPrefix() + "&e" + issue.getTitle()));
                            player.sendMessage(MessageUtils.colorize("  &7" + issue.getDescription()));
                        }
                    }

                    if (hasWarnings) {
                        player.sendMessage("");
                    }

                    // Depois as informações
                    boolean hasInfo = false;
                    for (SafetyScanner.ScanResult.Issue issue : result.getIssues()) {
                        if (issue.getType() == SafetyScanner.ScanResult.IssueType.INFO
                                || issue.getType() == SafetyScanner.ScanResult.IssueType.GOOD) {
                            hasInfo = true;
                            player.sendMessage(
                                    MessageUtils.colorize(issue.getType().getPrefix() + "&f" + issue.getTitle()));
                            player.sendMessage(MessageUtils.colorize("  &7" + issue.getDescription()));
                        }
                    }

                    player.sendMessage("");

                    // Resumo
                    if (result.hasDangers()) {
                        player.sendMessage(MessageUtils.colorize("&c⚠ Este local NÃO é seguro para criar uma warp!"));
                    } else if (result.hasWarnings()) {
                        player.sendMessage(
                                MessageUtils.colorize("&e⚠ Este local pode ser usado, mas tem alguns riscos."));
                    } else {
                        player.sendMessage(MessageUtils.colorize("&a✓ Este local parece adequado para uma warp."));
                    }
                }
                break;

            default:
                showHelp(player);
                break;
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(MessageUtils.colorize("&8&l[&6&lPlayerShopWarp&8&l] &eComandos:"));

        if (player.hasPermission("playershopwarp.player.create"))
            player.sendMessage(
                    MessageUtils.colorize("&e/shopwarp create <nome> [descrição] &7- Cria uma warp de loja"));

        if (player.hasPermission("playershopwarp.player.delete"))
            player.sendMessage(MessageUtils.colorize("&e/shopwarp delete <nome> &7- Remove uma warp de loja"));

        if (player.hasPermission("playershopwarp.player.list"))
            player.sendMessage(MessageUtils.colorize("&e/shopwarp list &7- Lista suas warps de loja"));

        if (player.hasPermission("playershopwarp.player.teleport"))
            player.sendMessage(MessageUtils.colorize("&e/shopwarp tp <nome> &7- Teleporta para uma warp de loja"));

        if (player.hasPermission("playershopwarp.player.announce"))
            player.sendMessage(
                    MessageUtils.colorize("&e/shopwarp announce <nome> <mensagem> &7- Anuncia sua loja no chat"));

        player.sendMessage(MessageUtils.colorize("&e/shopwarp &7- Abre o menu de lojas"));

        if (player.hasPermission("playershopwarp.admin.reload"))
            player.sendMessage(MessageUtils.colorize("&e/shopwarp reload &7- Recarrega a configuração"));

        if (player.hasPermission("playershopwarp.player.scan"))
            player.sendMessage(MessageUtils
                    .colorize("&e/shopwarp scan &7- Escaneia a área para verificar se é segura para uma warp"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> commands = new ArrayList<>();

            if (player.hasPermission("playershopwarp.player.create"))
                commands.add("create");

            if (player.hasPermission("playershopwarp.player.delete"))
                commands.add("delete");

            if (player.hasPermission("playershopwarp.player.list"))
                commands.add("list");

            if (player.hasPermission("playershopwarp.player.teleport"))
                commands.add("tp");

            if (player.hasPermission("playershopwarp.player.announce"))
                commands.add("announce");

            commands.add("help");

            if (player.hasPermission("playershopwarp.admin.reload"))
                commands.add("reload");

            if (player.hasPermission("playershopwarp.admin")) {
                commands.add("forcetp");

                if (player.hasPermission("playershopwarp.player.scan"))
                    commands.add("scan");
            }

            return filterStartingWith(args[0], commands);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete") && player.hasPermission("playershopwarp.player.delete")) {
                // Se for admin, mostra todas as warps, senão só as do jogador
                if (player.hasPermission("playershopwarp.admin")) {
                    return filterStartingWith(args[1], plugin.getShopWarpManager().getAllWarps().stream()
                            .map(ShopWarp::getName)
                            .collect(Collectors.toList()));
                } else {
                    return filterStartingWith(args[1],
                            plugin.getShopWarpManager().getPlayerWarps(player.getUniqueId()).stream()
                                    .map(ShopWarp::getName)
                                    .collect(Collectors.toList()));
                }
            }

            if (args[0].equalsIgnoreCase("tp") && player.hasPermission("playershopwarp.player.teleport")) {
                return filterStartingWith(args[1], plugin.getShopWarpManager().getAllWarps().stream()
                        .map(ShopWarp::getName)
                        .collect(Collectors.toList()));
            }

            if (args[0].equalsIgnoreCase("announce") && player.hasPermission("playershopwarp.player.announce")) {
                return filterStartingWith(args[1],
                        plugin.getShopWarpManager().getPlayerWarps(player.getUniqueId()).stream()
                                .map(ShopWarp::getName)
                                .collect(Collectors.toList()));
            }
        }

        return completions;
    }

    private List<String> filterStartingWith(String prefix, List<String> options) {
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}