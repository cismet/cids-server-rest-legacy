/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import de.cismet.cidsx.server.cores.CidsServerCore;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@Parameters(separators = "=")
@ServiceProvider(service = CidsServerCore.class)
public class LegacyCidsServerCore implements CidsServerCore {

    //~ Static fields/initializers ---------------------------------------------

    @Parameter(
        names = { "-core.legacy.callserver", "--core.legacy.domain" },
        required = true,
        description = "domain name of the unerlying domain server"
    )
    static String callserver;
    @Parameter(
        names = { "-core.legacy.compressionEnabled", "--core.legacy.compressionEnabled" },
        required = false,
        description = "whether or not the compression should be enabled"
    )
    static String compressionEnabled;
    @Parameter(
        names = { "-core.legacy.testdomain", "--core.legacy.testdomain" },
        required = false,
        description = "domain name to use for unittests"
    )
    static String testDomain;
    @Parameter(
        names = { "-core.legacy.clientName", "--core.legacy.clientName" },
        required = false,
        description = "client name to use for connection"
    )
    static String clientName;
    @Parameter(
        names = { "-core.legacy.testuser", "--core.legacy.testuser" },
        required = false,
        description = "user name to use for unittests"
    )
    static String testUser;
    @Parameter(
        names = { "-core.legacy.testrole", "--core.legacy.testpassword" },
        required = false,
        description = "password to use for unittests"
    )
    static String testPassword;

    //~ Methods ----------------------------------------------------------------

    @Override
    public String getCoreKey() {
        return "core.legacy.cidsServer"; // NOI18N
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getCallserver() {
        return callserver;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static boolean isCompressionEnabled() {
        return "true".equalsIgnoreCase(compressionEnabled);
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getTestDomain() {
        return testDomain;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getTestUser() {
        return testUser;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getClientName() {
        return clientName;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public static String getTestPassword() {
        return testPassword;
    }
}
