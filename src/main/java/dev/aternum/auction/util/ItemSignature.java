package dev.aternum.auction.util;

import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ItemSignature {
    private ItemSignature() {
    }

    public static String of(ItemStack item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ItemSerializer.encode(item).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : hash) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) {
            return "";
        }
    }
}
