package net.weavemc.mods.endstone.command;

import net.weavemc.api.command.Command;
import net.weavemc.mods.endstone.EndstoneMod;

public final class EndstoneCommand extends Command {
    public EndstoneCommand() {
        super("endstone");
    }

    @Override
    public void execute(String[] args) {
        EndstoneMod.toggleFeature();
    }
}
