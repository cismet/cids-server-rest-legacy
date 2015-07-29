/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
package de.cismet.cidsx.server.cores.legacy;

import Sirius.server.middleware.types.MetaClass;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import org.openide.util.lookup.ServiceProvider;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;

import java.io.ByteArrayOutputStream;

import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import javax.servlet.http.HttpServletResponse;

import javax.swing.ImageIcon;

import de.cismet.cids.dynamics.CidsBean;

import de.cismet.cidsx.server.api.types.CidsAttribute;
import de.cismet.cidsx.server.api.types.CidsClass;
import de.cismet.cidsx.server.api.types.User;
import de.cismet.cidsx.server.api.types.legacy.CidsClassFactory;
import de.cismet.cidsx.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cidsx.server.cores.CidsServerCore;
import de.cismet.cidsx.server.cores.EntityInfoCore;
import de.cismet.cidsx.server.exceptions.CidsServerException;
import de.cismet.cidsx.server.exceptions.EntityInfoNotFoundException;

/**
 * DOCUMENT ME!
 *
 * @author   thorsten
 * @version  1.0
 */
@Slf4j
@ServiceProvider(service = CidsServerCore.class)
public class LegacyEntityInfoCore implements EntityInfoCore {

    //~ Static fields/initializers ---------------------------------------------

    protected static final ObjectMapper MAPPER = new ObjectMapper(new JsonFactory());

    //~ Methods ----------------------------------------------------------------

    @Override
    public List<JsonNode> getAllClasses(final User user, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAllClasses");
        }

        try {
            final List<JsonNode> all = new ArrayList<JsonNode>();
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass[] metaClasses = LegacyCoreBackend.getInstance()
                        .getService()
                        .getClasses(legacyUser, legacyUser.getDomain());
            if (metaClasses != null) {
                for (final MetaClass metaClass : metaClasses) {
                    final CidsClass cidsClass = CidsClassFactory.getFactory()
                                .restCidsClassFromLegacyCidsClass(metaClass);
                    final JsonNode node = (JsonNode)MAPPER.convertValue(cidsClass, JsonNode.class);
                    all.add(node);
                }

                // fill the class key cache if required
                if (!LegacyCoreBackend.getInstance().getClassNameCache().isDomainCached(role)) {
                    LegacyCoreBackend.getInstance().getClassNameCache().fillCache(role, metaClasses);
                }
            }

            return all;
        } catch (final Exception ex) {
            final String message = "error while getting all classes: " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode getClass(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getClass with classKey '" + classKey + "'.");
        }

        try {
            if (LegacyCoreBackend.getInstance().getClassCache().containsKey(classKey)) {
                if (log.isDebugEnabled()) {
                    log.debug("loading class with classKey '" + classKey + "' from class cache");
                }
                return LegacyCoreBackend.getInstance().getClassCache().get(classKey);
            }

            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaClassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting class with classKey '" + classKey
                            + "': class not found!";
                log.warn(message);
                throw new EntityInfoNotFoundException(message, classKey);
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final JsonNode node = MAPPER.convertValue(cidsClass, JsonNode.class);
            if (log.isDebugEnabled()) {
                log.debug("adding class with classKey '" + classKey + "' to class cache");
            }
            LegacyCoreBackend.getInstance().getClassCache().put(classKey, node);

            return node;
        } catch (final Exception ex) {
            final String message = "error while getting class with classKey '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode getAttribute(final User user,
            final String classKey,
            final String attributeKey,
            final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getAttribute with classKey '" + classKey + "' and attributeKey '" + attributeKey + "'.");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaClassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting attribute with classKey '" + classKey
                            + "' and attributeKey '" + attributeKey + "': class not found!";
                log.warn(message);
                throw new EntityInfoNotFoundException(message, classKey);
            }

            final CidsClass cidsClass = CidsClassFactory.getFactory().restCidsClassFromLegacyCidsClass(metaClass);
            final CidsAttribute cidsAttribute = cidsClass.getAttribute(attributeKey);
            final JsonNode node = MAPPER.convertValue(cidsAttribute, JsonNode.class);
            return node;
        } catch (final Exception ex) {
            final String message = "error while getting attribute with classKey '" + classKey
                        + "' and attributeKey '" + attributeKey + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public JsonNode emptyInstance(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("emptyInstance with classKey '" + classKey + "'.");
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);

            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaClassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting empty instance with classKey '" + classKey
                            + "': class not found!";
                log.error(message);
                throw new CidsServerException(message, message, HttpServletResponse.SC_FOUND);
            }

            final CidsBean beanNew = metaClass.getEmptyInstance().getBean();
            final JsonNode node = MAPPER.reader().readTree(beanNew.toJSONString(true));
            return node;
        } catch (final Exception ex) {
            final String message = "error while getting empty instance with classKey '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public String getCoreKey() {
        return "core.legacy.entityInfo"; // NOI18N
    }

    @Override
    public byte[] getClassIcon(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getClassIcon with classKey '" + classKey + "'");
        }

        // check the cache!
        if (LegacyCoreBackend.getInstance().getClassIconCache().containsKey(classKey)) {
            return LegacyCoreBackend.getInstance().getClassIconCache().get(classKey);
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaClassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting class with classKey '" + classKey
                            + "': class not found!";
                log.warn(message);
                throw new EntityInfoNotFoundException(message, classKey);
            }

            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final byte[] iconData = metaClass.getIconData();

            // FIXME: byte to icon to byte ?!
            final Image image = new ImageIcon(iconData).getImage();
            final BufferedImage bimage = new BufferedImage(image.getWidth(null),
                    image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);
            final Graphics2D bGr = bimage.createGraphics();
            bGr.drawImage(image, 0, 0, null);
            bGr.dispose();
            ImageIO.write(bimage, "png", bos);
            bos.flush();
            bos.close();
            final byte[] icon = bos.toByteArray();

            LegacyCoreBackend.getInstance().getClassIconCache().put(classKey, icon);

            return icon;
        } catch (final Exception ex) {
            final String message = "error while getting class icon for class '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }

    @Override
    public byte[] getObjectIcon(final User user, final String classKey, final String role) {
        if (log.isDebugEnabled()) {
            log.debug("getObjectIcon with classKey '" + classKey + "'");
        }

        // check the cache!
        if (LegacyCoreBackend.getInstance().getObjectIconCache().containsKey(classKey)) {
            return LegacyCoreBackend.getInstance().getObjectIconCache().get(classKey);
        }

        try {
            final Sirius.server.newuser.User legacyUser = LegacyCoreBackend.getInstance().getCidsUser(user, role);
            final MetaClass metaClass = LegacyCoreBackend.getInstance().getMetaClassForClassname(classKey, legacyUser);
            if (metaClass == null) {
                final String message = "error while getting class with classKey '" + classKey
                            + "': class not found!";
                log.warn(message);
                throw new EntityInfoNotFoundException(message, classKey);
            }

            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final byte[] iconData = metaClass.getObjectIconData();

            // FIXME: byte to icon to byte ?!
            final Image image = new ImageIcon(iconData).getImage();
            final BufferedImage bimage = new BufferedImage(image.getWidth(null),
                    image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);
            final Graphics2D bGr = bimage.createGraphics();
            bGr.drawImage(image, 0, 0, null);
            bGr.dispose();
            ImageIO.write(bimage, "png", bos);
            bos.flush();
            bos.close();
            final byte[] icon = bos.toByteArray();

            LegacyCoreBackend.getInstance().getObjectIconCache().put(classKey, icon);

            return icon;
        } catch (final Exception ex) {
            final String message = "error while getting object icon for class '" + classKey
                        + "': " + ex.getMessage();
            log.error(message, ex);
            throw new CidsServerException(message, message,
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex);
        }
    }
}
