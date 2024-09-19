package fr.insalyon.creatis.gasw.executor.slurm.internals.commands.items;

import fr.insalyon.creatis.gasw.executor.slurm.internals.commands.RemoteCommand;

public class Mkdir extends RemoteCommand {

    public Mkdir (String folderToCreate, String options) {
        super("mkdir " + options + " " + folderToCreate);
    }

    public String result() {
        return "";
    }
}
