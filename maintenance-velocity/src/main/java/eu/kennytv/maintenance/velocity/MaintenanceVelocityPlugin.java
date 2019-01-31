/*
 * Maintenance - https://git.io/maintenancemode
 * Copyright (C) 2018 KennyTV (https://github.com/KennyTV)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.kennytv.maintenance.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.Favicon;
import eu.kennytv.maintenance.api.ISettings;
import eu.kennytv.maintenance.api.proxy.Server;
import eu.kennytv.maintenance.core.hook.ServerListPlusHook;
import eu.kennytv.maintenance.core.proxy.MaintenanceProxyPlugin;
import eu.kennytv.maintenance.core.proxy.SettingsProxy;
import eu.kennytv.maintenance.core.util.MaintenanceVersion;
import eu.kennytv.maintenance.core.util.SenderInfo;
import eu.kennytv.maintenance.core.util.ServerType;
import eu.kennytv.maintenance.core.util.Task;
import eu.kennytv.maintenance.velocity.command.MaintenanceVelocityCommand;
import eu.kennytv.maintenance.velocity.listener.LoginListener;
import eu.kennytv.maintenance.velocity.listener.ProxyPingListener;
import eu.kennytv.maintenance.velocity.listener.ServerConnectListener;
import eu.kennytv.maintenance.velocity.util.LoggerWrapper;
import eu.kennytv.maintenance.velocity.util.VelocitySenderInfo;
import eu.kennytv.maintenance.velocity.util.VelocityServer;
import eu.kennytv.maintenance.velocity.util.VelocityTask;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.serializer.ComponentSerializers;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * @author KennyTV
 * @since 3.0
 */
@Plugin(id = "maintenancevelocity", name = "MaintenanceVelocity", version = MaintenanceVersion.VERSION, authors = "KennyTV",
        description = "Enable maintenance mode with a custom maintenance motd and icon.", url = "https://www.spigotmc.org/resources/maintenancemode.40699/",
        dependencies = @Dependency(id = "serverlistplus", optional = true))
public final class MaintenanceVelocityPlugin extends MaintenanceProxyPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final File dataFolder;
    private Favicon favicon;

    @Inject
    public MaintenanceVelocityPlugin(final ProxyServer server, final org.slf4j.Logger logger, @DataDirectory final Path folder) {
        super(MaintenanceVersion.VERSION, ServerType.VELOCITY);
        this.server = server;
        this.logger = new LoggerWrapper(logger);
        this.dataFolder = folder.toFile();
    }

    @Subscribe
    public void onEnable(final ProxyInitializeEvent event) {
        sendEnableMessage();

        settings = new SettingsProxy(this);

        server.getCommandManager().register(new MaintenanceVelocityCommand(this, settings), "maintenance", "maintenancevelocity");
        final EventManager em = server.getEventManager();
        em.register(this, ProxyPingEvent.class, PostOrder.LAST, new ProxyPingListener(this, settings));
        em.register(this, ServerPreConnectEvent.class, PostOrder.LAST, new ServerConnectListener(this, settings));
        em.register(this, LoginEvent.class, PostOrder.LAST, new LoginListener(this, settings));

        // ServerListPlus integration
        server.getPluginManager().getPlugin("serverlistplus").ifPresent(slpContainer -> slpContainer.getInstance().ifPresent(serverListPlus -> {
            serverListPlusHook = new ServerListPlusHook(serverListPlus);
            serverListPlusHook.setEnabled(!settings.isMaintenance());
            logger.info("Enabled ServerListPlus integration!");
        }));
    }

    @Subscribe
    public void proxyReload(final ProxyReloadEvent event) {
        settings.reloadConfigs();
        logger.info("Reloaded config files!");
    }

    @Override
    public void sendUpdateNotification(final SenderInfo sender) {
        sender.sendMessage(getPrefix() + "§cThere is a newer version available: §aVersion " + getNewestVersion() + "§c, you're on §a" + getVersion());
        final TextComponent tc1 = translate(getPrefix());
        final TextComponent tc2 = translate("§cDownload it at: §6https://www.spigotmc.org/resources/maintenancemode.40699/");
        final TextComponent click = translate(" §7§l§o(CLICK ME)");
        click.clickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://www.spigotmc.org/resources/maintenancemode.40699/"));
        click.hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.of("§aDownload the latest version")));
        tc1.append(tc2);
        tc1.append(click);
        ((VelocitySenderInfo) sender).sendMessage(tc1);
    }

    public boolean isMaintenance(final ServerInfo serverInfo) {
        return settings.getMaintenanceServers().contains(serverInfo.getName());
    }

    @Override
    protected void kickPlayers() {
        server.getAllPlayers().stream()
                .filter(p -> !p.hasPermission("maintenance.bypass") && !settings.getWhitelistedPlayers().containsKey(p.getUniqueId()))
                .forEach(p -> p.disconnect(TextComponent.of(settings.getKickMessage().replace("%NEWLINE%", "\n"))));
    }

    @Override
    protected void kickPlayers(final Server server, final Server fallback) {
        final RegisteredServer fallbackServer = fallback != null ? ((VelocityServer) fallback).getServer() : null;
        ((VelocityServer) server).getServer().getPlayersConnected().forEach(p -> {
            if (!p.hasPermission("maintenance.bypass") && !settings.getWhitelistedPlayers().containsKey(p.getUniqueId())) {
                if (fallbackServer != null) {
                    p.sendMessage(translate(settings.getMessage("singleMaintenanceActivated").replace("%SERVER%", server.getName())));
                    // Kick the player if fallback server is not reachable
                    p.createConnectionRequest(fallbackServer).connect().whenComplete((result, e) -> {
                        if (!result.isSuccessful())
                            p.disconnect(TextComponent.of(settings.getMessage("singleMaintenanceKickComplete").replace("%NEWLINE%", "\n").replace("%SERVER%", server.getName())));
                    });
                }
            } else {
                p.sendMessage(translate(settings.getMessage("singleMaintenanceActivated").replace("%SERVER%", server.getName())));
            }
        });
    }

    @Override
    public Task startMaintenanceRunnable(final Runnable runnable) {
        return new VelocityTask(server.getScheduler().buildTask(this, runnable).repeat(1, TimeUnit.SECONDS).schedule());
    }

    @Override
    public Server getServer(final String server) {
        final Optional<RegisteredServer> serverInfo = this.server.getServer(server);
        return serverInfo.map(VelocityServer::new).orElse(null);
    }

    @Override
    public SenderInfo getPlayer(final String name) {
        final Optional<Player> player = server.getPlayer(name);
        return player.map(VelocitySenderInfo::new).orElse(null);
    }

    @Override
    public SenderInfo getOfflinePlayer(final UUID uuid) {
        final Optional<Player> player = server.getPlayer(uuid);
        return player.map(VelocitySenderInfo::new).orElse(null);
    }

    @Override
    public void async(final Runnable runnable) {
        server.getScheduler().buildTask(this, runnable).schedule();
    }

    @Override
    public void broadcast(final String message) {
        server.broadcast(translate(message));
        logger.info(message);
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public ISettings getSettings() {
        return settings;
    }

    @Override
    public File getPluginFile() {
        return server.getPluginManager().getPlugin("maintenancevelocity")
                .orElseThrow(() -> new IllegalArgumentException("Couldn't get Maintenance instance. Custom/broken build?")).getDescription().getSource()
                .orElseThrow(IllegalArgumentException::new).toFile();
    }

    @Override
    public InputStream getResource(final String name) {
        return this.getClass().getClassLoader().getResourceAsStream(name);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    protected void loadIcon(final File file) throws IOException {
        favicon = Favicon.create(ImageIO.read(file));
    }

    public ProxyServer getServer() {
        return server;
    }

    public Favicon getFavicon() {
        return favicon;
    }

    public TextComponent translate(final String s) {
        return ComponentSerializers.LEGACY.deserialize(s);
    }
}
