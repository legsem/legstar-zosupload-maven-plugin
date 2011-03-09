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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * This class gets the development host target properties from Maven settings.
 * The Maven settings must contain an profile such as:
 * <p/>
 * <code>
 *     &lt;profile&gt; </br>
 *      &lt;id&gt;legstar-development&lt;/id&gt; </br>
 *      &lt;properties&gt; </br>
 *          &lt;legstar-dev-zos-host&gt;mainframe&lt;/legstar-dev-zos-host&gt; </br>
 *          &lt;legstar-dev-zos-userid&gt;P390&lt;/legstar-dev-zos-userid&gt; </br>
 *          &lt;legstar-dev-zos-password&gt;STREAM2&lt;/legstar-dev-zos-password&gt; </br>
 *      &lt;/properties&gt; </br>
 *  &lt;/profile&gt; </br>
 * </code>
 */
public class HostSettings {
	
	/** The z/OS server IP address. */
	private String _hostName;

	/** The z/OS user ID to use for authentication. */
	private String _hostUserId;

	/** The z/OS password to use for authentication. */
	private String _hostPassword;
	
	/** Element name in the maven settings that points to the host name.*/
	public static final String HOST_NAME_TAG = "legstar-dev-zos-host";
	
	/** Element name in the maven settings that points to the host user id.*/
	public static final String HOST_USER_ID_TAG = "legstar-dev-zos-userid";

	/** Element name in the maven settings that points to the host password.*/
	public static final String HOST_PASSWORD_TAG = "legstar-dev-zos-password";

	/**
	 * Reading the settings happen at construction time.
	 * <p/>
	 * Any errors are silently ignored.
	 */
	public HostSettings() {
        try {
			DocumentBuilderFactory docFac = DocumentBuilderFactory.newInstance();
			docFac.setNamespaceAware(true);
			DocumentBuilder db = docFac.newDocumentBuilder();
			String settingsFileName = System.getenv("M2_REPO") + "/../settings.xml";
			Document dom = db.parse(new File(settingsFileName));
			NodeList nodes = dom.getElementsByTagName("profile");
	        if (nodes != null) {
	        	for (int i=0; i < nodes.getLength(); i++) {
	        		Element profile = (Element) nodes.item(i);
	        		String id = getTextByTagName(profile, "id");
	        		if (id != null && id.equals("legstar-development")) {
	        			_hostName = getTextByTagName(profile, HOST_NAME_TAG);
	        			_hostUserId = getTextByTagName(profile, HOST_USER_ID_TAG);
	        			_hostPassword = getTextByTagName(profile, HOST_PASSWORD_TAG);
	        			break;
	        		}
	        	}
	        }
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Fetch the text from a child element identified by its tag name.
	 * @param parent the parent node
	 * @param tagName the child element tag name
	 * @return the child text value or null if not found
	 */
	protected String getTextByTagName(final Element parent, final String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes == null) {
			return null;
		} else {
			Node node = nodes.item(0);
			if (node instanceof Element) {
				return ((Element) node).getTextContent();
			} else {
				return null;
			}
		}
	}

	/**
	 * @return the z/OS server IP address
	 */
	public String getHostName() {
		return _hostName;
	}
	/**
	 * @return the z/OS user ID to use for authentication
	 */
	public String getHostUserId() {
		return _hostUserId;
	}
	/**
	 * @return the z/OS password to use for authentication
	 */
	public String getHostPassword() {
		return _hostPassword;
	}

}
