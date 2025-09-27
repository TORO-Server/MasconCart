package prj.salmon.masconcart;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import com.bergerkiller.bukkit.tc.properties.standard.type.SignSkipOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MasconCart extends JavaPlugin implements Listener {

    private static final long TOGGLE_COOLDOWN_MS = 1000;
    private static final long TICK_INTERVAL = 2L;
    private static final double FRICTION_FACTOR = 0.995;
    private static final double B_IMPULSE = 0.007;
    private static final double MAX_SPEED = 1.5;
    private static final double STOP_THRESHOLD_SPEED = 0.05;

    private ProtocolManager protocolManager;
    private final Map<UUID, Boolean> masconMode = new HashMap<>();
    private final Map<UUID, Integer> notchLevel = new HashMap<>();
    private final Map<UUID, Double> currentSpeed = new HashMap<>();
    private final Map<UUID, Long> lastToggle = new HashMap<>();
    private final Map<UUID, Boolean> lastASide = new HashMap<>();
    private final Map<UUID, Boolean> lastForward = new HashMap<>();
    private final Map<UUID, Boolean> lastBackward = new HashMap<>();
    private final Map<UUID, Boolean> lastDSide = new HashMap<>();
    private final Map<UUID, Vector> lastDirectionByPlayer = new HashMap<>();
    private final Map<String, Vector> lastDirectionByTrain = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();

    private BukkitTask updateTask;

    @Override
    public void onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                handleSteerVehicle(event);
            }
        });

        Bukkit.getPluginManager().registerEvents(this, this);
        updateTask = Bukkit.getScheduler().runTaskTimer(this, this::tickUpdate, 0L, TICK_INTERVAL);
        getLogger().info("MasconCart enabled (prj.salmon.masconcart)");
    }

    @Override
    public void onDisable() {
        if (updateTask != null) updateTask.cancel();
        getLogger().info("MasconCart disabled");
    }

    private void handleSteerVehicle(PacketEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (!p.isInsideVehicle()) {
            lastASide.remove(id);
            lastForward.remove(id);
            lastBackward.remove(id);
            lastDSide.remove(id);
            return;
        }

        PacketContainer packet = event.getPacket();
        float sideways = 0f;
        float forward = 0f;
        try {
            sideways = packet.getFloat().read(0);
            forward = packet.getFloat().read(1);
        } catch (Exception ignored) {}

        boolean aPressed = sideways < -0.5f;
        boolean dPressed = sideways > 0.5f;
        boolean forwardPressed = forward > 0.5f;
        boolean backwardPressed = forward < -0.5f;

        boolean prevA = lastASide.getOrDefault(id, false);
        boolean prevD = lastDSide.getOrDefault(id, false);
        boolean prevF = lastForward.getOrDefault(id, false);
        boolean prevB = lastBackward.getOrDefault(id, false);

        if (aPressed && !prevA) {
            long now = System.currentTimeMillis();
            long last = lastToggle.getOrDefault(id, 0L);
            if (now - last > TOGGLE_COOLDOWN_MS) {
                boolean newMode = !masconMode.getOrDefault(id, false);
                masconMode.put(id, newMode);
                lastToggle.put(id, now);
                p.sendMessage("§a[Mascon] モード: " + (newMode ? "ON TC制御無効" : "OFF TC制御有効"));
                if (newMode) {
                    notchLevel.put(id, 0);
                    currentSpeed.put(id, 0.0);
                    Vehicle vehicle = (Vehicle) p.getVehicle();
                    MinecartGroup group = MinecartGroup.get(vehicle);
                    if (group != null) {
                        group.getProperties().setSpeedLimit(Double.MAX_VALUE);
                    }
                } else {
                    notchLevel.remove(id);
                    currentSpeed.remove(id);
                    lastDirectionByPlayer.remove(id);
                    Vehicle vehicle = (Vehicle) p.getVehicle();
                    MinecartGroup group = MinecartGroup.get(vehicle);
                    if (group != null) {
                        group.getProperties().setSpeedLimit(0.4);
                        group.getProperties().setSkipOptions(SignSkipOptions.NONE);
                    }
                }
            }
        }
        if (masconMode.getOrDefault(id, false)) {
            int level = notchLevel.getOrDefault(id, 0);

            if (dPressed && !prevD) {
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
            if (dPressed || forwardPressed || backwardPressed) {
                event.setCancelled(true);
            }
        }

        lastASide.put(id, aPressed);
        lastDSide.put(id, dPressed);
        lastForward.put(id, forwardPressed);
        lastBackward.put(id, backwardPressed);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();

        long now = System.currentTimeMillis();
        long cooldown = 500;
        if (lastClickTime.containsKey(id) && now - lastClickTime.get(id) < cooldown) {
            return;
        }
        lastClickTime.put(id, now);
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
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType().isInteractable()) {
            return;
        }

        Vehicle vehicle = (Vehicle) p.getVehicle();
        if (vehicle == null) return;

        MinecartGroup group = MinecartGroup.get(vehicle);
        if (group == null || group.isEmpty()) return;

        MinecartMember<?> leader = group.get(0);
        org.bukkit.entity.Entity ent = leader.getEntity().getEntity();
        if (!(ent instanceof Minecart mc)) return;

        double currentHorizontalSpeed = Math.sqrt(mc.getVelocity().getX() * mc.getVelocity().getX() + mc.getVelocity().getZ() * mc.getVelocity().getZ());

        if (currentHorizontalSpeed < STOP_THRESHOLD_SPEED) {
            for (MinecartMember<?> member : group) {
                try {
                    org.bukkit.entity.Entity memberEnt = member.getEntity().getEntity();
                    if (!(memberEnt instanceof Minecart memberMc)) continue;

                    Vector vel = memberMc.getVelocity();
                    memberMc.setVelocity(new Vector(-vel.getX(), vel.getY(), -vel.getZ()));
                } catch (Throwable t) {
                    getLogger().warning(t.getMessage());
                }
            }

            currentSpeed.put(id, currentHorizontalSpeed);

            Vector lastDir = lastDirectionByPlayer.getOrDefault(id, new Vector(1, 0, 0));
            lastDirectionByPlayer.put(id, lastDir.multiply(-1));

            p.sendMessage("§b[Mascon] 進行方向を切り替えました");
            event.setCancelled(true);
        } else {
            p.sendMessage("§c[Mascon] 走行中は進行方向を切り替えられません");
            event.setCancelled(true);
        }
    }

    private String levelToLabel(int level) {
        if (level == 0) return "N";
        if (level == -8) return "EB";
        if (level > 0) return "P" + level;
        return "B" + Math.abs(level);
    }

    private void tickUpdate() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();

            if (!p.isInsideVehicle()) {
                masconMode.remove(id);
                notchLevel.remove(id);
                currentSpeed.remove(id);
                continue;
            }

            Vehicle vehicle = (Vehicle) p.getVehicle();
            if (vehicle == null) continue;
            MinecartGroup group = MinecartGroup.get(vehicle);
            if (group == null || group.isEmpty()) continue;

            boolean masconOn = masconMode.getOrDefault(id, false);

            if (masconOn) {
                group.getProperties().setSkipOptions(SignSkipOptions.create(0, 9999, ""));
                group.getProperties().setSpeedLimit(Double.MAX_VALUE);
            } else {
                group.getProperties().setSkipOptions(SignSkipOptions.NONE);
                if (group.getProperties().getSpeedLimit() == Double.MAX_VALUE) {
                    group.getProperties().setSpeedLimit(0.4);
                }
                group.getProperties().setManualMovementAllowed(true);
            }

            if (!masconOn) {
                continue;
            }

            int level = notchLevel.getOrDefault(id, 0);
            double current = currentSpeed.getOrDefault(id, 0.0);
            double targetSpeed;

            if (level > 0) {
                targetSpeed = (MAX_SPEED / 7.0) * level;
                double accel = 0.02;
                if (current < targetSpeed) current = Math.min(current + accel, targetSpeed);
                else current *= FRICTION_FACTOR;
            } else if (level < 0) {
                if (level == -8) current = 0.0;
                else current -= B_IMPULSE * Math.abs(level);
            } else {
                current *= FRICTION_FACTOR;
            }

            current = Math.max(0.0, Math.min(current, MAX_SPEED));
            currentSpeed.put(id, current);

            Vector direction = lastDirectionByPlayer.getOrDefault(id, new Vector(1, 0, 0));
            applySpeedToGroup(group, current, direction);

            MinecartMember<?> leader = group.get(0);
            org.bukkit.entity.Entity ent = leader.getEntity().getEntity();
            if (ent instanceof Minecart mc) {
                Vector vel = mc.getVelocity().clone().setY(0);
                if (vel.lengthSquared() > 1e-6) lastDirectionByPlayer.put(id, vel.normalize());
            }

            double speedKmH = current * 80;
            String actionBarMsg = "ノッチ: " + levelToLabel(level) + " 速度: " + String.format("%.1f", speedKmH) + " km/h";
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMsg));
        }
    }

    private void applySpeedToGroup(MinecartGroup group, double speed, Vector playerDirection) {
        MinecartMember<?> leader = group.get(0);
        org.bukkit.entity.Entity leaderEnt = leader.getEntity().getEntity();
        if (!(leaderEnt instanceof Minecart mcLeader)) return;

        Vector forward = mcLeader.getVelocity().clone();
        forward.setY(0);
        if (forward.lengthSquared() < 1e-6) {
            forward = playerDirection.clone().setY(0);
        }

        forward.normalize().multiply(speed);

        for (MinecartMember<?> member : group) {
            try {
                org.bukkit.entity.Entity ent = member.getEntity().getEntity();
                if (!(ent instanceof Minecart mc)) continue;

                Vector vel = forward.clone();
                vel.setY(mc.getVelocity().getY());
                mc.setVelocity(vel);

            } catch (Throwable t) {
                getLogger().warning(t.getMessage());
            }
        }

        lastDirectionByTrain.put(group.getProperties().getTrainName(), forward.clone().normalize());
    }
}