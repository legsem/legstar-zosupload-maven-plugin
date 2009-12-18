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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Maven plugin for source upload to z/OS.
 * <p/>
 * This maven plugin can be used to send ASCII files to PDS members.
 * JCL files are also submitted for execution once uploaded and the
 * execution return code is checked.
 * <p/>
 * The input is a folder that contains sub folders. Each sub folder
 * corresponds to a PDS on the mainframe. The name of the sub folder
 * must be the last index in the PDS name. For instance, assuming
 * an input folder hierarchy like so:
 *     zos/C370
 *         H370
 *         CNTL
 * And a PDS prefix "P390.LEGSTAR.VxRyMz", the plugin will upload
 * all files from zos/C370 to 'P390.LEGSTAR.VxRyMz.C370'
 *           from zos/H370 to 'P390.LEGSTAR.VxRyMz.H370'
 *           from zos/CNTL to 'P390.LEGSTAR.VxRyMz.CNTL'
 * <p/>
 * file names should not have any extensions and must be valid PDS member names
 * (i.e. must be less than 9 characters).
 * <p/>
 * The folder CNTL has special meaning as all members are assumed
 * to be submittable to jes.
 * 
 * @goal submit
 * @description z/OS source upload plugin
 */
public class ZosUploadMojo extends AbstractMojo {

    /**
     * The z/OS FTP server IP address.
     *
     * @parameter expression="${legstar.hostName}" default-value="mainframe"
     */
    protected String hostName;

    /**
     * The z/OS user ID to use for authentication.
     * Must have enough authority to submit jobs on the mainframe.
     *
     * @parameter expression="${legstar.hostUserId}"
     */
    protected String hostUserId;

    /**
     * The z/OS password to use for authentication.
     *
     * @parameter expression="${legstar.hostPassword}"
     */
    protected String hostPassword;
    
    /**
     * The target z/OS file name prefix.
     * <p/>
     * The process appends the local folder name to that prefix to make up a z/OS PDS name.
     *
     * @parameter
     */
    protected String remoteFilesPrefix;

    /**
     * The local folder containing sub folders whose content is to be uploaded.
     * <p/>
     * Each sub folder corresponds to a z/OS PDS and files in the sub folder
     * will be uploaded as members of that PDS.
     *
     * @parameter
     */
    protected File inputFolder;
    
    
    /**
     * Useful to disable any uploads (for instance if mainframe is not available).
     *
     * @parameter expression="${legstar.skipUpload}" default-value="false"
     */
    protected boolean skipUpload;
    
    
    /**
     * Useful when it is necessary to enforce an order of JCL executions.
     * <p/>
     * When this list is empty, JCLs are picked up from the CNTL sub folder
     * and submitted in alphabetical order which is fine if JCLs are 
     * independent from one another.
     * <p/>
     * In the case where you want a specific order or you want to run only some
     * of the JCLs in CNTL, pass an ordered list of JCL file names, as they 
     * appear in the CNTL sub folder, to be executed.
     * 
     * @parameter
     */
    protected List < String > jclFileNames;

    /**
     * {@inheritDoc}
     */
    public void execute() throws MojoExecutionException, MojoFailureException {
    	if (skipUpload) {
    		getLog().info("Uploads skipped per request.");
    		return;
    	}
		getLog().info("Upload sources to z/OS server: " + hostName + ", user id: " + hostUserId);
		FtpZosClient ftpZosClient = new FtpZosClient();
		try {
			ftpZosClient.open(hostName, hostUserId, hostPassword);
			doUploads(ftpZosClient);
		} catch (IOException e) {
			throw new MojoExecutionException("Upload sources to z/OS failed", e);
		} finally {
			try {
				ftpZosClient.close();
			} catch (IOException e) {
				// Just ignore. mainframe will wipe up connection anyway
			}
		}
	}
    
    /**
     * Uploads all eligible source files from inputfolder and then
     * submit the JCLs if any.
     * <p/>
     * Each sub folder in inputfolder (apart from hidden folders whose
     * name start with a period) is assumed to correspond to a PDS on
     * z/OS. The name of that PDS is built from remoteFilesPrefix and
     * the sub folder name.
     * <p/>
     * A sub folder named CNTL is assumed to contain JCL that we want 
     * to submit for execution. If the names of JCLs to submit are
     * explicitly requested using jclFileNames, then only these JCLs
     * are submitted. Otherwise all files in CNTL are submitted.
     * 
     * @param ftpZosClient the FTP client
     * @throws MojoFailureException if job submission fails
     * @throws MojoExecutionException if upload fails
     */
    protected void doUploads(
    		final FtpZosClient ftpZosClient) throws MojoFailureException, MojoExecutionException {

    	if (inputFolder == null || !inputFolder.isDirectory()) {
    		throw new MojoFailureException("Invalid input folder " + inputFolder);
    	}

    	File[] subFolders = inputFolder.listFiles();
    	if (subFolders == null || subFolders.length == 0) {
    		getLog().warn("Folder " + inputFolder + " is empty.");
    		return;
    	}

    	/* Get each sub folder in turn if they are not hidden, build a PDS name
    	 * and upload all files in the sub folder. */
    	for (File subFolder : subFolders) {
    		if (subFolder.isDirectory() && subFolder.getName().charAt(0) != '.') {
	    		String name = subFolder.getName();
	    		String remote = remoteFilesPrefix + '.' + name;
	    		for (File local : subFolder.listFiles()) {
	    			if (local.isFile()) {
	    				upload(ftpZosClient,
	    						"'" + remote + "(" + local.getName() + ")'", local);
	    			}
	    		}
    		}
    	}

    	/* Submit JCLs. All of them if no list otherwise pick them from the list.*/
    	File cntlFolder = new File(inputFolder, "CNTL");
    	if (cntlFolder.exists() && cntlFolder.isDirectory()) {
    		if (jclFileNames == null || jclFileNames.size() == 0) {
	    		for (File local : cntlFolder.listFiles()) {
	    			if (local.isFile()) {
	    				submitJcl(ftpZosClient, "'" 
	    						+ remoteFilesPrefix + ".CNTL"
	    						+ "(" + local.getName() + ")'");
	    			}
	    		}
    		} else {
    			for (String jclFileName : jclFileNames) {
    				File local = new File(cntlFolder, jclFileName);
    				submitJcl(ftpZosClient, "'" 
    						+ remoteFilesPrefix + ".CNTL"
    						+ "(" + local.getName() + ")'");
    			}
    		}
    	}
    }
    
    /**
	 * Upload a single file to the mainframe.
     * @param ftpZosClient the FTP client
	 * @param remote the z/OS name of the file
	 * @param local the local file
     * @throws MojoExecutionException if upload fails
     */
    protected void upload(
    		final FtpZosClient ftpZosClient,
    		final String remote,
    		final File local) throws MojoExecutionException {
		try {
			getLog().info("Uploading: " + local + " to " + remote);
			ftpZosClient.upload(remote, local);
		} catch (IOException e) {
			throw new MojoExecutionException("Upload to z/OS failed", e);
		}
    }
    
    /**
     * Submit a JCL to JES and wait for an output. Then get the output and
     * check for any invalid condition codes.
     * @param ftpZosClient the FTP client
     * @param jclFileName the z/OS file name holding the JCL
     * @throws MojoFailureException if the job submitted fails
     * @throws MojoExecutionException if something is wrong with the FTP connection
     */
    protected void submitJcl(
    		final FtpZosClient ftpZosClient,
    		final String jclFileName) throws MojoFailureException, MojoExecutionException {
		try {
			getLog().info("Submit job from: " + jclFileName);
			String heldOutput = ftpZosClient.submitWaitForOutput(jclFileName);
			int maxCondCode = ftpZosClient.getHighestCondCode(heldOutput);
			if (maxCondCode > 4) {
				throw new MojoFailureException(
						"Job submitted to z/OS failed.  Highest condition code: " + maxCondCode);
			}
			getLog().info("Job from: " + jclFileName + " succeeded. Highest condition code: " + maxCondCode);
		} catch (IOException e) {
			throw new MojoExecutionException("Job submission to z/OS failed", e);
		}
    }

}
