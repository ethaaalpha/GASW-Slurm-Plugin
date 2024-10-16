package fr.insalyon.creatis.gasw.executor.slurm.internals;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import fr.insalyon.creatis.gasw.GaswConfiguration;
import fr.insalyon.creatis.gasw.GaswConstants;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.execution.GaswStatus;
import fr.insalyon.creatis.gasw.executor.slurm.config.json.properties.Config;
import fr.insalyon.creatis.gasw.executor.slurm.internals.commands.RemoteCommand;
import fr.insalyon.creatis.gasw.executor.slurm.internals.commands.items.Mkdir;
import fr.insalyon.creatis.gasw.executor.slurm.internals.commands.items.Rm;
import fr.insalyon.creatis.gasw.executor.slurm.internals.terminal.RemoteFile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j;

@Getter @Log4j
public class SlurmManager {
    
    final private String            workflowId;
    final private Config            config;
    final private String            workingDir;
    final private List<SlurmJob>    jobs = new ArrayList<>();

    private boolean                 inited = false;
    private Boolean                 end;

    public SlurmManager(final String workflowId, final Config config) {
        this.workflowId = workflowId;
        this.config = config;
        this.workingDir = config.getCredentials().getWorkingDir() + workflowId;
    }

    public void init() {
        try {
            checkRemoteDirs();
            checkLocalOutputsDir();

            inited = true;
        } catch (GaswException e) {
            log.error("Failed to init the slurm manager !");
        }
    }

    /**
     * Create the necessary directories on the remote (slurm cluster)
     */
    public void checkRemoteDirs() throws GaswException {
        final List<RemoteCommand> commands = new ArrayList<>();

        commands.add(new Mkdir(getWorkingDir(), ""));
        commands.add(new Mkdir(getWorkingDir() + "/out", "-p"));
        commands.add(new Mkdir(getWorkingDir() + "/err", "-p"));
        commands.add(new Mkdir(getWorkingDir() + "/sh", "-p"));

        for (final RemoteCommand command : commands) {
            command.execute(config);

            if (command.failed()) {
                throw new GaswException("Failed to create the remotes dirs !");
            }
        }
    }

    public void checkLocalOutputsDir() {
        final String[] dirs = { GaswConstants.OUT_ROOT, GaswConstants.ERR_ROOT, "./cache" };
        
        Arrays.stream(dirs)
            .map(File::new)
            .forEach(dir -> {
                if ( ! dir.exists()) {
                    dir.mkdirs();
                }
            });
    }

    /**
     * Clean files created inside the workflow folder
     */
    public void destroy() {
        final RemoteCommand remoteCommand = new Rm(config.getCredentials().getWorkingDir() + workflowId, "-rf");

        end = true;
        try {
            if (remoteCommand.execute(config).failed()) {
                throw new GaswException("");
            }
            
        } catch (GaswException e) {
            log.error("Failed to desroy the slurm manager !");
        }
    }

    private void submitter(final SlurmJob exec) {
        if (end == null) {
            end = false;
            new Thread(this.new SlurmRunner()).start();
        }
        synchronized (this) {
            jobs.add(exec);
        }
    }

    public void submitter(final String jobID, final String command) {
        final SlurmJobData jobData = new SlurmJobData(jobID, config);
        final String wDirectoryJob = config.getCredentials().getWorkingDir() + workflowId + "/";
        final List<RemoteFile> filesUpload = new ArrayList<>();
        final List<RemoteFile> filesDownload = new ArrayList<>();

        jobData.setWorkingDir(wDirectoryJob);
        jobData.setCommand(command);

        filesUpload.add(new RemoteFile("./inv/" + jobID + "-invocation.json", wDirectoryJob));
        filesUpload.add(new RemoteFile("./config/" + jobID + "-configuration.sh", wDirectoryJob));
        filesUpload.add(new RemoteFile("./sh/" + jobID + ".sh", wDirectoryJob + "/sh"));
        filesUpload.add(new RemoteFile("./workflow.json", wDirectoryJob));

        filesDownload.add(new RemoteFile(jobData.getStderrPath(), "./err/" + jobID + ".sh.err"));
        filesDownload.add(new RemoteFile(jobData.getStdoutPath(), "./out/" + jobID + ".sh.out"));
        filesDownload.add(new RemoteFile(wDirectoryJob + jobID + ".sh.provenance.json", "./" + jobID + ".sh.provenance.json"));
        filesDownload.add(new RemoteFile(wDirectoryJob + jobID + ".batch", "./" + jobID + ".batch"));

        jobData.setFilesDownload(filesDownload);
        jobData.setFilesUpload(filesUpload);

        submitter(new SlurmJob(jobData));
    }

    public SlurmJob getJob(final String jobID) {
        return jobs.stream()
                .filter(job -> job.getData().getJobID().equals(jobID))
                .findFirst()
                .orElse(null);
    }

    public List<SlurmJob> getUnfinishedJobs() {
        return jobs.stream()
               .filter(job -> !job.isTerminated())
               .collect(Collectors.toCollection(ArrayList::new));
    }

    @NoArgsConstructor
    class SlurmRunner implements Runnable {
        private DateTime startedTime;

        @Override
        public void run() {
            try {
                startedTime = DateTime.now();
                loop();
            } catch (GaswException | InterruptedException e) {
                log.error("Somehing bad happened during the K8sRunner", e);
            }
        }

        private void sleep() throws GaswException, InterruptedException {
            final Duration diff = new Duration(startedTime, DateTime.now());

            if (diff.getStandardSeconds() > config.getOptions().getTimeToBeReady()) {
                throw new GaswException("Volume wasn't eady in 2 minutes, aborting !");
            } else {
                Thread.sleep(GaswConfiguration.getInstance().getDefaultSleeptime());
            }
        }

        private void loop() throws InterruptedException, GaswException {
            while (inited != true) {
                sleep();
            }
            while (end == false) {
                synchronized (this) {
                    for (final SlurmJob exec : getUnfinishedJobs()) {
                        if (exec.getStatus() == GaswStatus.NOT_SUBMITTED) {
                            exec.setStatus(GaswStatus.QUEUED);
                            exec.start();
                            System.err.println("je fais le start");
                        }
                    }
                }
                Thread.sleep(GaswConfiguration.getInstance().getDefaultSleeptime());
            }
        }
    }
}
