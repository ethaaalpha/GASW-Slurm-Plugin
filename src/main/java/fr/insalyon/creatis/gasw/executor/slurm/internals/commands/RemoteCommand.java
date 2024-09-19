package fr.insalyon.creatis.gasw.executor.slurm.internals.commands;

import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.executor.slurm.config.json.properties.Credentials;
import fr.insalyon.creatis.gasw.executor.slurm.internals.terminal.RemoteOutput;
import fr.insalyon.creatis.gasw.executor.slurm.internals.terminal.RemoteTerminal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class RemoteCommand {
    
    final private String    command;

    @Getter
    private RemoteOutput    output;

    public RemoteCommand execute(Credentials config) throws GaswException {
        output = RemoteTerminal.oneCommand(config, command);
        return this;
    }

    public boolean failed() {
        if (output.getExitCode() != 0)
            return true;
        if ( ! output.getStderr().getContent().isEmpty())
            return true;
        return false;
    }

    public abstract String result();
}
