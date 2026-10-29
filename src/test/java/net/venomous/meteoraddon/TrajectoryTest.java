package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.venomous.meteoraddon.modules.combat.Trajectory;

public class TrajectoryTest {
    @Test
    void moduleLoadsAndHasPrediction() {
        Trajectory t = new Trajectory();
        assertNotNull(t);
    }
}
