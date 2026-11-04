package prj.salmon.masconcart;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MasconTrainPolicyTest {
    @Test
    void onlyConvertedAndClickToMinecartCartsGoBackToVanilla() {
        assertTrue(MasconTrainPolicy.isPlayerCart(Set.of(MasconTrainPolicy.CONVERTED_TAG)));
        assertTrue(MasconTrainPolicy.isPlayerCart(Set.of(MasconTrainPolicy.CLICK_TO_MINECART_TAG, "other")));
        assertFalse(MasconTrainPolicy.isPlayerCart(Set.of()));
        assertFalse(MasconTrainPolicy.isPlayerCart(Set.of("other", MasconTrainPolicy.DEFAULT_TAKEOVER_TAG)));
    }

    @Test
    void anyTrainCanBeTakenOverWhenTheTagIsNotRequired() {
        MasconTrainPolicy policy = new MasconTrainPolicy(false, MasconTrainPolicy.DEFAULT_TAKEOVER_TAG);
        assertTrue(policy.allowsTakeover(false, List.of()));
        assertTrue(policy.allowsTakeover(true, List.of()));
    }

    @Test
    void onlyTaggedTrainsAndPlayerCartsCanBeTakenOverWhenTheTagIsRequired() {
        MasconTrainPolicy policy = new MasconTrainPolicy(true, "driver_train");
        assertTrue(policy.allowsTakeover(false, List.of("line1", "driver_train")));
        assertFalse(policy.allowsTakeover(false, List.of("line1")));
        assertFalse(policy.allowsTakeover(false, List.of()));
        // Tags are compared as they are, without TrainCarts' wildcard matching.
        assertFalse(policy.allowsTakeover(false, List.of("driver_train2")));
        assertTrue(policy.allowsTakeover(true, List.of()));
    }
}
