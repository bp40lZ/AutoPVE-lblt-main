package dev.b_p40lz.autopve.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

public class BlockUtil {
    public static boolean isSafeBlock(BlockPos pos) {
        if (MeteorClient.mc.world == null) return false;
        return MeteorClient.mc.world.getBlockState(pos).isAir()
            && MeteorClient.mc.world.getBlockState(pos.up()).isAir()
            && !MeteorClient.mc.world.getBlockState(pos).isOf(Blocks.BEDROCK);
    }

    public static BlockPos findVclipHole(BlockPos pos, double vclip) {
        if (vclip > 0.0) {
            for (int i = (int) vclip + pos.getY(); i >= pos.getY(); i--) {
                BlockPos checkPos = new BlockPos(pos.getX(), i, pos.getZ());
                BlockPos checkUp = checkPos.up();
                if (isSafeBlock(checkPos) && isSafeBlock(checkUp)) {
                    return checkPos;
                }
            }
        }
        if (vclip < 0.0) {
            for (int i = (int) vclip + pos.getY(); i <= pos.getY(); i++) {
                BlockPos checkPos = new BlockPos(pos.getX(), i, pos.getZ());
                BlockPos checkUp = checkPos.up();
                if (isSafeBlock(checkPos) && isSafeBlock(checkUp)) {
                    return checkPos;
                }
            }
        }
        return pos;
    }
}
