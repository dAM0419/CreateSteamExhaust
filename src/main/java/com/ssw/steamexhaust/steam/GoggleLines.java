package com.ssw.steamexhaust.steam;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 엔지니어 고글로 보일러를 볼 때 배기 정보를 덧붙인다 (기획서 9.3). */
public final class GoggleLines {
    private GoggleLines() {
    }

    // Create 고글 들여쓰기 (IHaveGoggleInformation: 4칸, 하위 항목은 1칸 더)
    private static final String INDENT = "    ";
    private static final String SUB = "     ";

    public static void append(FluidTankBlockEntity be, List<Component> tooltip) {
        FluidTankBlockEntity controller = be.getControllerBE();
        if (controller == null) {
            return;
        }
        SteamState st = BoilerHooks.state(controller.boiler);
        if (st == null || !st.applies) {
            return;
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.literal(INDENT).append(
                Component.translatable("steamexhaust.goggles.title").withStyle(ChatFormatting.GRAY)));

        ChatFormatting levelColor = st.status == SteamState.STATUS_LIMITED ? ChatFormatting.GOLD
                : st.status == SteamState.STATUS_HEADROOM ? ChatFormatting.YELLOW : ChatFormatting.AQUA;
        tooltip.add(Component.literal(SUB).append(Component.translatable("steamexhaust.goggles.exhaust",
                        Component.literal(String.valueOf(st.exhaustLevel)).withStyle(levelColor),
                        Component.literal(format(st.capacity)).withStyle(ChatFormatting.BLUE))
                .withStyle(ChatFormatting.DARK_GRAY)));

        if (st.status != SteamState.STATUS_OK) {
            tooltip.add(Component.literal(SUB).append(statusLine(st)));
        }
        if (st.overheatRatio > 0) {
            ChatFormatting color = st.overheatRatio > 0.75F ? ChatFormatting.DARK_RED : ChatFormatting.RED;
            tooltip.add(Component.literal(SUB).append(Component.translatable("steamexhaust.goggles.overheat",
                    Math.round(st.overheatRatio * 100)).withStyle(color)));
        }
        if (st.recovered > 0.05F) {
            tooltip.add(Component.literal(SUB).append(Component.translatable("steamexhaust.goggles.recovered",
                    format(st.recovered)).withStyle(ChatFormatting.DARK_AQUA)));
        }
    }

    private static MutableComponent statusLine(SteamState st) {
        return switch (st.status) {
            case SteamState.STATUS_HEADROOM -> Component.translatable("steamexhaust.goggles.status.headroom",
                    String.valueOf(st.exhaustLevel)).withStyle(ChatFormatting.YELLOW);
            case SteamState.STATUS_LIMITED -> Component.translatable("steamexhaust.goggles.status.limited").withStyle(ChatFormatting.GOLD);
            case SteamState.STATUS_NO_EXHAUST -> Component.translatable("steamexhaust.goggles.status.no_exhaust").withStyle(ChatFormatting.RED);
            case SteamState.STATUS_FEED_MIXED -> Component.translatable("steamexhaust.goggles.status.feed_mixed").withStyle(ChatFormatting.RED);
            case SteamState.STATUS_BLOCKED -> Component.translatable("steamexhaust.goggles.status.blocked").withStyle(ChatFormatting.RED);
            case SteamState.STATUS_DISTANCE -> Component.translatable("steamexhaust.goggles.status.distance").withStyle(ChatFormatting.RED);
            case SteamState.STATUS_PUMP_STOPPED -> Component.translatable("steamexhaust.goggles.status.pump_stopped").withStyle(ChatFormatting.RED);
            case SteamState.STATUS_UNKNOWN -> Component.translatable("steamexhaust.goggles.status.unknown").withStyle(ChatFormatting.GRAY);
            case SteamState.STATUS_GRACE -> Component.translatable("steamexhaust.goggles.status.grace").withStyle(ChatFormatting.YELLOW);
            default -> Component.empty();
        };
    }

    private static String format(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05F) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
