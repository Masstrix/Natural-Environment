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
import me.masstrix.eternalnature.PluginData;
import me.masstrix.eternalnature.config.Configurable;
import me.masstrix.eternalnature.util.StringUtil;
import me.masstrix.version.checker.VersionCheckInfo;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@Configurable.Path("general")
public class ConnectionListener implements Listener, Configurable {

    private final EternalNature PLUGIN;
    private boolean notifyUpdates;

    public ConnectionListener(EternalNature plugin) {
        this.PLUGIN = plugin;
    }

    @Override
    public void updateConfig(ConfigurationSection section) {
        notifyUpdates = section.getBoolean("notify-update-join");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PLUGIN.getEngine().loadPlayerData(event.getPlayer().getUniqueId());

        Player player = event.getPlayer();
        if (notifyUpdates && (player.hasPermission("eternalnature.admin") || player.isOp())) {
            VersionCheckInfo info = PLUGIN.getVersionInfo();
            if (info == null) return;
            switch (info.getState()) {
                case BEHIND: {
                    ComponentBuilder builder = new ComponentBuilder(StringUtil.color(PluginData.PREFIX));
                    builder.append("There is a newer version aviliable ").color(ChatColor.WHITE)
                            .append("(" + info.getLatest().getName() + ")   ").color(ChatColor.GOLD);
                    builder.append("\nVIEW DOWNLOAD", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).bold(true)
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("\u00A7eClick to view download page")))
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/natural-environment.43290/"));
                    player.spigot().sendMessage(builder.create());
                    break;
                }
                case AHEAD: {
                    ComponentBuilder builder = new ComponentBuilder(StringUtil.color(PluginData.PREFIX));
                    builder.append("You are running a dev build. Please report bugs ").color(ChatColor.RED);
                    builder.append("HERE", ComponentBuilder.FormatRetention.NONE).color(ChatColor.AQUA).bold(true)
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("\u00A7eClick to report a bug.")))
                            .event(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Masstrix/Eternal-Nature/issues"));
                    player.spigot().sendMessage(builder.create());
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PLUGIN.getEngine().unloadUserData(event.getPlayer().getUniqueId());
    }
}
