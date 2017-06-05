package at.TimoCraft.TimoCloud.bukkit.listeners;

import at.TimoCraft.TimoCloud.bukkit.TimoCloudBukkit;
import at.TimoCraft.TimoCloud.utils.ServerToGroupUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Arrays;

/**
 * Created by Timo on 30.12.16.
 */
public class PlayerInteract implements Listener {

    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (! Arrays.asList(Material.WALL_SIGN, Material.SIGN_POST).contains(block.getType())) {
            return;
        }
        if (!TimoCloudBukkit.getInstance().getSignManager().getSigns().containsKey(block.getLocation())) {
            return;
        }

        String server = TimoCloudBukkit.getInstance().getSignManager().getServerOnSign(block.getLocation());
        String group = ServerToGroupUtil.getGroupByServer(server);
        String state = TimoCloudBukkit.getInstance().getOtherServerPingManager().getState(server);
        if (TimoCloudBukkit.getInstance().getSignManager().shouldBeSortedOut(state, group)) {
            return;
        }
        TimoCloudBukkit.getInstance().sendPlayerToServer(event.getPlayer(), TimoCloudBukkit.getInstance().getSignManager().getSigns().get(block.getLocation()));
    }
}
