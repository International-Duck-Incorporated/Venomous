package net.venomous.meteoraddon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class MixinConfigTest {
    @Test
    void testMixinJsonConfiguration() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("venomous.mixins.json");
        assertNotNull(is, "venomous.mixins.json should exist in classpath resources");

        JsonObject json = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(json.get("required").getAsBoolean());
        assertEquals("net.venomous.meteoraddon.mixin", json.get("package").getAsString());
        assertEquals("JAVA_25", json.get("compatibilityLevel").getAsString());

        JsonArray clientMixins = json.getAsJsonArray("client");
        assertNotNull(clientMixins);
        assertTrue(clientMixins.size() >= 8);

        for (JsonElement elem : clientMixins) {
            String mixinSimpleName = elem.getAsString();
            String fullClassName = "net.venomous.meteoraddon.mixin." + mixinSimpleName;
            Class<?> clazz = Class.forName(fullClassName);
            assertNotNull(clazz, "Class should be loadable: " + fullClassName);
        }
    }
}
