package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AddonMetadataTest {
    @Test
    void testAddonProperties() {
        VenomousAddon addon = new VenomousAddon();
        assertEquals("7.0.0", VenomousAddon.VERSION);
        assertEquals("net.venomous.meteoraddon", addon.getPackage());
        assertNotNull(addon.getRepo());
        assertEquals("International-Duck-Incorporated", addon.getRepo().owner());
        assertEquals("Venomous", addon.getRepo().name());
        assertEquals("Venomous", VenomousAddon.CATEGORY.name);
        assertEquals("Venomous OP", VenomousAddon.CATEGORY_OP.name);
    }
}
