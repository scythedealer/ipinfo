package com.ipinfo.command;

import com.ipinfo.IPInfoPlugin;
import com.ipinfo.model.IpData;
import com.ipinfo.model.ProxyData;
import com.ipinfo.service.IpLookupService;
import com.ipinfo.util.Colors;
import dev.rollczi.litecommands.annotations.argument.Arg;
import dev.rollczi.litecommands.annotations.command.Command;
import dev.rollczi.litecommands.annotations.context.Context;
import dev.rollczi.litecommands.annotations.execute.Execute;
import dev.rollczi.litecommands.annotations.permission.Permission;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.val;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

@Command(name = "player info")
@Permission("ipinfo.lookup")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlayerInfoCommand {

    IPInfoPlugin plugin;
    IpLookupService ipLookupService;

    @Execute
    public void execute(@Context CommandSender sender, @Arg Player target) {
        val address = target.getAddress();
        if (address == null) {
            sender.sendMessage(Component.text("✘ ", Colors.ERROR)
                    .append(Component.text("Не удалось получить адрес игрока.", NamedTextColor.GRAY)));
            return;
        }

        val inet = address.getAddress();
        val ip   = inet.getHostAddress();

        if (inet.isLoopbackAddress() || inet.isSiteLocalAddress() || inet.isAnyLocalAddress()) {
            sender.sendMessage(buildLocalInfoComponent(target, ip));
            return;
        }

        sender.sendMessage(
                Component.text("⏳ ", Colors.LAVENDER)
                        .append(Component.text("Загрузка информации о ", NamedTextColor.GRAY))
                        .append(Component.text(target.getName(), Colors.LAVENDER))
                        .append(Component.text("...", NamedTextColor.GRAY))
        );

        plugin.getLogger().info("IP lookup requested for " + target.getName() + " (" + ip + ")");

        val geoFuture   = ipLookupService.lookup(ip);
        val proxyFuture = ipLookupService.lookupProxy(ip);

        CompletableFuture.allOf(geoFuture, proxyFuture).thenRun(() -> {
            try {
                val geo   = geoFuture.join();
                val proxy = proxyFuture.join();

                if (geo == null || !"success".equals(geo.getStatus())) {
                    plugin.getLogger().warning("Geo lookup failed for " + ip);
                    runSync(() -> sender.sendMessage(Component.text("✘ ", Colors.ERROR)
                            .append(Component.text("Не удалось получить данные об IP.", NamedTextColor.GRAY))));
                    return;
                }

                runSync(() -> sender.sendMessage(buildInfoComponent(target, geo, proxy)));
            } catch (Exception ex) {
                plugin.getLogger().severe("Exception during lookup for " + ip + ": " + ex.getMessage());
                runSync(() -> sender.sendMessage(Component.text("✘ ", Colors.ERROR)
                        .append(Component.text("Ошибка при запросе: " + ex.getMessage(), NamedTextColor.GRAY))));
            }
        });
    }

    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private Component buildLocalInfoComponent(Player target, String ip) {
        return Component.empty()
                .append(Component.text("  ").append(Component.text("★ ", Colors.LILAC))
                        .append(Component.text("Информация об игроке ", NamedTextColor.WHITE))
                        .append(Component.text(target.getName(), Colors.PURPLE))
                        .append(Component.newline()))
                .append(separator()).append(Component.newline())
                .append(entry("IP-адрес", clickableIp(ip)))
                .append(entry("Тип", Component.text("Локальный / LAN", Colors.LAVENDER)))
                .append(Component.text("    ").append(Component.text("⚠ ", Colors.PURPLE))
                        .append(Component.text("Игрок подключён через локальную сеть.", NamedTextColor.GRAY))
                        .append(Component.newline()))
                .append(Component.text("    ")
                        .append(Component.text("Геоданные недоступны для приватных IP.", Colors.GRAY2))
                        .append(Component.newline()))
                .append(separator());
    }

    private Component buildInfoComponent(Player target, IpData data, ProxyData proxy) {
        return Component.empty()
                .append(Component.text("  ").append(Component.text("★ ", Colors.LILAC))
                        .append(Component.text("Информация об игроке ", NamedTextColor.WHITE))
                        .append(Component.text(target.getName(), Colors.PURPLE))
                        .append(Component.newline()))
                .append(separator()).append(Component.newline())
                .append(entry("IP-адрес",      clickableIp(data.getQuery())))
                .append(entry("Страна",         data.getCountry() + " (" + data.getCountryCode() + ")"))
                .append(entry("Регион",          data.getRegionName()))
                .append(entry("Город",           data.getCity()))
                .append(entry("Индекс",          data.getZip()))
                .append(entry("Координаты",      formatCoords(data.getLat(), data.getLon())))
                .append(entry("Часовой пояс",    data.getTimezone()))
                .append(entry("Провайдер",       data.getIsp()))
                .append(entry("Организация",     data.getOrg()))
                .append(entry("AS",              data.getAs()))
                .append(entry("Тип подключения", resolveConnectionTag(proxy)))
                .append(separator());
    }

    private Component resolveConnectionTag(ProxyData proxy) {
        if (proxy == null) return Component.text("Неизвестно", Colors.VALUE);

        if (proxy.isTor()) {
            return Component.text("⚠ Tor", Colors.ERROR);
        }
        if (proxy.isVpn()) {
            val label = proxy.getProvider().isBlank() ? "VPN" : "VPN (" + proxy.getProvider() + ")";
            return Component.text("⚠ " + label, Colors.VPN);
        }
        if (proxy.isProxy()) {
            val type = proxy.getType().isBlank() ? "Proxy" : proxy.getType();
            return Component.text("⚠ " + type, Colors.VPN);
        }
        if (proxy.isHosting()) {
            return Component.text("Хостинг / Сервер", Colors.VALUE);
        }
        return Component.text("✔ Обычный пользователь", Colors.CLEAN);
    }

    private String formatCoords(double lat, double lon) {
        return String.format("%.4f, %.4f", lat, lon);
    }

    private Component clickableIp(String ip) {
        return Component.text(ip, Colors.PURPLE, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text("Нажмите, чтобы скопировать IP", NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.copyToClipboard(ip));
    }

    private Component separator() {
        return Component.text(" ═════════════════════════════", Colors.SEPARATOR);
    }

    private Component entry(String label, String value) {
        return entry(label, Component.text(value, Colors.VALUE));
    }

    private Component entry(String label, Component valueComp) {
        return Component.text("  ")
                .append(Component.text("▪ ", Colors.BULLET))
                .append(Component.text(label + ": ", NamedTextColor.WHITE))
                .append(valueComp)
                .append(Component.newline());
    }
}
