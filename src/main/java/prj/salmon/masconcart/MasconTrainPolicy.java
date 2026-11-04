package prj.salmon.masconcart;

import java.util.Collection;

/** Decides which carts and trains mascon may touch. Free of Bukkit so that it can be unit tested. */
final class MasconTrainPolicy {
    /** Scoreboard tag put on a vanilla minecart when this plugin converts it for mascon. */
    static final String CONVERTED_TAG = "masconcart_converted";
    /** Scoreboard tag Click-to-Minecart puts on the minecart it spawns for one rider. */
    static final String CLICK_TO_MINECART_TAG = "cminecart";
    /** TrainCarts tag that marks a train as drivable when the tag is required. */
    static final String DEFAULT_TAKEOVER_TAG = "mascon";

    private final boolean requireTag;
    private final String takeoverTag;

    MasconTrainPolicy(boolean requireTag, String takeoverTag) {
        this.requireTag = requireTag;
        this.takeoverTag = takeoverTag;
    }

    /**
     * Only these carts go back to vanilla when mascon is off. TrainCarts converts every spawned minecart when
     * allMinecartsAreTrainCarts is on, so Click-to-Minecart carts are TrainCarts members before anyone boards them.
     * Everything else is a TrainCarts train, which keeps its group and properties.
     */
    static boolean isPlayerCart(Collection<String> scoreboardTags) {
        return scoreboardTags.contains(CONVERTED_TAG) || scoreboardTags.contains(CLICK_TO_MINECART_TAG);
    }

    /** Whether mascon may start on a train, given its TrainCarts tags and whether it is made of player carts only. */
    boolean allowsTakeover(boolean onlyPlayerCarts, Collection<String> trainTags) {
        return !requireTag || onlyPlayerCarts || trainTags.contains(takeoverTag);
    }
}
