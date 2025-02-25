/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package minegame159.meteorclient.systems.modules.combat;

import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.events.game.OpenScreenEvent;
import minegame159.meteorclient.events.world.TickEvent;
import minegame159.meteorclient.settings.*;
import minegame159.meteorclient.systems.modules.Categories;
import minegame159.meteorclient.systems.modules.Module;
import minegame159.meteorclient.utils.entity.SortPriority;
import minegame159.meteorclient.utils.entity.TargetUtils;
import minegame159.meteorclient.utils.player.InvUtils;
import minegame159.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

public class AutoAnvil extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");

    // General

    private final Setting<Boolean> toggleOnBreak = sgGeneral.add(new BoolSetting.Builder()
            .name("toggle-on-break")
            .description("Toggles when the target's helmet slot is empty.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
            .name("rotate")
            .description("Automatically rotates towards the position anvils/pressure plates/buttons are placed.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> antiStep = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-step")
            .description("Place extra blocks for preventing the enemy from escaping")
            .defaultValue(false)
            .build()
    );

    // Place

    private final Setting<Double> range = sgPlace.add(new DoubleSetting.Builder()
            .name("target-range")
            .description("The radius in which players get targeted.")
            .defaultValue(4)
            .min(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<SortPriority> priority = sgPlace.add(new EnumSetting.Builder<SortPriority>()
            .name("target-priority")
            .description("How to select the player to target.")
            .defaultValue(SortPriority.LowestHealth)
            .build()
    );

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
            .name("delay")
            .description("The delay in between anvil placements.")
            .min(0)
            .defaultValue(0)
            .sliderMax(50)
            .build()
    );

    private final Setting<Integer> startHeight = sgPlace.add(new IntSetting.Builder()
            .name("start-Height")
            .description("The height at the beginning.")
            .defaultValue(5)
            .min(0)
            .max(10)
            .sliderMin(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Integer> minHeight = sgPlace.add(new IntSetting.Builder()
            .name("min-Height")
            .description("The minimum height acceptable.")
            .defaultValue(1)
            .min(0)
            .max(5)
            .sliderMin(0)
            .sliderMax(5)
            .build()
    );

    private final Setting<Double> decrease = sgPlace.add(new DoubleSetting.Builder()
            .name("decrease")
            .description("The distance where it will start to decrease.")
            .defaultValue(1.4)
            .min(0)
            .max(4)
            .sliderMin(0)
            .sliderMax(4)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("The number of blocks you can place every ticks.")
            .defaultValue(4)
            .min(2)
            .max(8)
            .sliderMin(2)
            .sliderMax(8)
            .build()
    );

    private final Setting<Boolean> placeButton = sgPlace.add(new BoolSetting.Builder()
            .name("place-at-feet")
            .description("Automatically places a button or pressure plate at the targets feet to break the anvils.")
            .defaultValue(true)
            .build()
    );

    private final List<BlockPos> antiStepStructure = Arrays.asList(
            new BlockPos(1, 2, 0),
            new BlockPos(-1, 2, 0),
            new BlockPos(0, 2, 1),
            new BlockPos(0, 2, -1)
    );

    public AutoAnvil() {
        super(Categories.Combat, "auto-anvil", "Automatically places anvils above players to destroy helmets.");
    }

    private PlayerEntity target;
    private int timer;

    @Override
    public void onActivate() {
        timer = 0;
        target = null;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof AnvilScreen) mc.player.closeScreen();
    }

    // Given a position, this function say if, that position, is surrounded by blocks
    private boolean isHole(BlockPos pos) {
        BlockPos.Mutable posStart = new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ());
        return mc.world.getBlockState(posStart.add(1,0,0)).getBlock().is(Blocks.AIR) ||
                mc.world.getBlockState(posStart.add(-1,0,0)).getBlock().is(Blocks.AIR) ||
                mc.world.getBlockState(posStart.add(0,0,1)).getBlock().is(Blocks.AIR) ||
                mc.world.getBlockState(posStart.add(0,0,-1)).getBlock().is(Blocks.AIR);

    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Head check
        if (isActive() && toggleOnBreak.get() && target != null && target.inventory.getArmorStack(3).isEmpty()) {
            error("Target head slot is empty... disabling.");
            toggle();
            return;
        }

        // Check distance + alive
        if (TargetUtils.isBadTarget(target, range.get())) target = TargetUtils.getPlayerTarget(range.get(), priority.get());
        if (TargetUtils.isBadTarget(target, range.get())) return;

        // Number of blocks we have placed
        int blocksPlaced = 0;

        // Iterate
        if (timer >= delay.get() && target != null) {
            // Reset timer
            timer = 0;

            // Get anvil slot
            int slot = getAnvilSlot();
            // If not found
            if (slot == -1) return;

            // Place button
            if (placeButton.get()) {
                // Get button
                int slot2 = getFloorSlot();
                // Get position of the button
                BlockPos blockPos = target.getBlockPos();

                if (BlockUtils.place(blockPos, Hand.MAIN_HAND, slot2, rotate.get(), 0, false)) {
                    // If the block has been placed, incr
                    blocksPlaced++;
                }
            }

            // antiStep structure
            if (antiStep.get()) {
                // Get obby slot
                int slotObby = InvUtils.findItemInHotbar(Blocks.OBSIDIAN.asItem());
                if (slotObby == -1) return;
                // Iterate for every blocks in antiStepStructure
                for(BlockPos pos : antiStepStructure) {
                    // Position where we are going to place
                    BlockPos posBlock = target.getBlockPos().add(pos);
                    // Try to place
                    if (BlockUtils.place(posBlock, Hand.MAIN_HAND, slotObby, rotate.get(), 0, true)) {
                        if (++blocksPlaced == blocksPerTick.get())
                            return;
                    }
                }
            }

            // Get start height that is the result by the startHeight default value + the difference between our Y and the enemy's Y
            int startHeightValue = startHeight.get() + (int)(mc.player.getY() - target.getY());
            // We want the XZ distance, not the XYZ
            double distanceEnemyXZ = Math.sqrt(Math.pow(mc.player.getPos().x - target.getX(), 2) + Math.pow(mc.player.getPos().z - target.getZ(), 2));
            // Decrease the startHeight if we are too far from the enemy
            while (distanceEnemyXZ > decrease.get()) {
                startHeightValue -= 1;
                distanceEnemyXZ -= decrease.get();
            }

            // If we are too far away
            if (startHeightValue <= minHeight.get()) {
                error("Target too far away... disabling.");
                toggle();
                return;
            }

            // Place anvil
            BlockPos blockPos = target.getBlockPos().up().add(0, startHeightValue, 0);
            BlockUtils.place(blockPos, Hand.MAIN_HAND, slot, rotate.get(), 0, true);
        } else timer++;
    }

    public int getFloorSlot() {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.inventory.getStack(i).getItem();
            Block block = Block.getBlockFromItem(item);

            if (block instanceof AbstractPressurePlateBlock || block instanceof AbstractButtonBlock) {
                slot = i;
                break;
            }
        }
        return slot;
    }

    private int getAnvilSlot() {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            Item item = mc.player.inventory.getStack(i).getItem();
            Block block = Block.getBlockFromItem(item);

            if (block instanceof AnvilBlock) {
                slot = i;
                break;
            }
        }
        return slot;
    }

    @Override
    public String getInfoString() {
        if (target != null) return target.getEntityName();
        return null;
    }
}
