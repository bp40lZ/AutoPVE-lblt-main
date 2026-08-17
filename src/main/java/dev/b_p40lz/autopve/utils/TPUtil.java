package dev.b_p40lz.autopve.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Map;

public class TPUtil {
    private static long lastTpMoveLog;

    public static void sendMovePacket(double x, double y, double z, boolean onGround) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.player.networkHandler != null) {
            MeteorClient.mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, false));
        }
    }

    public static void sendMovePacket(Vec3d vec, boolean onGround) {
        sendMovePacket(vec.x, vec.y, vec.z, onGround);
    }

    public static Vec3d findVClipVecToMove(Vec3d fromVec, Vec3d toVec, double searchStep, boolean allowVoid) {
        Vec3d findVec = null;
        double step = 0.0;

        if (MeteorClient.mc.world == null) return null;

        boolean isEnd = MeteorClient.mc.world.getDimension().hasCeiling();
        int voidY = isEnd ? 0 : -64;

        while (step <= 200.0) {
            Vec3d vec = new Vec3d(fromVec.x, fromVec.y + step, fromVec.z);
            if (!allowVoid || fromVec.y > (double) voidY) {
                if (BlockUtil.isSafeBlock(BlockPos.ofFloored(vec))) {
                    return vec;
                }
            }
            vec = new Vec3d(fromVec.x, fromVec.y - step, fromVec.z);
            if (!allowVoid || fromVec.y > (double) voidY) {
                if (BlockUtil.isSafeBlock(BlockPos.ofFloored(vec))) {
                    return vec;
                }
            }
            step += searchStep;
        }
        return findVec;
    }

    public static void doTp(Vec3d fromVec, Vec3d toVec, double moveDistance, boolean onGround) {
        doTp(fromVec.x, fromVec.y, fromVec.z, toVec.x, toVec.y, toVec.z, moveDistance, onGround);
    }

    public static void doTp(double fromX, double fromY, double fromZ,
                            double toX, double toY, double toZ,
                            double moveDistance, boolean onGround) {
        double distance = Math.sqrt(Utils.squaredDistance(fromX, fromY, fromZ, toX, toY, toZ));
        int steps = (int) Math.ceil(distance / moveDistance);

        for (int i = 1; i <= steps; i++) {
            sendMovePacket(fromX, fromY, fromZ, onGround);
        }

        sendMovePacket(toX, toY, toZ, onGround);
    }

    public static void doTpSmart(Vec3d fromVec, Vec3d toVec, double moveDistance,
                                 boolean startOnGround, boolean targetOnGround) {
        double totalDistance = Math.sqrt(Utils.squaredDistance(fromVec.x, fromVec.y, fromVec.z,
                                                                toVec.x, toVec.y, toVec.z));

        if (totalDistance <= 0.1) {
            sendMovePacket(toVec.x, toVec.y, toVec.z, targetOnGround);
            return;
        }

        int packetCount = Math.max(1, (int) Math.ceil(totalDistance / moveDistance));

        for (int i = 1; i < packetCount; i++) {
            double progress = (double) i / (double) packetCount;
            double x = fromVec.x + (toVec.x - fromVec.x) * progress;
            double y = fromVec.y + (toVec.y - fromVec.y) * progress;
            double z = fromVec.z + (toVec.z - fromVec.z) * progress;
            sendMovePacket(x, y, z, false);
        }

        sendMovePacket(toVec.x, toVec.y, toVec.z, targetOnGround);
        sendMovePacket(toVec.x, toVec.y, toVec.z, targetOnGround);
    }

    private static void doTpSegment(double fromX, double fromY, double fromZ,
                                    double toX, double toY, double toZ,
                                    double moveDistance, boolean onGround) {
        double distance = Math.sqrt(Utils.squaredDistance(fromX, fromY, fromZ, toX, toY, toZ));
        int steps = (int) Math.ceil(distance / moveDistance);

        if (steps <= 0) {
            sendMovePacket(toX, toY, toZ, onGround);
            return;
        }

        for (int i = 0; i < steps; i++) {
            double progress = (double) i / (double) steps;
            double x = fromX + (toX - fromX) * progress;
            double y = fromY + (toY - fromY) * progress;
            double z = fromZ + (toZ - fromZ) * progress;
            sendMovePacket(x, y, z, onGround);
        }

        sendMovePacket(toX, toY, toZ, onGround);
    }

    public static void doTpMove(Vec3d fromVec, Vec3d toVec, double moveDistance, boolean back, Runnable action) {
        doTpMove(fromVec, toVec, moveDistance, back, action, null);
    }

    public static boolean doTpMoveNoUpwardBridge(Vec3d fromVec, Vec3d toVec, double moveDistance, Runnable action) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;
        if (MeteorClient.mc.player.networkHandler == null) return false;
        if (!isBlinkVec(fromVec) || !isGroundedBlinkVec(toVec)) return false;

        Vec3d bridge = findNonAscendingBridge(fromVec, toVec);
        if (bridge == null) return false;

        int packets = (int) Math.ceil(Math.max(fromVec.distanceTo(bridge), fromVec.distanceTo(toVec)) / moveDistance) + 3;
        if (packets > 20) return false;

        for (int i = 1; i < packets; i++) {
            sendMovePacket(fromVec, false);
        }
        sendMovePacket(bridge, false);
        sendMovePacket(toVec, false);

        action.run();

        sendMovePacket(toVec, true);
        MeteorClient.mc.player.setPosition(toVec.x, toVec.y, toVec.z);
        return true;
    }

    public static void doTpMove(Vec3d fromVec, Vec3d toVec, double moveDistance, boolean back, Runnable action, Map<BlockPos, BlockState> extraPosMap) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return;
        if (MeteorClient.mc.player.networkHandler == null) return;
        if (!isBlinkVec(fromVec, extraPosMap) || !isBlinkVec(toVec, extraPosMap)) {
            if (System.currentTimeMillis() - lastTpMoveLog > 1000) {
                System.out.println("[TPUtil] doTpMove BLINK FAIL from=" + fromVec + " to=" + toVec);
                lastTpMoveLog = System.currentTimeMillis();
            }
            return;
        }

        Vec3d a1 = findVClipVecToMove(fromVec, toVec);
        Vec3d b1 = findVClipVecToMove(toVec, fromVec);

        int a2 = (int) Math.ceil(fromVec.distanceTo(a1) / moveDistance);
        int b2 = (int) Math.ceil(toVec.distanceTo(b1) / moveDistance);
        int c  = (int) Math.ceil(fromVec.distanceTo(toVec) / moveDistance);
        int p  = NumberUtils.max(a2, b2, c) - 1 + 4;

        if (System.currentTimeMillis() - lastTpMoveLog > 1000) {
            System.out.println("[TPUtil] doTpMove from=" + fromVec + " to=" + toVec + " a1=" + a1 + " p=" + p + " md=" + moveDistance);
            lastTpMoveLog = System.currentTimeMillis();
        }

        if (p > 100) return;

        for (int i = 1; i < p; i++) {
            sendMovePacket(fromVec, false);
        }
        sendMovePacket(a1, false);
        sendMovePacket(toVec, false);

        try {
            action.run();
        } finally {
            if (back) {
                sendMovePacket(b1, false);
                sendMovePacket(fromVec, false);
                MeteorClient.mc.player.setPosition(fromVec.x, fromVec.y, fromVec.z);
            } else {
                boolean grounded = isGroundedBlinkVec(toVec);
                sendMovePacket(toVec, grounded);
                MeteorClient.mc.player.setPosition(toVec.x, toVec.y, toVec.z);
            }
        }
    }

    public static Vec3d findVClipVecToMove(Vec3d fromVec, Vec3d toVec) {
        if (MeteorClient.mc.world == null) return fromVec;
        if (canTpThrough(fromVec, toVec)) return fromVec;

        double maxSearchY = Math.max(MeteorClient.mc.world.getHeight(), fromVec.y + 200.0);
        for (double step = 0.0; step <= 200.0; step += 1.8) {
            Vec3d vecDown = new Vec3d(fromVec.x, fromVec.y - step, fromVec.z);
            if (vecDown.y > MeteorClient.mc.world.getBottomY() && canTpThrough(vecDown, toVec)) return vecDown;

            Vec3d vecUp = new Vec3d(fromVec.x, fromVec.y + step, fromVec.z);
            if (vecUp.y <= maxSearchY && canTpThrough(vecUp, toVec)) return vecUp;
        }
        return fromVec;
    }

    private static Vec3d findNonAscendingBridge(Vec3d fromVec, Vec3d toVec) {
        if (canTpThrough(fromVec, toVec)) return fromVec;

        for (double step = 1.8; step <= 200.0; step += 1.8) {
            Vec3d bridge = new Vec3d(fromVec.x, fromVec.y - step, fromVec.z);
            if (bridge.y <= MeteorClient.mc.world.getBottomY()) break;
            if (isBlinkVec(bridge) && canTpThrough(bridge, toVec)) return bridge;
        }

        return null;
    }

    public static boolean canTpThrough(Vec3d fromVec, Vec3d toVec) {
        if (fromVec.x == toVec.x && fromVec.z == toVec.z) return true;
        for (double[] offset : getPlayerDoubles()) {
            Vec3d from = fromVec.add(offset[0], offset[1], offset[2]);
            Vec3d to = toVec.add(offset[0], offset[1], offset[2]);
            if (!canSee(from, to)) return false;
        }
        return true;
    }

    private static double[][] getPlayerDoubles() {
        if (MeteorClient.mc.player == null) return new double[0][];
        Box box = MeteorClient.mc.player.getBoundingBox();
        double halfLx = box.getLengthX() / 2.0 - 0.1;
        double Ly = box.getLengthY() - 1.0E-4;
        double halfLz = box.getLengthZ() / 2.0 - 0.1;
        return new double[][] {
            { halfLx, 1.0E-4, halfLz }, { halfLx, 1.0E-4, -halfLz },
            { -halfLx, 1.0E-4, halfLz }, { -halfLx, 1.0E-4, -halfLz },
            { halfLx, 1.0, halfLz }, { halfLx, 1.0, -halfLz },
            { -halfLx, 1.0, halfLz }, { -halfLx, 1.0, -halfLz },
            { halfLx, Ly, halfLz }, { halfLx, Ly, -halfLz },
            { -halfLx, Ly, halfLz }, { -halfLx, Ly, -halfLz }
        };
    }

    public static boolean isBlinkVec(Vec3d vec) {
        return isBlinkVec(vec, null);
    }

    public static boolean isBlinkVec(Vec3d vec, Map<BlockPos, BlockState> extraPosMap) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;
        if (vec.y < MeteorClient.mc.world.getBottomY()) return false;

        Box box = MeteorClient.mc.player.getBoundingBox();
        double halfX = box.getLengthX() / 2.0;
        double height = box.getLengthY();
        double halfZ = box.getLengthZ() / 2.0;
        double[][] corners = {
            { halfX, 0.0, halfZ }, { halfX, 0.0, -halfZ },
            { -halfX, 0.0, halfZ }, { -halfX, 0.0, -halfZ }
        };
        for (double[] corner : corners) {
            BlockPos feet = BlockPos.ofFloored(vec.add(corner[0], 0.0, corner[2]));
            if (isCollisionBlock(feet, extraPosMap)) return false;
            if (isCollisionBlock(feet.up(), extraPosMap)) return false;
            BlockPos head = BlockPos.ofFloored(vec.add(corner[0], height, corner[2]));
            if (isCollisionBlock(head, extraPosMap)) return false;
        }
        return true;
    }

    public static boolean isGroundedBlinkVec(Vec3d vec) {
        return isBlinkVec(vec) && isCollisionBlock(BlockPos.ofFloored(vec).down());
    }

    public static boolean isCollisionBlock(BlockPos pos) {
        return isCollisionBlock(pos, null);
    }

    public static boolean isCollisionBlock(BlockPos pos, Map<BlockPos, BlockState> extraPosMap) {
        if (MeteorClient.mc.world == null) return true;
        if (extraPosMap != null && extraPosMap.containsKey(pos)) return true;
        return !MeteorClient.mc.world.getBlockState(pos).getCollisionShape(MeteorClient.mc.world, pos).isEmpty();
    }

    public static boolean canSee(Vec3d from, Vec3d to) {
        if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) return false;
        RaycastContext ctx = new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, MeteorClient.mc.player);
        BlockHitResult hit = MeteorClient.mc.world.raycast(ctx);
        return hit == null || hit.getType() == HitResult.Type.MISS;
    }

    public static Vec3d getPrevPos(Entity entity, float partialTicks) {
        return new Vec3d(
            entity.getX() + (entity.getX() - entity.lastX) * partialTicks,
            entity.getY() + (entity.getY() - entity.lastY) * partialTicks,
            entity.getZ() + (entity.getZ() - entity.lastZ) * partialTicks
        );
    }
}
