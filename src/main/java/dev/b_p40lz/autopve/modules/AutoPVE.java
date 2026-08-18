package dev.b_p40lz.autopve.modules;

import dev.b_p40lz.autopve.AddonTemplate;
import dev.b_p40lz.autopve.mixin.HandledScreenAccessor;
import dev.b_p40lz.autopve.mixin.ServerboundMovePlayerPacketAccessor;
import dev.b_p40lz.autopve.utils.MSTimer;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;//
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.settings.Vector3dSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

//b_p40lz是作者 我操你妈的。别拿去倒卖了。//

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

    private final Setting<Double> flightSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("flight-speed")
        .description("Velocity used while flying between route points.")
        .defaultValue(15)
        .range(0.05, 30.0)
        .sliderRange(0.05, 30.0)
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

    private final Setting<Integer> maxTarget = sgGeneral.add(new IntSetting.Builder()
        .name("max-target")
        .description("Maximum number of mobs attacked at once.")
        .defaultValue(10)
        .range(1, 100)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> noGround = sgGeneral.add(new BoolSetting.Builder()
        .name("no-ground")
        .description("Forces all movement packets to send onGround=false.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepDiamond = sgGeneral.add(new BoolSetting.Builder()
        .name("diamond")
        .description("Keep diamond items. Disable to automatically discard them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepGolden = sgGeneral.add(new BoolSetting.Builder()
        .name("golden")
        .description("Keep gold items. Disable to automatically discard them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> keepCopper = sgGeneral.add(new BoolSetting.Builder()
        .name("copper")
        .description("Keep copper items. Disable to automatically discard them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Vector3d> routeStart = sgGeneral.add(new Vector3dSetting.Builder()
        .name("pos-1")
        .description("pos1.")
        .defaultValue(-158, 15.1, 68)
        .build()
    );

    private final Setting<Vector3d> routeEnd = sgGeneral.add(new Vector3dSetting.Builder()
        .name("pos-2")
        .description("pos2.")
        .defaultValue(-158, 15.1, 0)
        .build()
    );

    private static final Vec3d INIT_WAYPOINT_1 = new Vec3d(20, 22, 27);
    private static final Vec3d INIT_WAYPOINT_2 = new Vec3d(20, 38, 27);
    private static final double ROUTE_ARRIVAL_DISTANCE_SQR = 2.25;
    private static final double ATTACK_RANGE_SQR = 25.0;
    private static final int PVE_MENU_SLOT = 15;
    private static final long PVE_HOVER_DELAY_MS = 150;

    private Pair<ServerAddress, ServerInfo> lastServer;
    private final MSTimer reconnectTimer = new MSTimer();
    private boolean reconnectPending;
    private boolean loggedIn;
    private boolean loginSelectPending;
    private boolean guiOpenPending;
    private int hoveredPveSlotId = -1;
    private boolean pveClickSent;
    private boolean headingToRouteEnd;
    private RoutePhase routePhase = RoutePhase.INIT_WAYPOINT_1;
    private final MSTimer selectTimer = new MSTimer();

    private enum RoutePhase {
        INIT_WAYPOINT_1,
        INIT_WAYPOINT_2,
        INIT_WAYPOINT_3,
        ENTER_LOOP,
        LOOP
    }

    public AutoPVE() {
        super(AddonTemplate.CATEGORY, "Auto-PVE", "autopve");
        runInMainMenu = true;
    }

    @Override
    public void onDeactivate() {
        reconnectPending = false;
        loggedIn = false;
        loginSelectPending = false;
        guiOpenPending = false;
        hoveredPveSlotId = -1;
        pveClickSent = false;
        resetRoute();
        stopRouteMovement();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleReconnect();
        handleSelect();

        if (mc.player == null || mc.world == null) return;
        if (loginSelectPending || guiOpenPending) return;

        flyRoute();
        attackNearbyEntities();
        discardDisabledMaterials();
    }

    private void flyRoute() {
        initializeRoutePhase();

        if (routePhase != RoutePhase.LOOP) {
            flyInitialRoute();
            return;
        }

        Vec3d target = headingToRouteEnd ? getRoutePoint(routeEnd) : getRoutePoint(routeStart);
        if (mc.player.squaredDistanceTo(target) <= ROUTE_ARRIVAL_DISTANCE_SQR) {
            headingToRouteEnd = !headingToRouteEnd;
            stopRouteMovement();
            return;
        }

        flyTowards(target);
    }

    private void initializeRoutePhase() {
        if (routePhase != RoutePhase.INIT_WAYPOINT_1) return;

        if (mc.player.squaredDistanceTo(getRoutePoint(routeStart)) <= ROUTE_ARRIVAL_DISTANCE_SQR) {
            routePhase = RoutePhase.LOOP;
            headingToRouteEnd = true;
        } else if (mc.player.squaredDistanceTo(getRoutePoint(routeEnd)) <= ROUTE_ARRIVAL_DISTANCE_SQR) {
            routePhase = RoutePhase.LOOP;
            headingToRouteEnd = false;
        }
    }

    private void flyInitialRoute() {
        Vec3d target = switch (routePhase) {
            case INIT_WAYPOINT_1 -> INIT_WAYPOINT_1;
            case INIT_WAYPOINT_2 -> INIT_WAYPOINT_2;
            case INIT_WAYPOINT_3 -> getInitWaypoint3();
            case ENTER_LOOP -> getRoutePoint(routeEnd);
            case LOOP -> throw new IllegalStateException("Loop phase handled separately");
        };

        if (mc.player.squaredDistanceTo(target) <= ROUTE_ARRIVAL_DISTANCE_SQR) {
            routePhase = switch (routePhase) {
                case INIT_WAYPOINT_1 -> RoutePhase.INIT_WAYPOINT_2;
                case INIT_WAYPOINT_2 -> RoutePhase.INIT_WAYPOINT_3;
                case INIT_WAYPOINT_3 -> RoutePhase.ENTER_LOOP;
                case ENTER_LOOP -> RoutePhase.LOOP;
                case LOOP -> RoutePhase.LOOP;
            };
            if (routePhase == RoutePhase.LOOP) headingToRouteEnd = false;
            stopRouteMovement();
            return;
        }

        flyTowards(target);
    }

    private void flyTowards(Vec3d target) {
        Vec3d difference = target.subtract(mc.player.getEntityPos());
        double speed = Math.min(flightSpeed.get(), difference.length());
        mc.player.setVelocity(difference.normalize().multiply(speed));
        mc.player.setOnGround(false);
    }

    private Vec3d getRoutePoint(Setting<Vector3d> setting) {
        Vector3d v = setting.get();
        return new Vec3d(v.x, v.y, v.z);
    }

    private Vec3d getInitWaypoint3() {
        return new Vec3d(getRoutePoint(routeStart).x, 40, 30);
    }

    private void resetRoute() {
        headingToRouteEnd = false;
        routePhase = RoutePhase.INIT_WAYPOINT_1;
    }

    private void stopRouteMovement() {
        if (mc.player != null) mc.player.setVelocity(Vec3d.ZERO);
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        reconnectPending = false;
        loginSelectPending = false;
        guiOpenPending = false;
        hoveredPveSlotId = -1;
        pveClickSent = false;
        resetRoute();

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

        if (event.getMessage().getString().contains("欢迎玩家")) {
            loggedIn = true;
            String pwd = password.get();
            if (!pwd.isEmpty()) {
                ChatUtils.sendPlayerMsg("/login " + pwd);
            }
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
                openServerSelector();
                loginSelectPending = false;
                guiOpenPending = true;
                hoveredPveSlotId = -1;
                pveClickSent = false;
                selectTimer.reset();
            }
            return;
        }

        if (guiOpenPending) {
            Screen screen = mc.currentScreen;
            if (screen == null) {
                if (selectTimer.hasPassTime(2000)) {
                    hoveredPveSlotId = -1;
                    pveClickSent = false;
                    openServerSelector();
                    selectTimer.reset();
                }
                return;
            }

            if (!(screen instanceof HandledScreen<?> containerScreen)) {
                if (selectTimer.hasPassTime(2000)) {
                    openServerSelector();
                    selectTimer.reset();
                }
                return;
            }

            ScreenHandler menu = containerScreen.getScreenHandler();
            Slot pveSlot = findPveSlot(menu);
            if (pveSlot == null) {
                hoveredPveSlotId = -1;
                return;
            }

            if (pveClickSent) return;

            HandledScreenAccessor accessor = (HandledScreenAccessor) containerScreen;
            double mouseX = accessor.autopve$getX() + pveSlot.x + 8;
            double mouseY = accessor.autopve$getY() + pveSlot.y + 8;

            if (hoveredPveSlotId != pveSlot.id) {
                double scale = mc.getWindow().getScaleFactor();
                GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), mouseX * scale, mouseY * scale);
                hoveredPveSlotId = pveSlot.id;
                selectTimer.reset();
                return;
            }

            if (selectTimer.hasPassTime(PVE_HOVER_DELAY_MS)) {
                Click click = new Click(mouseX, mouseY, new MouseInput(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
                if (!containerScreen.mouseClicked(click, false)) {
                    mc.interactionManager.clickSlot(menu.syncId, pveSlot.id, 0, SlotActionType.PICKUP, mc.player);
                }
                hoveredPveSlotId = -1;
                pveClickSent = true;
                selectTimer.reset();
            }
        }
    }

    private void openServerSelector() {
        mc.player.getInventory().setSelectedSlot(selectSlot.get());
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private Slot findPveSlot(ScreenHandler menu) {
        if (menu.slots.size() > PVE_MENU_SLOT) {
            Slot fixedSlot = menu.slots.get(PVE_MENU_SLOT);
            if (!fixedSlot.getStack().isEmpty()) return fixedSlot;
        }

        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getName().getString().toLowerCase().contains("pve")) return slot;
        }
        return null;
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

    private void attackNearbyEntities() {
        int attacked = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity) continue;
            if (!(entity instanceof LivingEntity living)) continue;
            if (living instanceof PlayerEntity) continue;
            if (living.isRemoved() || living.getHealth() <= 0) continue;
            if (mc.player.squaredDistanceTo(living) > ATTACK_RANGE_SQR) continue;

            mc.interactionManager.attackEntity(mc.player, living);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (++attacked >= maxTarget.get()) break;
        }
    }

    private void discardDisabledMaterials() {
        if (mc.currentScreen != null) return;
        if (keepDiamond.get() && keepGolden.get() && keepCopper.get()) return;

        PlayerInventory inventory = mc.player.getInventory();
        ScreenHandler handler = mc.player.playerScreenHandler;
        for (Slot slot : handler.slots) {
            if (slot.inventory != inventory) continue;
            if (slot.getIndex() < 0 || slot.getIndex() >= PlayerInventory.MAIN_SIZE) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !shouldDiscard(stack)) continue;

            mc.interactionManager.clickSlot(handler.syncId, slot.id, 1, SlotActionType.THROW, mc.player);
            return;
        }
    }

    private boolean shouldDiscard(ItemStack stack) {
        String itemId = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        String displayName = stack.getName().getString().toLowerCase(Locale.ROOT);

        if (!keepDiamond.get() && matchesMaterial(itemId, displayName, "diamond", "钻石")) return true;
        if (!keepGolden.get() && matchesIngot(itemId, displayName, "gold", "金锭")) return true;
        return !keepCopper.get() && matchesIngot(itemId, displayName, "copper", "铜锭");
    }

    private boolean matchesIngot(String itemId, String displayName, String english, String chineseIngot) {
        if (itemId.contains("alloy") || displayName.contains("alloy") || displayName.contains("合金")) return false;
        return itemId.contains(english + "_ingot") || displayName.contains(english + " ingot") || displayName.contains(chineseIngot);
    }

    private boolean matchesMaterial(String itemId, String displayName, String english, String chinese) {
        return itemId.contains(english) || displayName.contains(english) || displayName.contains(chinese);
    }
}
