package fr.insalyon.creatis.gasw.executor.slurm;

import java.io.File;

import org.apache.sshd.common.config.SyslogFacilityValue;

import fr.insalyon.creatis.gasw.GaswConstants;
import fr.insalyon.creatis.gasw.GaswException;
import fr.insalyon.creatis.gasw.GaswExitCode;
import fr.insalyon.creatis.gasw.GaswOutput;
import fr.insalyon.creatis.gasw.execution.GaswOutputParser;
import fr.insalyon.creatis.gasw.executor.slurm.internals.SlurmJob;

public class SlurmOutputParser extends GaswOutputParser{

    private File            stdOut;
    private File            stdErr;
    final private SlurmJob  job;

    public SlurmOutputParser(SlurmJob job) {
        super(job.getJobID());
        this.job = job;
    }

    @Override
    public GaswOutput getGaswOutput() throws GaswException {
        GaswExitCode gaswExitCode = GaswExitCode.EXECUTION_CANCELED;
        job.download();
        System.out.println("j'ai bien tout download");

        int exitCode;

        stdOut = getAppStdFile(GaswConstants.OUT_EXT, GaswConstants.OUT_ROOT);
        stdErr = getAppStdFile(GaswConstants.ERR_EXT, GaswConstants.ERR_ROOT);

        moveProvenanceFile(".");

        exitCode = parseStdOut(stdOut);
        exitCode = parseStdErr(stdErr, exitCode);

        switch (exitCode) {
            case 0:
                gaswExitCode = GaswExitCode.SUCCESS;
                break;
            case 1:
                gaswExitCode = GaswExitCode.ERROR_READ_GRID;
                break;
            case 2:
                gaswExitCode = GaswExitCode.ERROR_WRITE_GRID;
                break;
            case 6:
                gaswExitCode = GaswExitCode.EXECUTION_FAILED;
                break;
            case 7:
                gaswExitCode = GaswExitCode.ERROR_WRITE_LOCAL;
                break;
        }

        return new GaswOutput(job.getJobID(), gaswExitCode, "", uploadedResults,
                appStdOut, appStdErr, stdOut, stdErr);
    }

    @Override
    protected void resubmit() throws GaswException {
        throw new GaswException("");
    }
}