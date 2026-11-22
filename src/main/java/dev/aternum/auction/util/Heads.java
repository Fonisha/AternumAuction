package dev.aternum.auction.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional helper for custom head textures used by menu icons.
 * Uses the Paper profile API when available and falls back to Spigot reflection.
 *
 * @author Fonisha (https://github.com/Fonisha)
 */
public final class Heads {
    private Heads() {
    }

    public static ItemStack create(String base64, String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        if (base64 != null && !base64.isEmpty() && applyTexture(item, base64)) return item;
        if (playerName != null && !playerName.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private static boolean applyTexture(ItemStack item, String base64) {
        String textureUrl = textureUrl(base64);
        if (textureUrl == null) return false;
        boolean applied = applyModernPaper(item, base64, textureUrl)
                || applyLegacyPaper(item, base64)
                || applySpigot(item, base64);
        if (!applied) {
            Bukkit.getLogger().warning("[AternumAuction] Could not apply custom head texture. "
                    + "Check that the server is Paper/Purpur 1.16+ or use a compatible skull API.");
        }
        return applied;
    }

    private static boolean applyModernPaper(ItemStack item, String base64, String textureUrl) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            Object profile;
            try {
                profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class, String.class)
                        .invoke(null, uuid, "AternumIcon");
            } catch (NoSuchMethodException missingNamedProfile) {
                profile = Bukkit.class.getMethod("createPlayerProfile", UUID.class)
                        .invoke(null, uuid);
            }
            Class<?> playerProfileType = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> playerTexturesType = Class.forName("org.bukkit.profile.PlayerTextures");
            Object textures = playerProfileType.getMethod("getTextures").invoke(profile);
            playerTexturesType.getMethod("setSkin", URL.class).invoke(textures, new URL(textureUrl));
            playerProfileType.getMethod("setTextures", playerTexturesType).invoke(profile, textures);
            ItemMeta meta = item.getItemMeta();
            Method setter = findProfileSetter(meta.getClass(), playerProfileType);
            if (setter == null) return false;
            setter.invoke(meta, profile);
            item.setItemMeta(meta);
            return true;
        } catch (Throwable failure) {
            return false;
        }
    }

    private static boolean applyLegacyPaper(ItemStack item, String base64) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
            Object profile = Bukkit.class.getMethod("createProfile", UUID.class, String.class)
                    .invoke(null, uuid, "AternumIcon");
            try {
                profile.getClass().getMethod("setProperty", String.class, String.class)
                        .invoke(profile, "textures", base64);
            } catch (NoSuchMethodException missingOldMethod) {
                Class<?> propertyType = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
                Object property = propertyType.getConstructor(String.class, String.class)
                        .newInstance("textures", base64);
                profile.getClass().getMethod("setProperty", propertyType).invoke(profile, property);
            }
            Class<?> playerProfile = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            ItemMeta meta = item.getItemMeta();
            Method setter = findProfileSetter(meta.getClass(), profile.getClass());
            if (setter == null) return false;
            setter.invoke(meta, profile);
            item.setItemMeta(meta);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String textureUrl(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"(https?://[^\\\"]+)\\\"").matcher(json);
            if (!matcher.find()) return null;
            URL url = new URL(matcher.group(1));
            if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) return null;
            if ("textures.minecraft.net".equalsIgnoreCase(url.getHost()) && "http".equalsIgnoreCase(url.getProtocol())) {
                return "https://" + url.getHost() + url.getFile();
            }
            return url.toExternalForm();
        } catch (IllegalArgumentException | java.net.MalformedURLException ignored) {
            return null;
        }
    }

    private static Method findSpigotSetter(Class<?> type, Class<?> profileType) {
        for (Method method : type.getMethods()) {
            if ("setProfile".equals(method.getName())
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(profileType)) {
                return method;
            }
        }
        return null;
    }

    private static Method findProfileSetter(Class<?> metaType, Class<?> profileType) {
        Method method = findProfileSetterIn(metaType, profileType);
        if (method != null) return method;
        return findProfileSetterIn(SkullMeta.class, profileType);
    }

    private static Method findProfileSetterIn(Class<?> type, Class<?> profileType) {
        for (Method method : type.getMethods()) {
            if (("setPlayerProfile".equals(method.getName()) || "setOwnerProfile".equals(method.getName()))
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(profileType)) {
                return method;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean applySpigot(ItemStack item, String base64) {
        try {
            UUID uuid = UUID.nameUUIDFromBytes(base64.getBytes("UTF-8"));
            Object profile = Class.forName("com.mojang.authlib.GameProfile")
                    .getConstructor(UUID.class, String.class)
                    .newInstance(uuid, "AternumIcon");
            Object property = Class.forName("com.mojang.authlib.properties.Property")
                    .getConstructor(String.class, String.class)
                    .newInstance("textures", base64);
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", Object.class, Object.class).invoke(properties, "textures", property);

            ItemMeta meta = item.getItemMeta();
            Method setter = findSpigotSetter(meta.getClass(), profile.getClass());
            if (setter == null) setter = findSpigotSetter(SkullMeta.class, profile.getClass());
            if (setter != null) {
                setter.invoke(meta, profile);
            } else if (!setProfileField(meta, profile)) {
                return false;
            }
            item.setItemMeta(meta);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean setProfileField(ItemMeta meta, Object profile) {
        Class<?> type = meta.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("profile");
                if (!field.getType().isAssignableFrom(profile.getClass())) return false;
                field.setAccessible(true);
                field.set(meta, profile);
                return true;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            } catch (Throwable failure) {
                return false;
            }
        }
        return false;
    }
}
