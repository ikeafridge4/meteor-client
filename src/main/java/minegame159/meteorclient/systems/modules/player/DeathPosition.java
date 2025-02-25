/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package minegame159.meteorclient.systems.modules.player;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import meteordevelopment.orbit.EventHandler;
import minegame159.meteorclient.events.game.OpenScreenEvent;
import minegame159.meteorclient.gui.GuiTheme;
import minegame159.meteorclient.gui.widgets.WLabel;
import minegame159.meteorclient.gui.widgets.WWidget;
import minegame159.meteorclient.gui.widgets.containers.WHorizontalList;
import minegame159.meteorclient.gui.widgets.pressable.WButton;
import minegame159.meteorclient.settings.BoolSetting;
import minegame159.meteorclient.settings.Setting;
import minegame159.meteorclient.settings.SettingGroup;
import minegame159.meteorclient.systems.modules.Categories;
import minegame159.meteorclient.systems.modules.Module;
import minegame159.meteorclient.systems.waypoints.Waypoint;
import minegame159.meteorclient.systems.waypoints.Waypoints;
import minegame159.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.BaseText;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Vec3d;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static minegame159.meteorclient.utils.player.ChatUtils.formatCoords;

public class DeathPosition extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> createWaypoint = sgGeneral.add(new BoolSetting.Builder()
            .name("create-waypoint")
            .description("Creates a waypoint when you die.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> showTimestamp = sgGeneral.add(new BoolSetting.Builder()
            .name("show-timestamp")
            .description("Show timestamp in chat.")
            .defaultValue(true)
            .build()
    );
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    private final Map<String, Double> deathPos = new HashMap<>();
    private Waypoint waypoint;

    private Vec3d dmgPos;

    private String labelText = "No latest death";

    public DeathPosition() {
        super(Categories.Player, "death-position", "Sends you the coordinates to your latest death.");
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (event.screen instanceof DeathScreen) onDeath();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WHorizontalList list = theme.horizontalList();

        WLabel label = list.add(theme.label(labelText)).expandCellX().widget();

        WButton path = list.add(theme.button("Path")).widget();
        path.action = this::path;

        WButton clear = list.add(theme.button("Clear")).widget();
        clear.action = () -> {
            Waypoints.get().remove(waypoint);
            labelText = "No latest death";

            label.set(labelText);
        };

        return list;
    }

    private void onDeath() {
        if (mc.player == null) return;
        dmgPos = mc.player.getPos();
        deathPos.put("x", dmgPos.x);
        deathPos.put("z", dmgPos.z);
        labelText = String.format("Latest death: %.1f, %.1f, %.1f", dmgPos.x, dmgPos.y, dmgPos.z);

        String time = dateFormat.format(new Date());
        BaseText text = new LiteralText("Died at ");
        text.append(formatCoords(dmgPos));
        text.append(showTimestamp.get() ? String.format(" on %s.", time) : ".");
        info(text);

        // Create waypoint
        if (createWaypoint.get()) {
            waypoint = new Waypoint();
            waypoint.name = "Death " + time;

            waypoint.x = (int) dmgPos.x;
            waypoint.y = (int) dmgPos.y + 2;
            waypoint.z = (int) dmgPos.z;
            waypoint.maxVisibleDistance = Integer.MAX_VALUE;
            waypoint.actualDimension = PlayerUtils.getDimension();

            switch (PlayerUtils.getDimension()) {
                case Overworld:
                    waypoint.overworld = true;
                    break;
                case Nether:
                    waypoint.nether = true;
                    break;
                case End:
                    waypoint.end = true;
                    break;
            }

            Waypoints.get().add(waypoint);
        }
    }

    private void path() {
        if (deathPos.isEmpty() && mc.player != null) {
            warning("No latest death found.");
        }
        else {
            if (mc.world != null) {
                double x = dmgPos.x, z = dmgPos.z;
                if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing()) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
                }

                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) x, (int) z));
            }
        }
    }
}
