package prj.salmon.masconcart;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.properties.CartPropertiesStore;
import com.bergerkiller.bukkit.common.controller.DefaultEntityController;
import com.bergerkiller.bukkit.common.controller.DefaultEntityNetworkController;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.ServerboundPlayerInputPacketHandle;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MasconCart extends JavaPlugin implements Listener {

    private static final long TOGGLE_COOLDOWN_MS = 1000;
    private static final long TICK_INTERVAL = 2L;
    private static final double MAX_SPEED = MasconMotionProfile.MAX_SPEED;
    private static final double STOP_THRESHOLD_SPEED = 0.0025;


    private final Map<UUID, Boolean> masconDisabled = new HashMap<>();

    private final Map<UUID, Boolean> masconMode = new HashMap<>();
    private final Map<UUID, Integer> notchLevel = new HashMap<>();
    private final Map<UUID, Double> currentSpeed = new HashMap<>();
    private final Map<UUID, MasconMotionProfile> motionProfiles = new HashMap<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();
    private final Map<UUID, Boolean> lastTogglePressed = new HashMap<>();
    private final Map<UUID, Boolean> lastForward = new HashMap<>();
    private final Map<UUID, Boolean> lastBackward = new HashMap<>();
    private final Map<UUID, Boolean> lastNeutralPressed = new HashMap<>();
    private final Map<UUID, Boolean> lastJumpPressed = new HashMap<>();
    private final Map<UUID, ControlledTrain> controlledTrains = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();

    private BukkitTask updateTask;

    @Override
    public void onEnable() {

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(PacketAdapter.params()
                .plugin(this).types(PacketType.Play.Client.STEER_VEHICLE).optionSync()) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleSteerVehicle(event);
            }
        });

        Bukkit.getPluginManager().registerEvents(this, this);
        updateTask = Bukkit.getScheduler().runTaskTimer(this, this::tickUpdate, 0L, TICK_INTERVAL);
        getLogger().info("MasconCart enabled (prj.salmon.masconcart)");

        saveDefaultConfig();
        for (String uuidStr : getConfig().getStringList("disabledPlayers")) {
            try {
                masconDisabled.put(UUID.fromString(uuidStr), true);
            } catch (IllegalArgumentException ignored) {}
        }

        Objects.requireNonNull(getCommand("mascon")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("プレイヤーのみ使用できます");
                return true;
            }
            UUID id = p.getUniqueId();
            if (args.length == 0) {
                p.sendMessage("§e/mascon on §fまたは §e/mascon off");
                return true;
            }
            if (args[0].equalsIgnoreCase("off")) {
                stopMascon(id);
                masconDisabled.put(id, true);
                updateConfigDisabledList();
                p.sendMessage("§c[Mascon] このプレイヤーではMascon機能を無効化しました");
            } else if (args[0].equalsIgnoreCase("on")) {
                masconDisabled.remove(id);
                updateConfigDisabledList();
                p.sendMessage("§a[Mascon] Mascon機能を有効化しました");
            } else {
                p.sendMessage("§e使用方法: /mascon on | off");
            }
            return true;
        });

    }

    private void updateConfigDisabledList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (UUID uuid : masconDisabled.keySet()) {
            list.add(uuid.toString());
        }
        getConfig().set("disabledPlayers", list);
        saveConfig();
    }

    @Override
    public void onDisable() {

        if (updateTask != null) updateTask.cancel();
        for (UUID id : java.util.List.copyOf(controlledTrains.keySet())) stopMascon(id);
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
        getLogger().info("MasconCart disabled");
    }

    private void handleSteerVehicle(PacketEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (!p.isInsideVehicle()) {
            lastTogglePressed.remove(id);
            lastForward.remove(id);
            lastBackward.remove(id);
            lastNeutralPressed.remove(id);
            lastJumpPressed.remove(id);
            return;
        }
        if (!(p.getVehicle() instanceof Minecart)) return;
        MinecartGroup group = getRiddenGroup(p, false);

        // BKCommonLib handles both legacy float input and the 1.21.2+ Input record.
        ServerboundPlayerInputPacketHandle input =
                ServerboundPlayerInputPacketHandle.createHandle(event.getPacket().getHandle());
        boolean togglePressed = input.isRight(); // D toggles mascon mode
        boolean neutralPressed = input.isLeft();  // A selects neutral
        boolean forwardPressed = input.isForward();
        boolean backwardPressed = input.isBackward();
        boolean jumpPressed = input.isJump();

        boolean prevToggle = lastTogglePressed.getOrDefault(id, false);
        boolean prevNeutral = lastNeutralPressed.getOrDefault(id, false);
        boolean prevF = lastForward.getOrDefault(id, false);
        boolean prevB = lastBackward.getOrDefault(id, false);
        boolean prevJump = lastJumpPressed.getOrDefault(id, false);

        if (!masconDisabled.getOrDefault(id, false) && togglePressed && !prevToggle) {
            long now = System.currentTimeMillis();
            long last = lastToggle.getOrDefault(id, 0L);
            if (now - last > TOGGLE_COOLDOWN_MS) {
                boolean newMode = !masconMode.getOrDefault(id, false);
                if (newMode) group = getRiddenGroup(p, true);
                if (newMode && (group == null || group.isEmpty())) {
                    p.sendMessage("§c[Mascon] このトロッコではマスコンを開始できません");
                    lastTogglePressed.put(id, true);
                    return;
                }
                if (newMode && isControlled(group)) {
                    p.sendMessage("§c[Mascon] この列車は他のプレイヤーが操作中です");
                    lastTogglePressed.put(id, true);
                    return;
                }
                masconMode.put(id, newMode);
                lastToggle.put(id, now);
                p.sendMessage("§a[Mascon] モード: " + (newMode ? "ON TC制御無効" : "OFF TC制御有効"));
                if (newMode) {
                    notchLevel.put(id, 0);

                    MasconMovementAction movementAction = new MasconMovementAction();
                    controlledTrains.put(id, new ControlledTrain(group,
                            group.getProperties().getSpeedLimit(), group.getProperties().getSkipOptions(),
                            movementAction));
                    currentSpeed.put(id, group.head().getForce());
                    motionProfiles.put(id, new MasconMotionProfile());
                    group.getActions().clear();
                    group.getActions().addAction(movementAction);
                    group.getProperties().setSpeedLimit(MAX_SPEED);
                    group.getProperties().setManualMovementAllowed(false);
                } else {
                    stopMascon(id);
                    group = null;
                }
            }
        }
        if (masconMode.getOrDefault(id, false) && group != null && jumpPressed && !prevJump) {
            reverseIfStopped(p, group);
        }
        if (!masconMode.getOrDefault(id, false)) {
            // No packet cancellation or synthetic movement while OFF.
            if (group != null && !isControlled(group)) restoreVanilla(group);
        }
        if (masconMode.getOrDefault(id, false)) {
            int level = notchLevel.getOrDefault(id, 0);

            if (neutralPressed && !prevNeutral) {
                level = 0;
                notchLevel.put(id, 0);
            }

            if (forwardPressed && !prevF) {
                if (level < 7) {
                    level++;
                    notchLevel.put(id, level);
                }
            } else if (backwardPressed && !prevB) {
                if (level > -8) {
                    level--;
                    notchLevel.put(id, level);
                }
            }
            if (neutralPressed || forwardPressed || backwardPressed || jumpPressed) {
                event.setCancelled(true);
            }
        }

        lastTogglePressed.put(id, togglePressed);
        lastNeutralPressed.put(id, neutralPressed);
        lastForward.put(id, forwardPressed);
        lastBackward.put(id, backwardPressed);
        lastJumpPressed.put(id, jumpPressed);
    }

    private boolean isControlled(MinecartGroup group) {
        return controlledTrains.values().stream().anyMatch(train -> train.group() == group);
    }

    private MinecartGroup getRiddenGroup(Player player, boolean convert) {
        if (!(player.getVehicle() instanceof Minecart minecart)) return null;
        MinecartMember<?> member = MinecartMemberStore.getFromEntity(minecart);
        // Conversion is requested only when enabling mascon, never by OFF-mode polling.
        if (member == null && convert) {
            member = MinecartMemberStore.convert(TrainCarts.plugin, minecart);
        }
        return member == null || member.isUnloaded() ? null : member.getGroup();
    }

    private void restoreVanilla(MinecartGroup group) {
        // Snapshot because clearGroup removes members from the group during iteration.
        for (MinecartMember<?> member : java.util.List.copyOf(group)) {
            var entity = member.getEntity();
            var minecart = entity.getEntity();
            // Detach TrainCarts models/network tracking before removing group membership.
            entity.setNetworkController(new DefaultEntityNetworkController());
            member.clearGroup();
            member.setUnloaded(true);
            CartPropertiesStore.remove(minecart.getUniqueId());
            entity.setController(new DefaultEntityController());
            // TrainCarts changes this during physics; restore Bukkit's vanilla default.
            minecart.setMaxSpeed(0.4);
        }
    }

    private void reverseIfStopped(Player player, MinecartGroup group) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - lastClickTime.getOrDefault(id, 0L) < 500L) return;
        lastClickTime.put(id, now);
        if (controlledTrains.entrySet().stream().anyMatch(entry ->
                !entry.getKey().equals(id) && entry.getValue().group() == group)) {
            player.sendMessage("§c[Mascon] この列車は他のプレイヤーが操作中です");
            return;
        }
        boolean moving = group.stream().anyMatch(member -> member.getForce() >= STOP_THRESHOLD_SPEED);
        if (moving || currentSpeed.getOrDefault(id, 0.0) >= STOP_THRESHOLD_SPEED) {
            player.sendMessage("§c[Mascon] 走行中は進行方向を切り替えられません");
            return;
        }
        group.stop();
        group.reverse();
        if (masconMode.getOrDefault(id, false)) {
            currentSpeed.put(id, 0.0);
            motionProfiles.put(id, new MasconMotionProfile());
        }
        player.sendMessage("§b[Mascon] 進行方向を切り替えました");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();

        if (!p.isInsideVehicle() ||
                !masconMode.getOrDefault(id, false) ||
                !p.getInventory().getItemInMainHand().getType().isAir())
        {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && Objects.requireNonNull(event.getClickedBlock()).getType().isInteractable()) {
            return;
        }

        MinecartGroup group = getRiddenGroup(p, false);
        if (group == null || group.isEmpty()) return;
        reverseIfStopped(p, group);
        event.setCancelled(true);
    }

    private String levelToLabel(int level) {
        if (level == 0) return "N";
        if (level == -8) return "EB";
        if (level > 0) return "P" + level;
        return "B" + Math.abs(level);
    }

    private record ControlledTrain(MinecartGroup group, double speedLimit, SignSkipOptions skipOptions,
                                   MasconMovementAction movementAction) {}

    private void stopMascon(UUID id) {
        ControlledTrain train = controlledTrains.remove(id);
        if (train != null) train.movementAction().release();
        if (train != null && !train.group().isRemoved()) {
            if (train.group().getActions().getCurrentAction() == train.movementAction()) {
                train.group().getActions().removeAction();
            }
            train.group().getProperties().setSpeedLimit(train.speedLimit());
            train.group().getProperties().setSkipOptions(train.skipOptions());
            restoreVanilla(train.group());
        }
        masconMode.remove(id);
        notchLevel.remove(id);
        currentSpeed.remove(id);
        motionProfiles.remove(id);
        lastTogglePressed.remove(id);
        lastNeutralPressed.remove(id);
        lastForward.remove(id);
        lastBackward.remove(id);
        lastJumpPressed.remove(id);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopMascon(id);
        lastToggle.remove(id);
        lastClickTime.remove(id);
    }

    private void tickUpdate() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();

            if (!p.isInsideVehicle()) {
                stopMascon(id);
                continue;
            }

            MinecartGroup group = getRiddenGroup(p, false);
            ControlledTrain controlled = controlledTrains.get(id);
            if (controlled != null && controlled.group() != group) stopMascon(id);
            if (group == null || group.isEmpty()) continue;

            boolean masconOn = masconMode.getOrDefault(id, false);

            if (masconOn) {
                // A movement-controlling action makes powered rails skip both boosting and braking.
                if (controlled != null && group.getActions().getCurrentAction() != controlled.movementAction()) {
                    group.getActions().clear();
                    group.getActions().addAction(controlled.movementAction());
                }
                group.getProperties().setSkipOptions(SignSkipOptions.create(0, 9999, ""));
                group.getProperties().setSpeedLimit(MAX_SPEED);
            }

            if (!masconOn) {
                if (!isControlled(group)) restoreVanilla(group);
                continue;
            }

            int level = notchLevel.getOrDefault(id, 0);
            double current = currentSpeed.getOrDefault(id, 0.0);
            MasconMotionProfile profile = motionProfiles.computeIfAbsent(id, key -> new MasconMotionProfile());
            current = profile.update(current, level, TICK_INTERVAL / 20.0);
            currentSpeed.put(id, current);

            applySpeedToGroup(group, current);

            double speedKmH = current * MasconMotionProfile.DISPLAY_KMH_PER_SPEED;
            String actionBarMsg = "ノッチ: " + levelToLabel(level) + " 速度: " + String.format("%.1f", speedKmH) + " km/h";
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMsg));
        }
    }

    private void applySpeedToGroup(MinecartGroup group, double speed) {
        // TrainCarts applies force along each cart's rail direction, including at rest.
        group.setForwardForce(speed);
    }
}
