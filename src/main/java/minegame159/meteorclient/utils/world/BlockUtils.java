/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package minegame159.meteorclient.utils.world;

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.MeteorClient;
import minegame159.meteorclient.events.game.GameLeftEvent;
import minegame159.meteorclient.mixininterface.IVec3d;
import minegame159.meteorclient.utils.player.Rotations;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockBreakingInfo;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Vec3d hitPos = new Vec3d(0, 0, 0);

    private static final ArrayList<BlockPos> blocks = new ArrayList<>();

    public static final Map<Integer, BlockBreakingInfo> breakingBlocks = new HashMap<>();

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(BlockUtils.class);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        breakingBlocks.clear();
    }

    public static boolean place(BlockPos blockPos, Hand hand, int slot, boolean rotate, int priority, boolean swing, boolean checkEntities, boolean swap, boolean swapBack) {
        if (slot == -1 || !canPlace(blockPos, checkEntities)) return false;

        Direction side = getPlaceSide(blockPos);
        BlockPos neighbour;
        Vec3d hitPos = rotate ? new Vec3d(0, 0, 0) : BlockUtils.hitPos;

        if (side == null) {
            side = Direction.UP;
            neighbour = blockPos;
            ((IVec3d) hitPos).set(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        } else {
            neighbour = blockPos.offset(side.getOpposite());
            // The Y is not 0.5 but 0.6 for allowing "antiAnchor" placement. This should not damage any other modules
            ((IVec3d) hitPos).set(neighbour.getX() + 0.5 + side.getOffsetX() * 0.5, neighbour.getY() + 0.6 + side.getOffsetY() * 0.5, neighbour.getZ() + 0.5 + side.getOffsetZ() * 0.5);
        }

        if (rotate) {
            Direction s = side;
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), priority, () -> place(slot, hitPos, hand, s, neighbour, swing, swap, swapBack));
        } else place(slot, hitPos, hand, side, neighbour, swing, swap, swapBack);

        return true;
    }

    public static boolean place(BlockPos blockPos, Hand hand, int slot, boolean rotate, int priority, boolean checkEntities) {
        return place(blockPos, hand, slot, rotate, priority, true, checkEntities, true, true);
    }

    private static void place(int slot, Vec3d hitPos, Hand hand, Direction side, BlockPos neighbour, boolean swing, boolean swap, boolean swapBack) {
        int preSlot = mc.player.inventory.selectedSlot;
        if (swap) mc.player.inventory.selectedSlot = slot;

        boolean wasSneaking = mc.player.input.sneaking;
        mc.player.input.sneaking = false;

        mc.interactionManager.interactBlock(mc.player, mc.world, hand, new BlockHitResult(hitPos, side, neighbour, false));
        if (swing) mc.player.swingHand(hand);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));

        mc.player.input.sneaking = wasSneaking;

        if (swapBack) mc.player.inventory.selectedSlot = preSlot;
    }

    public static boolean canPlace(BlockPos blockPos, boolean checkEntities) {
        if (blockPos == null) return false;

        // Check y level
        if (World.isOutOfBuildLimitVertically(blockPos)) return false;

        // Check if current block is replaceable
        if (!mc.world.getBlockState(blockPos).getMaterial().isReplaceable()) return false;

        // Check if intersects entities
        return !checkEntities || mc.world.canPlace(Blocks.STONE.getDefaultState(), blockPos, ShapeContext.absent());
    }

    public static boolean canPlace(BlockPos blockPos) {
        return canPlace(blockPos, true);
    }

    public static boolean isClickable(Block block) {
        boolean clickable = block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof AbstractButtonBlock
                || block instanceof AbstractPressurePlateBlock
                || block instanceof BlockWithEntity
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapdoorBlock;

        return clickable;
    }

    private static Direction getPlaceSide(BlockPos blockPos) {
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.offset(side);
            Direction side2 = side.getOpposite();

            BlockState state = mc.world.getBlockState(neighbor);

            // Check if neighbour isn't empty
            if (state.isAir() || isClickable(state.getBlock())) continue;

            // Check if neighbour is a fluid
            if (!state.getFluidState().isEmpty()) continue;

            return side2;
        }

        return null;
    }

    public static List<BlockPos> getSphere(BlockPos centerPos, int radius, int height) {
        blocks.clear();

        for (int i = centerPos.getX() - radius; i < centerPos.getX() + radius; i++) {
            for (int j = centerPos.getY() - height; j < centerPos.getY() + height; j++) {
                for (int k = centerPos.getZ() - radius; k < centerPos.getZ() + radius; k++) {
                    BlockPos pos = new BlockPos(i, j, k);
                    if (distanceBetween(centerPos, pos) <= radius && !blocks.contains(pos)) blocks.add(pos);
                }
            }
        }

        return blocks;
    }

    public static double distanceBetween(BlockPos blockPos1, BlockPos blockPos2) {
        double d = blockPos1.getX() - blockPos2.getX();
        double e = blockPos1.getY() - blockPos2.getY();
        double f = blockPos1.getZ() - blockPos2.getZ();
        return MathHelper.sqrt(d * d + e * e + f * f);
    }

}
