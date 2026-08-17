package dev.b_p40lz.autopve.hud;

import dev.b_p40lz.autopve.AddonTemplate;
import dev.b_p40lz.autopve.utils.PacketTracker;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;//这个傻子提交怎么老是卡

public class PacketDisplay extends HudElement {
    public static final HudElementInfo<PacketDisplay> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "packet-display", "Shows the number of sent packets per second.", PacketDisplay::new
    );

    private static final Color SEND_COLOR = new Color(255, 180, 80, 255);

    public PacketDisplay() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        PacketTracker.addSent();
    }

    @Override
    public void render(HudRenderer renderer) {
        String text = "Sent: " + PacketTracker.getSentCount() + "/s";
        renderer.text(text, x, y, SEND_COLOR, true);
        setSize(renderer.textWidth(text, true), renderer.textHeight(true));
    }
}
