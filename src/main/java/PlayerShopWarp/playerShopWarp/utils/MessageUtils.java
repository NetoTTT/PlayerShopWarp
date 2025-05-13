package PlayerShopWarp.playerShopWarp.utils;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList; // Import para ArrayList
import java.util.List;      // Import para List

public class MessageUtils {

    public static String colorize(String message) {
        if (message == null) { // Adiciona verificação de nulo para segurança
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    // --- MÉTODOS PARA ENVIAR MENSAGENS AO JOGADOR ---

    public static void sendMessage(Player player, String configPath) {
        sendMessage(player, configPath, null);
    }

    public static void sendMessage(Player player, String configPath, String[][] replacements) {
        // Pega a instância do plugin uma vez
        PlayerShopWarp pluginInstance = PlayerShopWarp.getInstance();
        if (pluginInstance == null || pluginInstance.getConfigManager() == null || pluginInstance.getConfigManager().getMessagesConfig() == null) {
            player.sendMessage(colorize("&cErro crítico: ConfigManager não está disponível."));
            pluginInstance.getLogger().severe("MessageUtils: ConfigManager ou MessagesConfig é nulo ao tentar enviar mensagem: " + configPath);
            return;
        }

        String message = pluginInstance.getConfigManager().getMessagesConfig().getString(configPath);

        if (message == null || message.isEmpty()) {
            player.sendMessage(colorize("&c[PSW] Mensagem não encontrada: " + configPath));
            pluginInstance.getLogger().warning("MessageUtils: Chave de mensagem não encontrada ou vazia: " + configPath);
            return;
        }

        if (replacements != null) {
            for (String[] replacement : replacements) {
                if (replacement.length == 2 && replacement[0] != null && replacement[1] != null) {
                    message = message.replace(replacement[0], replacement[1]);
                }
            }
        }
        player.sendMessage(colorize(message));
    }

    // --- NOVOS MÉTODOS PARA OBTER STRINGS PROCESSADAS ---

    public static String getMessage(String configPath) {
        return getMessage(configPath, null);
    }

    public static String getMessage(String configPath, String[][] replacements) {
        PlayerShopWarp pluginInstance = PlayerShopWarp.getInstance();
        if (pluginInstance == null || pluginInstance.getConfigManager() == null || pluginInstance.getConfigManager().getMessagesConfig() == null) {
            pluginInstance.getLogger().severe("MessageUtils: ConfigManager ou MessagesConfig é nulo ao tentar obter mensagem: " + configPath);
            return colorize("&c[PSW Error] Config indisponível para: " + configPath.substring(configPath.lastIndexOf('.') + 1));
        }

        String message = pluginInstance.getConfigManager().getMessagesConfig().getString(configPath);

        if (message == null || message.isEmpty()) {
            pluginInstance.getLogger().warning("MessageUtils: Chave de mensagem não encontrada ou vazia para getMessage: " + configPath);
            // Retorna uma string de erro formatada, mas não envia diretamente ao jogador
            return colorize("&c[PSW MissingMsg] " + configPath.substring(configPath.lastIndexOf('.') + 1));
        }

        if (replacements != null) {
            for (String[] replacement : replacements) {
                if (replacement.length == 2 && replacement[0] != null && replacement[1] != null) {
                    message = message.replace(replacement[0], replacement[1]);
                }
            }
        }
        return colorize(message);
    }

    // --- NOVOS MÉTODOS PARA OBTER LISTAS DE STRINGS (LORE) PROCESSADAS ---

    public static List<String> getLore(String configPath) {
        return getLore(configPath, null);
    }

    public static List<String> getLore(String configPath, String[][] replacements) {
        PlayerShopWarp pluginInstance = PlayerShopWarp.getInstance();
         if (pluginInstance == null || pluginInstance.getConfigManager() == null || pluginInstance.getConfigManager().getMessagesConfig() == null) {
            pluginInstance.getLogger().severe("MessageUtils: ConfigManager ou MessagesConfig é nulo ao tentar obter lore: " + configPath);
            List<String> errorLore = new ArrayList<>();
            errorLore.add(colorize("&c[PSW Error] Config indisponível para lore: " + configPath.substring(configPath.lastIndexOf('.') + 1)));
            return errorLore;
        }

        List<String> rawLore = pluginInstance.getConfigManager().getMessagesConfig().getStringList(configPath);
        List<String> processedLore = new ArrayList<>();

        if (rawLore.isEmpty()) {
            // Verifica se a chave realmente não existe ou se é uma lista vazia intencional.
            // Se a chave não existir no messages.yml, getStringList retorna uma lista vazia.
            // Se você quiser um erro mais explícito para chaves totalmente ausentes:
            if (!pluginInstance.getConfigManager().getMessagesConfig().contains(configPath)) {
                 pluginInstance.getLogger().warning("MessageUtils: Chave de lore não encontrada: " + configPath);
                 processedLore.add(colorize("&c[PSW MissingLore] " + configPath.substring(configPath.lastIndexOf('.') + 1)));
            }
            // Se a chave existe mas está vazia, retorna a lista vazia (comportamento normal).
            return processedLore;
        }

        for (String line : rawLore) {
            String processedLine = line; // Começa com a linha original
            if (replacements != null) {
                for (String[] replacement : replacements) {
                    if (replacement.length == 2 && replacement[0] != null && replacement[1] != null) {
                        processedLine = processedLine.replace(replacement[0], replacement[1]);
                    }
                }
            }
            processedLore.add(colorize(processedLine));
        }
        return processedLore;
    }
}