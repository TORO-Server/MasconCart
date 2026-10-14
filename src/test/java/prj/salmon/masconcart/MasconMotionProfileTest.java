package prj.salmon.masconcart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MasconMotionProfileTest {
    @Test
    void onlyP1UsesReducedConstantAcceleration() {
        MasconMotionProfile profile = new MasconMotionProfile();
        assertEquals(0.005, profile.update(0.0, 1, 0.1), 1e-12);
        assertEquals(0.105, profile.update(0.1, 1, 0.1), 1e-12);
        for (int notch = 2; notch <= 7; notch++) {
            assertEquals(0.02, profile.update(0.0, notch, 0.1), 1e-12);
        }
        assertEquals(0.52, profile.update(0.5, 7, 0.1), 1e-12);
        double speed = 0.0;
        for (int tick = 0; tick < 10; tick++) speed = profile.update(speed, 7, 0.1);
        assertEquals(16.0, speed * 80.0, 1e-9);
    }

    @Test
    void everyNotchUsesOriginalTargetAndCoastingAtTarget() {
        MasconMotionProfile profile = new MasconMotionProfile();
        for (int notch = 1; notch <= 7; notch++) {
            double target = MasconMotionProfile.MAX_SPEED / 7.0 * notch;
            assertEquals(target, profile.update(target - 0.001, notch, 0.1), 1e-12);
            assertEquals(target * 0.995, profile.update(target, notch, 0.1), 1e-12);
        }
    }

    @Test
    void neutralServiceBrakesAndEmergencyStopMatchOriginalBehavior() {
        MasconMotionProfile profile = new MasconMotionProfile();
        assertEquals(0.995, profile.update(1.0, 0, 0.1), 1e-12);
        for (int brake = 1; brake <= 7; brake++) {
            assertEquals(1.0 - 0.007 * brake, profile.update(1.0, -brake, 0.1), 1e-12);
        }
        assertEquals(0.0, profile.update(1.0, -8, 0.1));
        assertEquals(0.0, profile.update(0.001, -7, 0.1));
        assertEquals(0.995, profile.update(1.0, 1, 0.1), 1e-12);
    }

    @Test
    void neutralOrEmergencyStopDoesNotDelayNextPowerApplication() {
        MasconMotionProfile profile = new MasconMotionProfile();
        profile.update(0.5, 0, 0.1);
        assertEquals(0.52, profile.update(0.5, 7, 0.1), 1e-12);
        profile.update(0.5, -8, 0.1);
        assertEquals(0.02, profile.update(0.0, 7, 0.1), 1e-12);
    }
}
