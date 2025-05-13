package PlayerShopWarp.playerShopWarp.utils;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.DataStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SafetyScanner {

    private final PlayerShopWarp plugin;
    private final int scanRadius;
    private final List<Material> dangerousBlocks;

    public SafetyScanner(PlayerShopWarp plugin) {
        this.plugin = plugin;
        this.scanRadius = plugin.getConfigManager().getConfig().getInt("scanner.radius", 8); // Raio padrão de 8 blocos
                                                                                             // (16x16)

        // Carrega lista de blocos perigosos da configuração
        List<String> dangerousBlockNames = plugin.getConfigManager().getConfig()
                .getStringList("anti-trap.dangerous-blocks");
        this.dangerousBlocks = new ArrayList<>();

        for (String blockName : dangerousBlockNames) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                dangerousBlocks.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Material inválido na configuração anti-trap: " + blockName);
            }
        }
    }

    /**
     * Escaneia a área ao redor do jogador e retorna um relatório de segurança
     * 
     * @param player Jogador que está escaneando
     * @return Relatório de segurança da área
     */
    public ScanResult scanArea(Player player) {
        Location centerLoc = player.getLocation();
        ScanResult result = new ScanResult();

        // Verifica o chão abaixo do jogador
        checkGroundSafety(centerLoc, result);

        // Verifica blocos perigosos na área
        checkDangerousBlocks(centerLoc, result);

        // Verifica proteção de GriefPrevention
        checkGriefPrevention(centerLoc, player, result);

        // Verifica altura (para evitar quedas fatais)
        checkHeight(centerLoc, result);

        // Verifica espaço livre (para evitar sufocamento)
        checkFreeSpace(centerLoc, result);

        return result;
    }

    /**
     * Verifica se há chão sólido abaixo do jogador
     */
    private void checkGroundSafety(Location location, ScanResult result) {
        Location checkLoc = location.clone();
        int groundCheckDistance = plugin.getConfigManager().getConfig().getInt("anti-trap.ground-check-distance", 5);
        boolean foundGround = false;
        int distanceToGround = 0;

        for (int i = 0; i <= groundCheckDistance; i++) {
            checkLoc.subtract(0, 1, 0);
            Block block = checkLoc.getBlock();
            distanceToGround++;

            if (block.getType().isSolid() && !block.getType().name().contains("LEAVES")) {
                foundGround = true;
                break;
            }
        }

        if (!foundGround) {
            result.addIssue("Não há chão sólido próximo", "Há um vazio de pelo menos " + groundCheckDistance +
                    " blocos abaixo. Isso pode causar quedas fatais.", ScanResult.IssueType.DANGER);
        } else if (distanceToGround > 2) {
            result.addIssue("Queda potencial", "Há uma queda de " + distanceToGround +
                    " blocos abaixo. Jogadores podem se machucar.", ScanResult.IssueType.WARNING);
        }
    }

    /**
     * Verifica se há blocos perigosos na área
     */
    private void checkDangerousBlocks(Location location, ScanResult result) {
        Map<Material, List<Location>> dangerousBlocksFound = new HashMap<>();

        for (int x = -scanRadius; x <= scanRadius; x++) {
            for (int y = -3; y <= 5; y++) { // Verifica de 3 blocos abaixo a 5 blocos acima
                for (int z = -scanRadius; z <= scanRadius; z++) {
                    Block block = location.getBlock().getRelative(x, y, z);

                    if (dangerousBlocks.contains(block.getType())) {
                        dangerousBlocksFound.computeIfAbsent(block.getType(), k -> new ArrayList<>())
                                .add(block.getLocation());
                    }
                }
            }
        }

        if (!dangerousBlocksFound.isEmpty()) {
            for (Map.Entry<Material, List<Location>> entry : dangerousBlocksFound.entrySet()) {
                Material material = entry.getKey();
                List<Location> locations = entry.getValue();

                Location closestDanger = findClosestLocation(location, locations);
                int distance = (int) location.distance(closestDanger);
                String direction = getDirection(location, closestDanger);

                result.addIssue("Bloco perigoso: " + formatMaterialName(material),
                        "Encontrado a " + distance + " blocos de distância (" + direction + "). " +
                                "Total: " + locations.size() + " blocos.",
                        ScanResult.IssueType.DANGER);
            }
        }
    }

    private void checkGriefPrevention(Location location, Player player, ScanResult result) {
        boolean checkGriefPrevention = plugin.getConfigManager().getConfig()
                .getBoolean("anti-trap.check-grief-prevention", true);
        // Adiciona uma configuração para permitir a criação apenas na própria claim
        boolean onlyOwnerCanCreate = plugin.getConfigManager().getConfig()
                .getBoolean("anti-trap.only-owner-claims", true); // Reutilizando a config do SafetyChecker

        if (!checkGriefPrevention) {
            return;
        }

        try {
            // Verifica se o GriefPrevention está instalado
            org.bukkit.plugin.Plugin gpPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
            if (gpPlugin == null || !gpPlugin.isEnabled()) {
                result.addIssue("GriefPrevention não encontrado/desabilitado",
                        "A verificação de proteção está ativa, mas o GriefPrevention não está disponível.",
                        ScanResult.IssueType.INFO);
                return;
            }

            // Usa a API do GriefPrevention diretamente
            GriefPrevention griefPrevention = GriefPrevention.instance;
            DataStore dataStore = griefPrevention.dataStore;

            // CORREÇÃO: Adiciona os parâmetros necessários para o método getClaimAt
            // ignoreHeight=true para verificar claims independente da altura
            // cachedClaim=null pois não temos uma claim em cache
            Claim claim = dataStore.getClaimAt(location, true, null);

            if (claim == null) {
                // Se só pode criar na própria claim, a ausência de claim é um problema
                if (onlyOwnerCanCreate) {
                    result.addIssue("Área não protegida (Requerido)",
                            "Este local não está protegido pelo GriefPrevention. É necessário criar em sua própria claim.",
                            ScanResult.IssueType.DANGER); // Perigo se for obrigatório
                } else {
                    result.addIssue("Área não protegida",
                            "Este local não está protegido pelo GriefPrevention. Considere criar uma proteção.",
                            ScanResult.IssueType.WARNING); // Aviso se não for obrigatório
                }
            } else {
                UUID claimOwnerUUID = claim.getOwnerID();

                if (claimOwnerUUID != null && claimOwnerUUID.equals(player.getUniqueId())) {
                    result.addIssue("Área protegida (sua)",
                            "Este local está protegido por você no GriefPrevention. Ótimo!",
                            ScanResult.IssueType.GOOD);
                } else {
                    String ownerName = "Desconhecido";
                    if (claimOwnerUUID != null) {
                        try {
                            // Tenta obter o nome do jogador pelo UUID
                            ownerName = plugin.getServer().getOfflinePlayer(claimOwnerUUID).getName();
                            if (ownerName == null)
                                ownerName = "Desconhecido (UUID: " + claimOwnerUUID.toString() + ")";
                        } catch (Exception ignored) {
                            ownerName = "Desconhecido (UUID: " + claimOwnerUUID.toString() + ")";
                        }
                    } else {
                        ownerName = "Ninguém (Claim Administrativa?)";
                    }

                    // Se só pode criar na própria claim, estar na claim de outro é um problema
                    if (onlyOwnerCanCreate) {
                        result.addIssue("Área protegida (outro jogador)",
                                "Este local está protegido por " + ownerName
                                        + ". Você só pode criar warps em sua própria claim.",
                                ScanResult.IssueType.DANGER);
                    } else {
                        result.addIssue("Área protegida (outro jogador)",
                                "Este local está protegido por " + ownerName
                                        + ". Você pode criar aqui, mas não é sua claim.",
                                ScanResult.IssueType.INFO); // Apenas informativo se não for obrigatório ser dono
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erro ao acessar GriefPrevention API: " + e.getMessage());
            e.printStackTrace(); // Útil para debug
            result.addIssue("Erro ao verificar proteção GP",
                    "Falha ao verificar proteção do GriefPrevention via API: " + e.getMessage()
                            + ". Verifique os logs.",
                    ScanResult.IssueType.WARNING);
        }
    }

    /**
     * Verifica a altura do local (para evitar quedas fatais)
     */
    private void checkHeight(Location location, ScanResult result) {
        int y = location.getBlockY();

        if (y > 150) {
            result.addIssue("Altitude elevada",
                    "Este local está muito alto (Y=" + y + "). Jogadores podem cair ao se teleportarem.",
                    ScanResult.IssueType.WARNING);
        } else if (y < 50) {
            result.addIssue("Altitude baixa",
                    "Este local está muito baixo (Y=" + y
                            + "). Pode ser difícil para jogadores encontrarem o caminho de volta.",
                    ScanResult.IssueType.INFO);
        }
    }

    /**
     * Verifica se há espaço livre suficiente para o jogador
     */
    private void checkFreeSpace(Location location, ScanResult result) {
        Location headLoc = location.clone().add(0, 1, 0);

        if (!headLoc.getBlock().getType().isAir()) {
            result.addIssue("Espaço insuficiente",
                    "Não há espaço suficiente para a cabeça do jogador. Isso pode causar sufocamento.",
                    ScanResult.IssueType.DANGER);
        }

        // Verifica se há espaço para se mover
        int blockedDirections = 0;

        if (!location.clone().add(1, 0, 0).getBlock().isPassable())
            blockedDirections++;
        if (!location.clone().add(-1, 0, 0).getBlock().isPassable())
            blockedDirections++;
        if (!location.clone().add(0, 0, 1).getBlock().isPassable())
            blockedDirections++;
        if (!location.clone().add(0, 0, -1).getBlock().isPassable())
            blockedDirections++;

        if (blockedDirections >= 3) {
            result.addIssue("Mobilidade restrita",
                    "Este local tem " + blockedDirections
                            + " direções bloqueadas. Os jogadores terão dificuldade para se mover.",
                    ScanResult.IssueType.WARNING);
        }
    }

    /**
     * Encontra a localização mais próxima de uma lista
     */
    private Location findClosestLocation(Location reference, List<Location> locations) {
        Location closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Location loc : locations) {
            double distance = reference.distance(loc);
            if (distance < minDistance) {
                minDistance = distance;
                closest = loc;
            }
        }

        return closest;
    }

    /**
     * Retorna a direção cardinal (N, S, L, O, etc) de uma localização em relação a
     * outra
     */
    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();

        String vertical = "";
        if (dy > 2) {
            vertical = " acima";
        } else if (dy < -2) {
            vertical = " abaixo";
        }

        // Determina a direção cardinal
        double angle = Math.atan2(dz, dx);
        angle = angle * 180 / Math.PI;
        angle = (angle + 360) % 360;

        if (angle >= 337.5 || angle < 22.5) {
            return "Leste" + vertical;
        } else if (angle >= 22.5 && angle < 67.5) {
            return "Sudeste" + vertical;
        } else if (angle >= 67.5 && angle < 112.5) {
            return "Sul" + vertical;
        } else if (angle >= 112.5 && angle < 157.5) {
            return "Sudoeste" + vertical;
        } else if (angle >= 157.5 && angle < 202.5) {
            return "Oeste" + vertical;
        } else if (angle >= 202.5 && angle < 247.5) {
            return "Noroeste" + vertical;
        } else if (angle >= 247.5 && angle < 292.5) {
            return "Norte" + vertical;
        } else {
            return "Nordeste" + vertical;
        }
    }

    /**
     * Formata o nome do material para exibição mais amigável
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
                if (c == ' ') {
                    capitalizeNext = true;
                }
            }
        }

        return result.toString();
    }

    /**
     * Classe para armazenar o resultado do escaneamento
     */
    public static class ScanResult {
        private final List<Issue> issues = new ArrayList<>();

        public void addIssue(String title, String description, IssueType type) {
            issues.add(new Issue(title, description, type));
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }

        public boolean hasDangers() {
            for (Issue issue : issues) {
                if (issue.getType() == IssueType.DANGER) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasWarnings() {
            for (Issue issue : issues) {
                if (issue.getType() == IssueType.WARNING) {
                    return true;
                }
            }
            return false;
        }

        public enum IssueType {
            DANGER(ChatColor.RED + "⚠ "),
            WARNING(ChatColor.GOLD + "⚠ "),
            INFO(ChatColor.BLUE + "ℹ "),
            GOOD(ChatColor.GREEN + "✓ ");

            private final String prefix;

            IssueType(String prefix) {
                this.prefix = prefix;
            }

            public String getPrefix() {
                return prefix;
            }
        }

        public static class Issue {
            private final String title;
            private final String description;
            private final IssueType type;

            public Issue(String title, String description, IssueType type) {
                this.title = title;
                this.description = description;
                this.type = type;
            }

            public String getTitle() {
                return title;
            }

            public String getDescription() {
                return description;
            }

            public IssueType getType() {
                return type;
            }
        }
    }
}