package dev.aternum.auction.service;

import dev.aternum.auction.AternumAuctionPlugin;
import dev.aternum.auction.util.Scheduler;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Discord webhook notifier. All network work happens off the main thread.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class DiscordService {
    private final AternumAuctionPlugin plugin;

    public DiscordService(AternumAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(String event, String title, String description) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        if (!plugin.getConfig().getStringList("discord.events").contains(event)) return;
        final String webhook = plugin.getConfig().getString("discord.webhook-url", "");
        if (webhook == null || webhook.isEmpty()) return;

        String username = plugin.getConfig().getString("discord.username", "AternumAuction");
        String avatar = plugin.getConfig().getString("discord.avatar-url", "");
        String serverName = plugin.getConfig().getString("discord.server-name", "");
        if (serverName == null || serverName.isEmpty()) serverName = plugin.getConfigManager().serverId();
        int color = plugin.getConfig().getInt("discord.color", 3447003);

        String payload = "{\"username\":" + quote(username)
                + (avatar == null || avatar.isEmpty() ? "" : ",\"avatar_url\":" + quote(avatar))
                + ",\"embeds\":[{\"title\":" + quote(title)
                + ",\"description\":" + quote(description)
                + ",\"color\":" + color
                + ",\"footer\":{\"text\":" + quote("AternumAuction • " + serverName) + "}"
                + ",\"timestamp\":" + quote(java.time.OffsetDateTime.now().toString())
                + "}]}";

        Scheduler.runAsync(plugin, new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(webhook).openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(bytes);
                    }
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) plugin.getLogger().fine("Discord webhook returned HTTP " + status);
                } catch (Exception exception) {
                    plugin.getLogger().fine("Discord webhook failed: " + exception.getMessage());
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
