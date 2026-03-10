package com.ipinfo;

import com.ipinfo.command.PlayerInfoCommand;
import com.ipinfo.service.IpLookupService;
import dev.rollczi.litecommands.LiteCommands;
import dev.rollczi.litecommands.bukkit.LiteBukkitFactory;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class IPInfoPlugin extends JavaPlugin {

    private LiteCommands<CommandSender> liteCommands;
    private IpLookupService ipLookupService;

    @Override
    public void onEnable() {
        this.ipLookupService = new IpLookupService();

        this.liteCommands = LiteBukkitFactory.builder("ipinfo", this)
                .commands(new PlayerInfoCommand(this, ipLookupService))
                .build();

        getLogger().info("ipinfo enable");
    }

    @Override
    public void onDisable() {
        liteCommands.unregister();
        ipLookupService.shutdown();
        getLogger().info("ipinfo disable");
    }
}
