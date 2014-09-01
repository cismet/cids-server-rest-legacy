
package de.cismet.cids.server.cores.legacy;

import de.cismet.cids.server.backend.legacy.LegacyCoreBackend;
import de.cismet.cids.server.cores.EntityCoreNGTest;
import org.testng.annotations.DataProvider;

public class LegacyEntityCoreNGTest extends EntityCoreNGTest {
    
    @DataProvider(name = "EntityCoreInstanceDataProvider")
    public Object[][] getEntityCoreInstance() {
        LegacyCoreBackend.getInstance().setEnableTestMode(true);
        return new Object[][]{
            {
                new LegacyEntityCore()
            }
        };
    }
    
}
