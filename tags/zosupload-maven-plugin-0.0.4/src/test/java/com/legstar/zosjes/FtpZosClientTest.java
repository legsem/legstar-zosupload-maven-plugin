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

import com.legstar.zosjes.FtpZosClient;

import junit.framework.TestCase;

/**
 * Test the FtpZosClient class.
 *
 */
public class FtpZosClientTest extends TestCase {
	
	
	/** Parameters needed to submit a job to z/OS. */
	private static final HostSettings _hostSettings = new HostSettings();
	
	/**
	 * A JCL that does a listcat.
	 */
	private static final String LISTCAT_JCL =
		"//P390LSTC   JOB (20,FB3),FADY,\n"
		+ "//            CLASS=A,MSGCLASS=X,NOTIFY=&SYSUID\n"
		+ "//STEPLC   EXEC PGM=IDCAMS\n"
		+ "//SYSPRINT DD  SYSOUT=*\n"
		+ "//SYSIN    DD  *\n"
		+ "  LISTCAT ENT(CICSTS23.CICS.FILEA) ALL\n"
		+ "/*\n"
		;

	/**
	 * Open on an invalid host.
	 */
	public void testOpenWrongHost() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open("zaratoustra", null, null);
			fail();
		} catch (IOException e) {
			assertEquals("java.net.UnknownHostException: zaratoustra", e.toString());
		}
	}

	/**
	 * Open with invalid credentials.
	 */
	public void testOpenWrongCredentials() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), "zara", "toustra");
			fail();
		} catch (IOException e) {
			assertEquals("java.io.IOException: 530 PASS command failed", e.toString());
		}
	}

	/**
	 * Do 2 sequences of open/close.
	 */
	public void testMultipleOpenClose() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			ftpzosClient.close();
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			ftpzosClient.close();
		} catch (IOException e) {
			fail(e.toString());
		}
	}

	/**
	 * Test submit a basic job.
	 */
	public void testSubmitBasicJob() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			String jobName = ftpzosClient.submitJob(LISTCAT_JCL);
			assertNotNull(jobName);
			ftpzosClient.close();
		} catch (IOException e) {
			fail(e.toString());
		}
	}

	/**
	 * Test submit a basic job and get the first spool file.
	 * When we attempt to retrieve the job output it might still
	 * be running and therefore the get fails. In that case, we
	 * give it some time and try again.
	 */
	public void testSubmitBasicJobAndGetSpoolFile() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			String jobId = ftpzosClient.submitJob(LISTCAT_JCL);
			assertNotNull(jobId);
			String result = null;
			try {
				result = ftpzosClient.getJobOutput(jobId);
			} catch (IOException e) {
				if (e.getMessage().contains("550 Jobid " + jobId + " not found for JESJOBNAME")) {
					Thread.sleep(3000L);
					/* Upon previous failure, the host severed the connection so we
					 * must reconnect. Otherwise FTPClient gets NPE.*/
					ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
					result = ftpzosClient.getJobOutput(jobId);
				}
			}
			assertNotNull(result);
			ftpzosClient.close();
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.toString());
		}
	}
	
	public void testDoubleGet() {
		
	}

	/**
	 * Test submit a remote job and get the first spool file.
	 */
	public void testSubmitRemoteJobAndGetSpoolFile() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			String result = ftpzosClient.submitWaitForOutput("'P390.LIB.CNTL(LISTCAT)'");
			assertNotNull(result);
			ftpzosClient.close();
		} catch (IOException e) {
			fail(e.toString());
		}
	}
	
	/**
	 * Check that we can upload a file.
	 */
	public void testUpload() {
		FtpZosClient ftpzosClient = new FtpZosClient();
		try {
			ftpzosClient.open(_hostSettings.getHostName(), _hostSettings.getHostUserId(), _hostSettings.getHostPassword());
			ftpzosClient.upload("'P390.LIB.CNTL(LISTCAT)'",
					new File("src/test/resources/zos/CNTL/LISTCAT"));
			ftpzosClient.close();
		} catch (IOException e) {
			fail(e.toString());
		}
	}
}
