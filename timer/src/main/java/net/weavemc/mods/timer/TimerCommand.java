package net.weavemc.mods.timer;

import net.weavemc.mods.servercontext.ServerContext;

import net.weavemc.api.command.Command;

final class TimerCommand extends Command {
    private final TimerState state;

    TimerCommand(TimerState state) {
        super("timer");
        this.state = state;
    }

    @Override
    public void execute(String[] args) {
        if (!ServerContext.isAllowed()) { ServerContext.explainDisabled(); return; }
        if (args.length == 1) {
            ServerContext.runOnClientThread(new Runnable() {
                @Override public void run() { state.toggle(); }
            });
        }
    }
}
