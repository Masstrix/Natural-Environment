/*
 * Copyright 2019 Matthew Denton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.masstrix.eternalnature.listeners;

import me.masstrix.eternalnature.EternalNature;
import me.masstrix.eternalnature.player.UserData;
import me.masstrix.eternalnature.util.StringUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeathListener implements Listener {

    private static Map<UUID, String> customReasons = new HashMap<>();

    /**
     * Adds a custom reason for why a player was killed. This is used as their death
     * message if the last damage they took was defined as a custom cause.
     *
     * @param player player to assign this to.
     * @param message message to send when they are killed.
     */
    public static void logCustomReason(Player player, String message) {
        customReasons.put(player.getUniqueId(), message);
    }

    private EternalNature plugin;

    public DeathListener(EternalNature plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent cause = player.getLastDamageCause();
        if (cause != null && cause.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            if (customReasons.containsKey(player.getUniqueId())) {
                event.setDeathMessage(StringUtil.color(customReasons.get(player.getUniqueId())));
            }
        }
        // Remove any instance of the player
        customReasons.remove(player.getUniqueId());
    }

    @EventHandler
    public void on(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UserData data = plugin.getEngine().getUserData(player.getUniqueId());
        new BukkitRunnable() {
            @Override
            public void run() {
                data.resetTemperature();
                data.setHydration(20);
            }
        }.runTaskLater(plugin, 5);
    }
}
