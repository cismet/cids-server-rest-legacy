/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.cismet.cidsx.client.connector;


import com.sun.jersey.core.util.Base64;
import java.io.IOException;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Pascal Dihé <pascal.dihe@cismet.de>
 */
public class RESTfulInterfaceTest extends RESTfulInterfaceConnector {
    
    private static String HOST;
    private static String BASIC_AUTH_STRING;
    
    private final static Logger LOGGER = Logger.getLogger(RESTfulInterfaceTest.class);
    
    
    public RESTfulInterfaceTest() throws IOException 
    {
        super("http://localhost:8890");
        
        
    }
    
     /**
     * DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        final Properties p = new Properties();
        p.put("log4j.appender.Remote", "org.apache.log4j.net.SocketAppender");
        p.put("log4j.appender.Remote.remoteHost", "localhost");
        p.put("log4j.appender.Remote.port", "4445");
        p.put("log4j.appender.Remote.locationInfo", "true");
        p.put("log4j.rootLogger", "ALL,Remote");
        org.apache.log4j.PropertyConfigurator.configure(p);
        
        
        PropertyResourceBundle bundle;
        
        try
        {
            bundle = new PropertyResourceBundle(RESTfulInterfaceConnector.class.getResourceAsStream("service.properties"));
        } catch(Exception ex) {
            LOGGER.error("could not find local properties file 'service.properties': " + ex.getMessage(), ex);
            throw ex;
        }
        
        assertNotNull(bundle.getString("host"));
        assertNotNull(bundle.getString("username"));
        assertNotNull(bundle.getString("domain"));
        assertNotNull(bundle.getString("password"));
        
        HOST = bundle.getString("host");
        BASIC_AUTH_STRING = "Basic "
                        + new String(Base64.encode(bundle.getString("username") 
                                + "@" + bundle.getString("domain") 
                                + ":" + bundle.getString("password")));
        
    }

    /**
     * DOCUMENT ME!
     *
     * @throws  Exception  DOCUMENT ME!
     */
    @AfterClass
    public static void tearDownClass() throws Exception {
    }
    
     /**
     * DOCUMENT ME!
     */
    @Before
    public void setUp() {
    }

    /**
     * DOCUMENT ME!
     */
    @After
    public void tearDown() {
    }
    
    @Test
    public void doNothing() {
        
    }
}
