package prj.salmon.masconcart;

import com.bergerkiller.bukkit.tc.actions.GroupAction;
import com.bergerkiller.bukkit.tc.actions.MovementAction;

/** Keeps TrainCarts' rail boosts and brakes from overriding the mascon velocity. */
final class MasconMovementAction extends GroupAction implements MovementAction {
    private boolean active = true;

    @Override
    public boolean isMovementSuppressed() {
        return active;
    }

    @Override
    public boolean update() {
        return !active;
    }

    void release() {
        active = false;
    }
}
