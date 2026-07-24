package net.weavemc.mods.timer;

import net.weavemc.api.command.Command;

final class TimerCommand extends Command {
    private final TimerState state;

    TimerCommand(TimerState state) {
        super("timer");
        this.state = state;
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 1) {
            state.toggle();
        }
    }
}
