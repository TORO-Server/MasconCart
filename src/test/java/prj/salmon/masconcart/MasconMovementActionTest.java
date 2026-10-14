package prj.salmon.masconcart;

import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MasconMovementActionTest {
    private ActionTracker tracker() {
        return new ActionTracker() {
            @Override
            public TrainCarts.Provider getOwner() { return null; }

            @Override
            public MinecartGroup getGroupOwner() { return null; }
        };
    }

    @Test
    void trainCartsRecognizesMasconAsMovementControlUntilReleased() {
        ActionTracker tracker = tracker();
        MasconMovementAction mascon = tracker.addAction(new MasconMovementAction());
        assertTrue(tracker.isMovementControlled());
        // The action must remain active across ticks, including when the train is stationary.
        for (int tick = 0; tick < 100; tick++) assertFalse(mascon.update());
        mascon.release();
        assertFalse(tracker.isMovementControlled());
        assertTrue(mascon.update());
    }

    @Test
    void leavingMasconPreservesQueuedTrainCartsActions() {
        ActionTracker tracker = tracker();
        MasconMovementAction mascon = tracker.addAction(new MasconMovementAction());
        Action next = tracker.addAction(new MasconMovementAction());
        mascon.release();
        assertSame(mascon, tracker.removeAction());
        assertSame(next, tracker.getCurrentAction());
        assertTrue(tracker.isMovementControlled());
    }
}
