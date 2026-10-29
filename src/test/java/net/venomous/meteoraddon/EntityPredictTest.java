package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.EntityPredict;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EntityPredictTest {
    @Test
    void zeroTicksReturnsPositionUnchanged() {
        double[] out = EntityPredict.predict(1.0, 2.0, 3.0, 0.5, 0.25, -0.1, false, 0);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, out, 1e-9);
    }

    @Test
    void negativeTicksReturnsPositionUnchanged() {
        double[] out = EntityPredict.predict(1.0, 2.0, 3.0, 0.5, 0.25, -0.1, false, -3);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, out, 1e-9);
    }

    @Test
    void groundFrictionDampsHorizontalMotion() {
        double[] one = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, 0.0, 0.0, true, 1);
        // on ground: vy reset to 0, x advances by vx=1, then vx *= 0.546 (applies next tick only)
        assertEquals(1.0, one[0], 1e-9);
        assertEquals(64.0, one[1], 1e-9);
        assertEquals(0.0, one[2], 1e-9);

        double[] two = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, 0.0, 0.0, true, 2);
        // second tick advances by 0.546
        assertEquals(1.0 + 0.546, two[0], 1e-9);
    }

    @Test
    void airDragAndGravityApply() {
        double[] one = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, 1.0, 0.0, false, 1);
        assertEquals(1.0, one[0], 1e-9);
        // y += vy (1.0) then gravity: vy becomes (1-0.08)*0.98 = 0.9016, but y already advanced
        assertEquals(65.0, one[1], 1e-9);
        assertEquals(0.0, one[2], 1e-9);

        double[] two = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, 1.0, 0.0, false, 2);
        // second horizontal advance uses dragged vx = 0.91
        assertTrue(two[0] > 1.0 && two[0] < 2.0);
    }

    @Test
    void returnsThreeElementArray() {
        double[] out = EntityPredict.predict(0, 0, 0, 0, 0, 0, false, 5);
        assertEquals(3, out.length);
    }
}
