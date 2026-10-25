package dev.wrenchbridge;

import net.minecraft.resources.ResourceLocation;

/** A native callback's identity and screen/action binding, independent of its client classes. */
public record NativeInputRule(String item, String screenClass, ResourceLocation action) {
    public static final NativeInputRule CREATE_ROTATION = new NativeInputRule("create:wrench",
            "com.simibubi.create.content.contraptions.wrench.RadialWrenchMenu",
            ResourceLocation.parse("create:rotation_menu"));
}
