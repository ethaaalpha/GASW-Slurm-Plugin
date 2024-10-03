package fr.insalyon.creatis.gasw.executor.slurm.internals.commands.items;

import fr.insalyon.creatis.gasw.executor.slurm.internals.commands.RemoteCommand;
import fr.insalyon.creatis.gasw.executor.slurm.internals.terminal.RemoteStream;

public class Tracejob extends RemoteCommand {

    public Tracejob(String jobID) {
        super("2>/dev/null tracejob " + jobID + " | grep state | tail -n 1 | xargs");
    }

    public String result() {
        RemoteStream out = getOutput().getStdout();
        String[] line = out.getRow(out.getLines().length - 1);

        return line[line.length - 1];
    } 
}