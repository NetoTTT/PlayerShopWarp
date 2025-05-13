// package PlayerShopWarp.playerShopWarp.listeners;

// import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
// import PlayerShopWarp.playerShopWarp.utils.MessageUtils;

// import org.bukkit.Material;
// import org.bukkit.block.Block;
// import org.bukkit.entity.Player;
// import org.bukkit.event.EventHandler;
// import org.bukkit.event.Listener;
// import org.bukkit.event.block.BlockPlaceEvent;
// import org.bukkit.event.block.SignChangeEvent;
// import org.bukkit.event.inventory.InventoryClickEvent;
// import org.bukkit.event.inventory.InventoryCloseEvent;
// import org.bukkit.event.player.AsyncPlayerChatEvent;
// import org.bukkit.event.player.PlayerQuitEvent;

// import java.util.HashMap;
// import java.util.Map;
// import java.util.UUID;

// public class PlayerListener implements Listener {

//     private final PlayerShopWarp plugin;
//     private final Map<UUID, ChatAction> pendingChatActions;

//     public PlayerListener(PlayerShopWarp plugin) {
//         this.plugin = plugin;
//         this.pendingChatActions = new HashMap<>();
//     }

//     @EventHandler
//     public void onInventoryClick(InventoryClickEvent event) {
//         if (event.getView().getTitle() == null || !(event.getWhoClicked() instanceof Player)) {
//             return;
//         }

//         Player player = (Player) event.getWhoClicked();
//         String currentPage = plugin.getShopWarpGUI().getCurrentPage(player.getUniqueId());

//         if (currentPage == null || currentPage.isEmpty()) {
//             return;
//         }

//         event.setCancelled(true);

//         if (event.getClickedInventory() == event.getView().getTopInventory()) {
//             plugin.getShopWarpGUI().handleInventoryClick(player, event.getSlot(), event.getView().getTitle());
//         }
//     }

//     @EventHandler
//     public void onPlayerChat(AsyncPlayerChatEvent event) {
//         Player player = event.getPlayer();
//         UUID playerId = player.getUniqueId();

//         if (pendingChatActions.containsKey(playerId)) {
//             event.setCancelled(true);

//             ChatAction action = pendingChatActions.get(playerId);
//             pendingChatActions.remove(playerId);

//             String message = event.getMessage();

//             if (action.getType() == ChatActionType.CREATE_WARP) {
//                 // Formato: nome|descrição
//                 String[] parts = message.split("\\|", 2);
//                 String name = parts[0].trim();
//                 String description = parts.length > 1 ? parts[1].trim() : "";

//                 plugin.getServer().getScheduler().runTask(plugin,
//                         () -> plugin.getShopWarpManager().createWarp(player, name, description));
//             } else if (action.getType() == ChatActionType.ANNOUNCE_WARP) {
//                 String warpId = action.getData();

//                 plugin.getServer().getScheduler().runTask(plugin,
//                         () -> plugin.getShopWarpManager().announceShop(player, warpId, message));
//             }
//         }
//     }

//     @EventHandler
//     public void onBlockPlace(BlockPlaceEvent event) {
//         Player player = event.getPlayer();
//         Block block = event.getBlock();
//         UUID playerId = player.getUniqueId();

//         // Verifica se o jogador está no processo de criação via placa
//         String process = plugin.getShopWarpGUI().getCreationProcess(playerId);
//         if (process != null && process.equals("sign") && (block.getType() == Material.OAK_SIGN ||
//                 block.getType() == Material.SPRUCE_SIGN || block.getType() == Material.BIRCH_SIGN ||
//                 block.getType() == Material.JUNGLE_SIGN || block.getType() == Material.ACACIA_SIGN ||
//                 block.getType() == Material.DARK_OAK_SIGN || block.getType() == Material.CRIMSON_SIGN ||
//                 block.getType() == Material.WARPED_SIGN || block.getType() == Material.OAK_WALL_SIGN ||
//                 block.getType() == Material.SPRUCE_WALL_SIGN || block.getType() == Material.BIRCH_WALL_SIGN ||
//                 block.getType() == Material.JUNGLE_WALL_SIGN || block.getType() == Material.ACACIA_WALL_SIGN ||
//                 block.getType() == Material.DARK_OAK_WALL_SIGN || block.getType() == Material.CRIMSON_WALL_SIGN ||
//                 block.getType() == Material.WARPED_WALL_SIGN)) {

//             // A placa foi colocada, o evento SignChangeEvent será chamado em seguida
//             plugin.getShopWarpGUI().setSignBlock(playerId, block.getLocation());
//         }
//     }

//     @EventHandler
//     public void onSignChange(SignChangeEvent event) {
//         Player player = event.getPlayer();
//         UUID playerId = player.getUniqueId();

//         // Verifica se o jogador está no processo de criação via placa
//         String process = plugin.getShopWarpGUI().getCreationProcess(playerId);
//         if (process != null && process.equals("sign")) {
//             // Verifica se a localização da placa corresponde à localização armazenada
//             if (plugin.getShopWarpGUI().isSignLocationMatching(playerId, event.getBlock().getLocation())) {
//                 // Obtém o nome da loja da primeira linha da placa
//                 String shopName = event.getLine(0);
//                 if (shopName != null && !shopName.isEmpty()) {
//                     // Obtém a descrição das outras linhas da placa
//                     StringBuilder description = new StringBuilder();
//                     for (int i = 1; i < 4; i++) {
//                         String line = event.getLine(i);
//                         if (line != null && !line.isEmpty()) {
//                             if (description.length() > 0) {
//                                 description.append(" ");
//                             }
//                             description.append(line);
//                         }
//                     }

//                     // Cria a loja
//                     plugin.getShopWarpManager().createWarp(player, shopName, description.toString());

//                     // Limpa o processo de criação
//                     plugin.getShopWarpGUI().clearCreationProcess(playerId);

//                     // Atualiza a placa com informações da loja
//                     event.setLine(0, MessageUtils.colorize("&8[&6Loja&8]"));
//                     event.setLine(1, MessageUtils.colorize("&e" + shopName));
//                     event.setLine(2, MessageUtils.colorize("&7de " + player.getName()));
//                     event.setLine(3, MessageUtils.colorize("&a/sw tp " + shopName));
//                 }
//             }
//         }
//     }

//     @EventHandler
//     public void onInventoryClose(InventoryCloseEvent event) {
//         if (!(event.getPlayer() instanceof Player)) {
//             return;
//         }

//         Player player = (Player) event.getPlayer();

//         // Agenda uma tarefa para restaurar o inventário do jogador após o fechamento
//         plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
//             player.updateInventory();
//         }, 1L);
//     }

//     @EventHandler
//     public void onPlayerQuit(PlayerQuitEvent event) {
//         UUID playerId = event.getPlayer().getUniqueId();
//         pendingChatActions.remove(playerId);
//     }

//     public void addPendingChatAction(Player player, ChatActionType type, String data) {
//         pendingChatActions.put(player.getUniqueId(), new ChatAction(type, data));
//     }

//     public enum ChatActionType {
//         CREATE_WARP,
//         ANNOUNCE_WARP
//     }

//     public static class ChatAction {
//         private final ChatActionType type;
//         private final String data;

//         public ChatAction(ChatActionType type, String data) {
//             this.type = type;
//             this.data = data;
//         }

//         public ChatActionType getType() {
//             return type;
//         }

//         public String getData() {
//             return data;
//         }
//     }
// }