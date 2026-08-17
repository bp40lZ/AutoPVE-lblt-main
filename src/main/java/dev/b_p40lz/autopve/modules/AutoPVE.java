package dev.b_p40lz.autopve.modules;

import dev.b_p40lz.autopve.AddonTemplate;
import dev.b_p40lz.autopve.mixin.ServerboundMovePlayerPacketAccessor;
import dev.b_p40lz.autopve.utils.MSTimer;
import dev.b_p40lz.autopve.utils.TPUtil;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Vector3dSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AutoPVE extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoLogin = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-login")
        .description("Detects the welcome message and sends the login command.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("The password used for /login.")
        .defaultValue("")
        .visible(autoLogin::get)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Automatically reconnects to the last server when disconnected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> reconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reconnect-delay")
        .description("Delay in seconds before reconnecting.")
        .defaultValue(5)
        .range(1, 120)
        .sliderMax(60)
        .visible(autoReconnect::get)
        .build()
    );

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("The vertical offset above the target's head.")
        .defaultValue(5)
        .range(0, 20)
        .build()
    );

    private final Setting<Double> stepDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("step-distance")
        .description("Distance per TP segment.")
        .defaultValue(5)
        .range(0.5, 20)
        .build()
    );

    private final Setting<Integer> selectSlot = sgGeneral.add(new IntSetting.Builder()
        .name("select-slot")
        .description("The hotbar slot containing the nether star.")
        .defaultValue(3)
        .range(0, 8)
        .build()
    );

    private final Setting<Integer> selectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("select-delay")
        .description("Delay in seconds before right-clicking the nether star after login.")
        .defaultValue(1)
        .range(0, 10)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("Skips a target if it hasn't died after this many seconds.")
        .defaultValue(2)
        .range(1, 60)
        .build()
    );

    private final Setting<Boolean> noGround = sgGeneral.add(new BoolSetting.Builder()
        .name("no-ground")
        .description("Forces all movement packets to send onGround=false.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxTargets = sgGeneral.add(new IntSetting.Builder()
        .name("max-targets")
        .description("Maximum targets to attack at once.")
        .defaultValue(5)
        .range(1, 20)
        .build()
    );

    private final Setting<Vector3d> areaPos1 = sgGeneral.add(new Vector3dSetting.Builder()
        .name("area-pos-1")
        .description("First corner of the attack area.")
        .defaultValue(-8, 39, 57)
        .build()
    );

    private final Setting<Vector3d> areaPos2 = sgGeneral.add(new Vector3dSetting.Builder()
        .name("area-pos-2")
        .description("Second corner of the attack area.")
        .defaultValue(-165, 15, 0)
        .build()
    );

    private static final List<String> BLOCKED_NAMES = List.of("核心", "炮塔", "战斗兵", "治疗兵", "地刺", "弓兵", "煤球炮", "音波炮");
    private static final Random RANDOM = new Random();

    private final Set<UUID> skipped = new HashSet<>();
    private final Map<UUID, Long> attackTimes = new HashMap<>();
    private int targetIndex;
    private Pair<ServerAddress, ServerInfo> lastServer;
    private final MSTimer reconnectTimer = new MSTimer();
    private boolean reconnectPending;
    private boolean loggedIn;
    private boolean loginSelectPending;
    private boolean guiOpenPending;
    private final MSTimer selectTimer = new MSTimer();

    public AutoPVE() {
        super(AddonTemplate.CATEGORY, "auto-pve", "autopve.");
        runInMainMenu = true;
    }

    @Override
    public void onDeactivate() {
        skipped.clear();
        attackTimes.clear();
        targetIndex = 0;
        reconnectPending = false;
        loggedIn = false;
        loginSelectPending = false;
        guiOpenPending = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleReconnect();
        handleSelect();

        if (mc.player == null || mc.world == null) return;
        if (loginSelectPending || guiOpenPending) return;

        List<LivingEntity> targets = findTargets();
        if (targets.isEmpty()) return;

        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get((targetIndex + i) % targets.size());
            long now = System.currentTimeMillis();
            long start = attackTimes.computeIfAbsent(target.getUuid(), k -> now);
            if (now - start > timeout.get() * 1000L) {
                skipped.add(target.getUuid());
                attackTimes.remove(target.getUuid());
                continue;
            }

            Vec3d playerPos = mc.player.getEntityPos();
            Vec3d targetVec = target.getEntityPos().add(0, target.getHeight(), 0);
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = RANDOM.nextDouble() * 2.0;
            Vec3d attackPos = targetVec.add(Math.cos(angle) * radius, yOffset.get(), Math.sin(angle) * radius);

            targetIndex = (targetIndex + 1) % targets.size();
            TPUtil.doTpMove(playerPos, attackPos, stepDistance.get(), true, () -> doAttack(target));
            break;
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reconnectPending = false;
        loginSelectPending = false;
        guiOpenPending = false;
        skipped.clear();
        attackTimes.clear();
        targetIndex = 0;

        ServerInfo server = mc.getCurrentServerEntry();
        if (server != null) {
            lastServer = Pair.of(ServerAddress.parse(server.address), server);
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        loggedIn = false;
        if (autoReconnect.get() && lastServer != null) {
            reconnectPending = true;
            reconnectTimer.reset();
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!autoLogin.get() || loggedIn) return;
        if (mc.player == null) return;
        if (password.get().isEmpty()) return;

        if (event.getMessage().getString().contains("欢迎玩家")) {
            loggedIn = true;
            ChatUtils.sendPlayerMsg("/login " + password.get());
            loginSelectPending = true;
            selectTimer.reset();
        }
    }

    @EventHandler
    private void onSentPacket(PacketEvent.Send event) {
        if (!noGround.get()) return;
        if (mc.player == null) return;
        if (event.packet instanceof PlayerMoveC2SPacket packet
            && packet instanceof ServerboundMovePlayerPacketAccessor accessor) {
            accessor.setOnGround(false);
        }
    }

    private void handleSelect() {
        if (mc.player == null) return;

        if (loginSelectPending) {
            if (selectTimer.hasPassTime(selectDelay.get() * 1000L)) {
                mc.player.getInventory().setSelectedSlot(selectSlot.get());
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                loginSelectPending = false;
                guiOpenPending = true;
                selectTimer.reset();
            }
            return;
        }

        if (guiOpenPending) {
            Screen screen = mc.currentScreen;
            if (screen == null) {
                guiOpenPending = false;
                return;
            }

            if (!(screen instanceof HandledScreen<?> containerScreen)) {
                if (selectTimer.hasPassTime(2000)) {
                    mc.player.getInventory().setSelectedSlot(selectSlot.get());
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    selectTimer.reset();
                }
                return;
            }

            if (selectTimer.hasPassTime(1000)) {
                ScreenHandler menu = containerScreen.getScreenHandler();
                for (Slot slot : menu.slots) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && stack.getName().getString().toLowerCase().contains("pve")) {
                        mc.interactionManager.clickSlot(menu.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
                        selectTimer.reset();
                        break;
                    }
                }
            }
        }
    }

    private void handleReconnect() {
        if (mc.player == null) {
            if (autoReconnect.get() && reconnectPending && lastServer != null
                && reconnectTimer.hasPassTime(reconnectDelay.get() * 1000L)) {
                reconnectPending = false;
                ConnectScreen.connect(new TitleScreen(), mc, lastServer.getLeft(), lastServer.getRight(), false, null);
            }
        } else {
            reconnectPending = false;
        }
    }

    private List<LivingEntity> findTargets() {
        if (skipped.size() > 50) skipped.clear();

        List<LivingEntity> targets = new ArrayList<>();
        List<Entity> visibleEntities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) visibleEntities.add(entity);

        for (Entity entity : visibleEntities) {
            if (entity instanceof ItemEntity) continue;
            if (!(entity instanceof LivingEntity le)) continue;
            if (le instanceof PlayerEntity) continue;
            if (le instanceof ArmorStandEntity) continue;
            if (le.isRemoved() || le.getHealth() <= 0) continue;
            if (skipped.contains(le.getUuid())) continue;
            if (!isInArea(le)) continue;
            if (hasBlockedName(le, visibleEntities)) continue;
            targets.add(le);
        }

        targets.sort(Comparator.comparingDouble(e -> mc.player.squaredDistanceTo(e)));
        int max = maxTargets.get();
        if (targets.size() > max) targets = new ArrayList<>(targets.subList(0, max));
        return targets;
    }

    private boolean isInArea(LivingEntity entity) {
        Vector3d pos1 = areaPos1.get();
        Vector3d pos2 = areaPos2.get();
        double minX = Math.min(pos1.x, pos2.x), maxX = Math.max(pos1.x, pos2.x);
        double minY = Math.min(pos1.y, pos2.y), maxY = Math.max(pos1.y, pos2.y);
        double minZ = Math.min(pos1.z, pos2.z), maxZ = Math.max(pos1.z, pos2.z);

        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private boolean hasBlockedName(LivingEntity entity, Iterable<Entity> nearbyEntities) {
        if (isBlockedName(getEntityName(entity))) return true;

        // Many servers render a mob's title as a separate entity above the actual mob.
        for (Entity nearby : nearbyEntities) {
            if (nearby == entity || nearby.isRemoved()) continue;
            double dx = nearby.getX() - entity.getX();
            double dz = nearby.getZ() - entity.getZ();
            if (dx * dx + dz * dz > 16.0) continue;
            if (nearby.getY() < entity.getY() - 3.0 || nearby.getY() > entity.getY() + 8.0) continue;
            if (isBlockedName(getEntityName(nearby))) return true;
        }

        return false;
    }

    private boolean isBlockedName(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("bot")) return true;
        for (String blocked : BLOCKED_NAMES) {
            if (name.contains(blocked)) return true;
        }
        return false;
    }

    private String getEntityName(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append(entity.getName().getString());
        sb.append('|').append(entity.getNameForScoreboard());
        if (entity.hasCustomName()) {
            sb.append('|').append(entity.getCustomName().getString());
        }
        sb.append('|').append(entity.getDisplayName().getString());
        return sb.toString().replace("\u00a7", "");
    }

    private void doAttack(LivingEntity target) {
        if (mc.world == null || target.isRemoved() || hasBlockedName(target, mc.world.getEntities())) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
