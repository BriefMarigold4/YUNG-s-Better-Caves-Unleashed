package com.yungnickyoung.minecraft.bettercaves.util;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import sayys.depthsupdate.core.HeightManager;

//Could I make a check for world height extension instead of just checking for Depths Update? Probably
//Am I gonna do it? Fuck no
//I'm gonna sleep, this coding shit sucks
public class HeightExtensionCheck {
    private static int maxY=255;

    public static int getMaxY(World world){
        if (!Loader.isModLoaded("depthsupdate")) return maxY;
        try {
           return HeightManager.getMaxY(world);
        } catch (Exception e) {
            return maxY;
        }
    }

    private static int minY=0;

    public static int getMinY(World world){
        if (!Loader.isModLoaded("depthsupdate")) return minY;
        try {
            return HeightManager.getMinY(world);
        } catch (Exception e) {
            return minY;
        }
    }
}
