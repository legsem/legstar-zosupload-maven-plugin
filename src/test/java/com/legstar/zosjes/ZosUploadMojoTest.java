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
import java.util.LinkedList;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;

import com.legstar.zosjes.HostSettings;
import com.legstar.zosjes.ZosUploadMojo;

/**
 * Unit test the plugin. 
 *
 */
public class ZosUploadMojoTest extends AbstractMojoTestCase {

	/** Parameters needed to submit a job to z/OS. */
	private HostSettings _hostSettings = new HostSettings();
	
	/** Setup the plugin environment. */
	protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * tests the proper discovery and configuration of the mojo
     *
     * @throws Exception
     */
 	public void testConfiguration() throws Exception {

        ZosUploadMojo mojo = new ZosUploadMojo();
        configureMojo (mojo, "zosjes-maven-plugin", getTestPom() );
        
        assertEquals(_hostSettings.getHostName(),
        		(String)getVariableValueFromObject(mojo, "hostName") );
        assertEquals(_hostSettings.getHostUserId(),
        		(String)getVariableValueFromObject(mojo, "hostUserId") );
        assertEquals(_hostSettings.getHostPassword(),
        		(String)getVariableValueFromObject(mojo, "hostPassword") );
        assertEquals("zos",
        	((File) getVariableValueFromObject(mojo, "inputFolder")).getName());
        assertEquals("P390.LIB",
            	(String) getVariableValueFromObject(mojo, "remoteFilesPrefix"));
    }
    
    /**
     * Test that we are able to submit actual jobs.
     * @throws Exception if test fails
     */
    public void testExecution() throws Exception {
        ZosUploadMojo mojo = new ZosUploadMojo();
        configureMojo (mojo, "zosjes-maven-plugin", getTestPom() );
        mojo.execute();
    }
    
    /**
     * Test that we are able to submit actual jobs when explicitly selected.
     * @throws Exception if test fails
     */
    public void testExecutionFromList() throws Exception {
        ZosUploadMojo mojo = new ZosUploadMojo();
        configureMojo (mojo, "zosjes-maven-plugin", getTestPom() );
        mojo.jclFileNames = new LinkedList < String >();
        mojo.jclFileNames.add("LISTCAT");
        mojo.execute();
    }
    
    /**
     * Test what happens when a job fails.
     * @throws Exception if test fails
     */
    public void testFailedExecution() {
        try {
			ZosUploadMojo mojo = new ZosUploadMojo();
			configureMojo (mojo, "zosjes-maven-plugin", getTestPom() );
			mojo.remoteFilesPrefix = "P390.LIB";
			mojo.inputFolder = new File("src/test/resources/zosfail");
			mojo.execute();
		} catch (Exception e) {
			assertEquals("Job submitted to z/OS failed.  Highest condition code: 12",
					e.getMessage());
		}
    }

    /**
     * Return a test pom where variables have been replaced by actual values.
     * @throws IOException if test pom cannot be read
     */
    protected File getTestPom() throws IOException {
        File testPom = new File( getBasedir(),
		"target/test-classes/unit/basic-test-plugin-config.xml" );
        String testPomStr = FileUtils.readFileToString(testPom);
        testPomStr = testPomStr.replace("${" + HostSettings.HOST_NAME_TAG + "}",
        		_hostSettings.getHostName());
        testPomStr = testPomStr.replace("${" + HostSettings.HOST_USER_ID_TAG + "}",
        		_hostSettings.getHostUserId());
        testPomStr = testPomStr.replace("${" + HostSettings.HOST_PASSWORD_TAG + "}",
        		_hostSettings.getHostPassword());
        File tempPom = File.createTempFile("legstar", "test.pom.xml");
        tempPom.deleteOnExit();
        FileUtils.writeStringToFile(tempPom, testPomStr);
        return tempPom;
    	
    }
}
