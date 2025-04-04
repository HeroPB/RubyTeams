package me.herohd.rubyteams.utils;

import dev.dbassett.skullcreator.SkullCreator;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Utils {


    public static String getLocationAsString(Location location) {
        return location.getWorld().getName()+";"+location.getBlockX()+";"+location.getBlockY()+";"+location.getBlockZ();
    }
    public static Location getLocationFromString(String location) {
        String[] strings = location.split(";");
        return new Location(Bukkit.getWorld(strings[0]), Integer.parseInt(strings[1]), Integer.parseInt(strings[2]), Integer.parseInt(strings[3]));
    }

    public static Location getBlockCenter(Location loc, int offsetY) {
        return new Location(loc.getWorld(),
                loc.getBlockX() + 0.5,  // Centro della X
                loc.getBlockY() + offsetY,  // Centro della Y
                loc.getBlockZ() + 0.5   // Centro della Z
        );
    }


    public static ArmorStand getArmorStandAt(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        for (Entity entity : world.getNearbyEntities(location, 0.5, 0.5, 0.5)) {
            if (entity instanceof ArmorStand) {
                return (ArmorStand) entity;
            }
        }

        return null; // Nessun ArmorStand trovato
    }


    public static ItemStack getFromConfiguration(ConfigurationSection section){
        String[] mat = section.getString("material").split(";");
        ItemStack item = new ItemStack(Material.valueOf(mat[0]), 1, Short.parseShort(mat[1]));
        String base64 = section.getString("meta");

        ItemStack stack = base64 == null ? item : SkullCreator.itemFromBase64(base64);
        ItemMeta meta = stack.getItemMeta();
        meta.setLore(Utils.coloraLista(section.getStringList("lore")));
        meta.setDisplayName(Utils.colora(section.getString("name")));
        if(section.getBoolean("glow")){
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        }
        stack.setItemMeta(meta);
        return stack;
    }


    public static String colora(String str) {
        if (str == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    public static String prefix() {
        return colora("&c&lNMS &7» &F");
    }

    public static List<String> coloraLista(List<String> colors) {
        List<String> a = new ArrayList<>();
        colors.forEach(b -> a.add(colora(b)));
        return a;
    }

    public static boolean isValidInt(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void sendActionbar(Player player, String message) {
        if (player == null || message == null)
            return;
        String nmsVersion = Bukkit.getServer().getClass().getPackage().getName();
        nmsVersion = nmsVersion.substring(nmsVersion.lastIndexOf(".") + 1);
        if (!nmsVersion.startsWith("v1_9_R") && !nmsVersion.startsWith("v1_8_R")) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, (BaseComponent) new TextComponent(message));
            return;
        }
        try {
            Class<?> craftPlayerClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".entity.CraftPlayer");
            Object craftPlayer = craftPlayerClass.cast(player);
            Class<?> ppoc = Class.forName("net.minecraft.server." + nmsVersion + ".PacketPlayOutChat");
            Class<?> packet = Class.forName("net.minecraft.server." + nmsVersion + ".Packet");
            Class<?> chat = Class.forName("net.minecraft.server." + nmsVersion + (nmsVersion.equalsIgnoreCase("v1_8_R1") ? ".ChatSerializer" : ".ChatComponentText"));
            Class<?> chatBaseComponent = Class.forName("net.minecraft.server." + nmsVersion + ".IChatBaseComponent");
            Method method = null;
            if (nmsVersion.equalsIgnoreCase("v1_8_R1"))
                method = chat.getDeclaredMethod("a", String.class);
            Object object = nmsVersion.equalsIgnoreCase("v1_8_R1") ? chatBaseComponent.cast(method.invoke(chat, "{'text': '" + message + "'}")) : chat.getConstructor(new Class[]{String.class}).newInstance(message);
            Object packetPlayOutChat = ppoc.getConstructor(new Class[]{chatBaseComponent, byte.class}).newInstance(object, (byte) 2);
            Method handle = craftPlayerClass.getDeclaredMethod("getHandle");
            Object iCraftPlayer = handle.invoke(craftPlayer);
            Field playerConnectionField = iCraftPlayer.getClass().getDeclaredField("playerConnection");
            Object playerConnection = playerConnectionField.get(iCraftPlayer);
            Method sendPacket = playerConnection.getClass().getDeclaredMethod("sendPacket", packet);
            sendPacket.invoke(playerConnection, packetPlayOutChat);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void dispatchCommand(String cmd) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    public static String getTimeLeftFormatted(long countdown) {
        long hours = countdown / 3600;
        long minutes = (countdown % 3600) / 60;
        long seconds = countdown % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String removeColor(String message) {
        return ChatColor.stripColor(message);
    }

    public static void sendMessage(Player player, String message){
        player.sendMessage(colora(message));
    }

    public static void sendMessage(CommandSender sender, String message){
        sender.sendMessage(colora(message));
    }

    public static String getCurrentHour(){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }

    public static String getCurrentDate(){
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        LocalDateTime now = LocalDateTime.now();
        return dtf.format(now);
    }

    public static boolean isValidDouble(String s){
        try {
            Double.parseDouble(s);
            return true;
        }
        catch (NumberFormatException e){
            return false;
        }
    }

    public static long getTimeFromString(String timeString) {
        String[] p = timeString.split(":");
        if (p.length != 3) {
            return 0;
        }
        try {
            long hours = Long.parseLong(p[0]);
            long min = Long.parseLong(p[1]);
            long sec = Long.parseLong(p[2]);
            return sec * 1000L + min * 60L * 1000L + hours * 60L * 60L * 1000L;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean hasInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }

    public static int parseInt(String s, int defaultNumber){
        try {
            return Integer.parseInt(s);
        }
        catch (Exception e){
            return defaultNumber;
        }
    }

    public static void runCommand(String cmd){
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }


    public static String[] substringsBetween(final String str, final String open, final String close) {
        if (str == null || open.isEmpty() || close.isEmpty()) {
            return null;
        }
        final int strLen = str.length();
        if (strLen == 0) {
            return new String[]{};
        }
        final int closeLen = close.length();
        final int openLen = open.length();
        final List<String> list = new ArrayList<>();
        int pos = 0;
        while (pos < strLen - closeLen) {
            int start = str.indexOf(open, pos);
            if (start < 0) {
                break;
            }
            start += openLen;
            final int end = str.indexOf(close, start);
            if (end < 0) {
                break;
            }
            list.add(str.substring(start, end));
            pos = end + closeLen;
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.toArray(new String[0]);
    }

    public static String convertSecond(double secondi) {
        int ore = (int) (secondi / 3600); // Un'ora ha 3600 secondi
        int minuti = (int) ((secondi % 3600) / 60); // I minuti sono il resto diviso per 60
        int rimanentiSecondi = (int) (secondi % 60); // I secondi rimanenti

        StringBuilder result = new StringBuilder();

        if (ore > 0) {
            result.append(ore).append("h ");
        }
        if (minuti > 0) {
            result.append(minuti).append("m ");
        }
        if (rimanentiSecondi > 0 || result.length() == 0) { // Aggiungi i secondi anche se sono 0 se non ci sono ore o minuti
            result.append(rimanentiSecondi).append("s");
        }

        return result.toString().trim(); // Rimuove eventuali spazi extra alla fine
    }

}
