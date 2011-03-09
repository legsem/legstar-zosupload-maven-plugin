/*******************************************************************************
 * Copyright (c) 2009 LegSem.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     LegSem - initial API and implementation
 ******************************************************************************/
package com.legstar.zosjes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;

/**
 * Manages an FTP connection to a z/OS server.
 * <p/>
 * Makes it simple to upload ASCII files and submit JCL for execution.
 * 
 */
public class FtpZosClient {

    /** Apache commons net FTP client. */
    FTPClient _ftpClient;

    /** The expected string reply from FTP z/OS on job submission. */
    public static final String SUBMIT_REPLY = "250-It is known to JES as ";

    /** Condition code the way it appears in jobs held output. */
    public static final Pattern COND_CODE_PATTERN = Pattern.compile(
            "COND CODE (\\d{4})", Pattern.CASE_INSENSITIVE);

    /** Completion code in case of abend. */
    public static final Pattern COMPLETION_CODE_PATTERN = Pattern.compile(
            "COMPLETION CODE - SYSTEM=(\\d{3})", Pattern.CASE_INSENSITIVE);

    /** Job not run report. */
    public static final Pattern JCL_ERROR_PATTERN = Pattern.compile(
            "JOB NOT RUN - JCL ERROR", Pattern.CASE_INSENSITIVE);

    /** Because zOS does not have a return code for JCL error, we fake one. */
    public static final int JCL_ERROR_COND_CODE = 9;

    /**
     * No-arg constructor.
     */
    public FtpZosClient() {
        _ftpClient = new FTPClient();
        FTPClientConfig ftpConf = new FTPClientConfig(FTPClientConfig.SYST_MVS);
        ftpConf.setServerTimeZoneId("GMT");
        _ftpClient.configure(ftpConf);
    }

    /**
     * Open an FTP connection to the mainframe.
     * 
     * @param hostname the mainframe IP address
     * @param hostUserID the mainframe user ID used to authenticate
     * @param hostPassword the mainframe password used to authenticate
     * @throws IOException if connection fails
     */
    public void open(final String hostname, final String hostUserID,
            final String hostPassword) throws IOException {
        if (_ftpClient.isConnected()) {
            _ftpClient.disconnect();
        }
        _ftpClient.connect(hostname);
        if (!FTPReply.isPositiveCompletion(_ftpClient.getReplyCode())) {
            throw new IOException(hostname + " not responding");
        }
        if (!_ftpClient.login(hostUserID, hostPassword)) {
            processFtpError();
        }
    }

    /**
     * Upload a single file to the mainframe.
     * 
     * @param remote the z/OS name of the file
     * @param local the local file
     * @throws IOException if upload fails
     */
    public void upload(final String remote, final File local)
            throws IOException {
        if (!_ftpClient.sendSiteCommand("FILEtype=SEQ")) {
            processFtpError();
        }
        if (!_ftpClient.storeFile(remote, new FileInputStream(local))) {
            processFtpError();
        }
    }

    /**
     * Submits the job passed as a string.
     * <p/>
     * Upon return the job is queued in JES. It is possible to query the status
     * of the job.
     * 
     * @param jcl a string containing JCL to submit
     * @return the JES job ID that was assigned
     * @throws IOException if submit fails
     */
    public String submitJob(final String jcl) throws IOException {

        String jobId = null;
        if (!_ftpClient.sendSiteCommand("FILEtype=JES")) {
            processFtpError();
        }

        OutputStream os = _ftpClient.storeFileStream("P390JCL8");
        if (os == null) {
            processFtpError();
        }
        os.write(jcl.getBytes());
        os.close();
        if (!_ftpClient.completePendingCommand()) {
            processFtpError();
        }

        String[] replies = _ftpClient.getReplyStrings();
        if (replies == null || replies.length == 0) {
            processFtpError();
        }

        if (replies[0].startsWith(SUBMIT_REPLY)) {
            jobId = replies[0].substring(SUBMIT_REPLY.length());
        } else {
            processFtpError();
        }

        return jobId;
    }

    /**
     * Retrieves the output of a job.
     * 
     * @param jobId the job ID to retrieve
     * @return the content of the job output files
     * @throws IOException if something goes wrong
     */
    public String getJobOutput(final String jobId) throws IOException {
        return getJesResource(jobId + ".x");
    }

    /**
     * Assuming a JCL is available on the mainframe ready for submission, this
     * will submit that JCL and wait until a result is available.
     * 
     * @param remoteFile the file on the server that holds the JCL
     * @return the content of the submitted job output files
     * @throws IOException if something goes wrong
     */
    public String submitWaitForOutput(final String remoteFile)
            throws IOException {
        return getJesResource(remoteFile);
    }

    /**
     * Generic request to get something back from Jes.
     * 
     * @param a job id or file name holding JCL to submit
     * @return the content of the submitted job output files
     * @throws IOException if something goes wrong
     */
    public String getJesResource(final String jesResource) throws IOException {
        if (!_ftpClient.sendSiteCommand("FILEtype=JES")) {
            processFtpError();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!_ftpClient.retrieveFile(jesResource, baos)) {
            processFtpError();
        }
        baos.close();
        String result = baos.toString("UTF-8"); // TODO why UTF-8?
        return result;
    }

    /**
     * Extracts the highest condition code from a job output. First look for a
     * potential abend or jcl error then, if none if found, examine each step
     * condition code and keep the highest.
     * 
     * @param heldOutput the job held output
     * @return the highest condition code
     */
    public int getHighestCondCode(final String heldOutput) {
        int maxCondCode = -1;
        Matcher matcher = COMPLETION_CODE_PATTERN.matcher(heldOutput);
        if (matcher.find()) {
            int completionCode = Integer.parseInt(matcher.group(1));
            return completionCode;
        }

        matcher = JCL_ERROR_PATTERN.matcher(heldOutput);
        if (matcher.find()) {
            return JCL_ERROR_COND_CODE;
        }

        matcher = COND_CODE_PATTERN.matcher(heldOutput);
        while (matcher.find()) {
            int condCode = Integer.parseInt(matcher.group(1));
            maxCondCode = (condCode > maxCondCode) ? condCode : maxCondCode;
        }
        return maxCondCode;
    }

    /**
     * Close an FTP connection to the mainframe.
     * <p/>
     * Not a real problem if we don't, the mainframe never keeps a connection
     * around for very long anyway.
     * 
     * @throws IOException if close fails
     */
    public void close() throws IOException {
        if (_ftpClient.isConnected()) {
            _ftpClient.logout();
            _ftpClient.disconnect();
        }
    }

    /**
     * Turns all FTP errors to IO exceptions.
     * 
     * @throws IOException systematic
     */
    protected void processFtpError() throws IOException {
        String errors[] = _ftpClient.getReplyStrings();
        _ftpClient.disconnect();

        if (errors == null || errors.length == 0) {
            throw new IOException("Unknown error.");
        }
        throw new IOException(errors[0]);
    }
}
