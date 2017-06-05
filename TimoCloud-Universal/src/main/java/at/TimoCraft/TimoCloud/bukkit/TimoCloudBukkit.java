package at.TimoCraft.TimoCloud.bukkit;

import at.TimoCraft.TimoCloud.api.TimoCloudAPI;
import at.TimoCraft.TimoCloud.bukkit.api.TimoCloudBukkitAPIImplementation;
import at.TimoCraft.TimoCloud.bukkit.commands.SendBungeeCommand;
import at.TimoCraft.TimoCloud.bukkit.commands.SignsCommand;
import at.TimoCraft.TimoCloud.bukkit.commands.TimoCloudBukkitCommand;
import at.TimoCraft.TimoCloud.bukkit.listeners.PlayerInteract;
import at.TimoCraft.TimoCloud.bukkit.listeners.PlayerJoin;
import at.TimoCraft.TimoCloud.bukkit.listeners.PlayerQuit;
import at.TimoCraft.TimoCloud.bukkit.listeners.SignChange;
import at.TimoCraft.TimoCloud.bukkit.managers.BukkitFileManager;
import at.TimoCraft.TimoCloud.bukkit.managers.OtherServerPingManager;
import at.TimoCraft.TimoCloud.bukkit.managers.SignManager;
import at.TimoCraft.TimoCloud.bukkit.managers.StateByEventManager;
import at.TimoCraft.TimoCloud.bukkit.sockets.BukkitSocketClient;
import at.TimoCraft.TimoCloud.bukkit.sockets.BukkitSocketClientHandler;
import at.TimoCraft.TimoCloud.bukkit.sockets.BukkitSocketMessageManager;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.InetAddress;

/**
 * Created by Timo on 27.12.16.
 */
public class TimoCloudBukkit extends JavaPlugin {

    private static TimoCloudBukkit instance;
    private BukkitFileManager fileManager;
    private BukkitSocketClientHandler socketClientHandler;
    private BukkitSocketMessageManager bukkitSocketMessageManager;
    private SignManager signManager;
    private OtherServerPingManager otherServerPingManager;
    private StateByEventManager stateByEventManager;
    private String prefix = "[TimoCloud]";

    public static void log(String message) {
        Bukkit.getConsoleSender().sendMessage(getInstance().getPrefix() + message.replace("&", "§"));
    }

    @Override
    public void onEnable() {
        makeInstances();
        registerCommands();
        registerListeners();
        registerTasks();
        registerChannel();
        TimoCloudAPI.setBukkitImplementation(new TimoCloudBukkitAPIImplementation());
        log("&ahas been enabled!");
    }

    @Override
    public void onDisable() {
        log("&chas been disabled!");
    }

    public void onSocketConnect() {
        getBukkitSocketMessageManager().sendMessage("HANDSHAKE", System.getProperty("timocloud-token"));
        if (isRandomMap()) {
            getBukkitSocketMessageManager().sendMessage("SETMAP", getMapName());
        }
    }

    public void onSocketDisconnect() {
        log("Disconnected from TimoCloud. Stopping server.");
        kill();
    }

    private void kill() {
        if (isStatic()) {
            Bukkit.shutdown();
        } else {
            Runtime.getRuntime().halt(0);
        }
    }

    private void makeInstances() {
        instance = this;
        fileManager = new BukkitFileManager();
        socketClientHandler = new BukkitSocketClientHandler();
        bukkitSocketMessageManager = new BukkitSocketMessageManager();
        signManager = new SignManager();
        otherServerPingManager = new OtherServerPingManager();
        stateByEventManager = new StateByEventManager();
    }

    private void registerCommands() {
        getCommand("signs").setExecutor(new SignsCommand());
        getCommand("sendbungee").setExecutor(new SendBungeeCommand());
        TimoCloudBukkitCommand timoCloudBukkitCommand = new TimoCloudBukkitCommand();
        getCommand("timocloudbukkit").setExecutor(timoCloudBukkitCommand);
        getCommand("tcb").setExecutor(timoCloudBukkitCommand);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new SignChange(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerInteract(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuit(), this);
    }

    private void registerChannel() {
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    public void sendPlayerToServer(Player p, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        try {
            out.writeUTF("Connect");
            out.writeUTF(server);
        } catch (Exception e) {
        }
        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    public boolean isRandomMap() {
        return Boolean.getBoolean("timocloud-randommap");
    }

    public boolean isStatic() {
        return Boolean.getBoolean("static");
    }

    public String getBungeeIP() {
        return System.getProperty("timocloud-bungeecordhost").split(":")[0];
    }

    public int getBungeeSocketPort() {
        return Integer.parseInt(System.getProperty("timocloud-bungeecordhost").split(":")[1]);
    }

    public File getTemplate() {
        return new File(System.getProperty("timocloud-template"));
    }

    private void registerTasks() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                log("Connecting to bungee socket on " + getBungeeIP() + ":" + getBungeeSocketPort() + "...");
                new BukkitSocketClient().init(getBungeeIP(), getBungeeSocketPort());
            } catch (Exception e) {
                e.printStackTrace();
                onSocketDisconnect();
            }
        });

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            sendEverything();
            getOtherServerPingManager().requestEverything();
            getSignManager().updateSigns();
        }, 20L, getFileManager().getConfig().getLong("updateSignsInServerTicks"));
    }

    public void sendEverything() {
        sendMotds();
        sendPlayers();
    }

    public void sendMotds() {
        try {
            ServerListPingEvent event = new ServerListPingEvent(InetAddress.getLocalHost(), Bukkit.getMotd(), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers());
            Bukkit.getPluginManager().callEvent(event);
            getBukkitSocketMessageManager().sendMessage("SETMOTD", event.getMotd());
            Object state = getFileManager().getConfig().get("MotdToState." + event.getMotd());
            if (state != null && state instanceof String) {
                getStateByEventManager().setStateByMotd((String) state);
            }
        } catch (Exception e) {
            log("Error while sending MOTD: ");
            e.printStackTrace();
            getBukkitSocketMessageManager().sendMessage("SETMOTD", Bukkit.getMotd());
        }
    }

    public void sendPlayers() {
        int curPlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        try {
            ServerListPingEvent event = new ServerListPingEvent(InetAddress.getLocalHost(), Bukkit.getMotd(), Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers());
            Bukkit.getPluginManager().callEvent(event);
            curPlayers = event.getNumPlayers();
            maxPlayers = event.getMaxPlayers();
        } catch (Exception e) {
            log("Error while sending player count: ");
            e.printStackTrace();
        }
        getBukkitSocketMessageManager().sendMessage("SETPLAYERS", curPlayers + "/" + maxPlayers);
    }

    public static TimoCloudBukkit getInstance() {
        return instance;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix.replace("&", "§") + " ";
    }

    public BukkitSocketClientHandler getSocketClientHandler() {
        return socketClientHandler;
    }

    public void setSocketClientHandler(BukkitSocketClientHandler socketClientHandler) {
        this.socketClientHandler = socketClientHandler;
    }

    public BukkitFileManager getFileManager() {
        return fileManager;
    }

    public BukkitSocketMessageManager getBukkitSocketMessageManager() {
        return bukkitSocketMessageManager;
    }

    public SignManager getSignManager() {
        return signManager;
    }

    public OtherServerPingManager getOtherServerPingManager() {
        return otherServerPingManager;
    }

    public StateByEventManager getStateByEventManager() {
        return stateByEventManager;
    }

    public String getServerName() {
        return System.getProperty("timocloud-servername");
    }

    public String getMapName() {
        if (isRandomMap()) {
            return System.getProperty("timocloud-mapname");
        }
        return getFileManager().getConfig().getString("defaultMapName");
    }
}
