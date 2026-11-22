package dev.aternum.auction.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public final class ItemSerializer {
    private ItemSerializer() {
    }

    public static String encode(ItemStack item) {
        if (item == null) return "";
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            BukkitObjectOutputStream data = new BukkitObjectOutputStream(output);
            data.writeObject(item);
            data.close();
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize item", exception);
        }
    }

    public static ItemStack decode(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            BukkitObjectInputStream data = new BukkitObjectInputStream(new ByteArrayInputStream(bytes));
            Object object = data.readObject();
            data.close();
            return object instanceof ItemStack ? (ItemStack) object : null;
        } catch (Exception exception) {
            return null;
        }
    }
}
