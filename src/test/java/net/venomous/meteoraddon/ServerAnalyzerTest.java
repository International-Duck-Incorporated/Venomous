package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ServerAnalyzer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ServerAnalyzerTest {
    @Test
    void testAnticheatClassification() throws Exception {
        ServerAnalyzer analyzer = new ServerAnalyzer();
        Method detectMethod = ServerAnalyzer.class.getDeclaredMethod("detect", List.class);
        detectMethod.setAccessible(true);

        // Grim Ping Sequence (diff = -1 with range -5..0)
        List<Integer> grimData = new ArrayList<>();
        grimData.add(-1);
        grimData.add(-2);
        grimData.add(-3);
        grimData.add(-4);
        grimData.add(-5);
        grimData.add(-6);
        String grimResult = (String) detectMethod.invoke(analyzer, grimData);
        assertNotNull(grimResult);
        assertTrue(grimResult.contains("Grim"));

        // Vulcan Ping Sequence (-30767, -30766, -25767, -25766, -25765...)
        List<Integer> vulcanData = new ArrayList<>();
        vulcanData.add(-30767);
        vulcanData.add(-30766);
        vulcanData.add(-25767);
        vulcanData.add(-25766);
        vulcanData.add(-25765);
        vulcanData.add(-25764);
        String vulcanResult = (String) detectMethod.invoke(analyzer, vulcanData);
        assertNotNull(vulcanResult);
        assertTrue(vulcanResult.contains("Vulcan"));
    }
}
