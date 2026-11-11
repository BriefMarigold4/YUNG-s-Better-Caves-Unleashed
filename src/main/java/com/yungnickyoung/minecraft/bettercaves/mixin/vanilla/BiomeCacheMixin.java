package com.yungnickyoung.minecraft.bettercaves.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.biome.BiomeCache;
import com.yungnickyoung.minecraft.bettercaves.config.Configuration;
import org.spongepowered.asm.mixin.*;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(BiomeCache.class)
public abstract class BiomeCacheMixin {

    //Quick and dirty thread safety, slight overhead but much less cost than the rest of the savings
    @Unique
    private final ReentrantLock bettercaves$cacheLock = new ReentrantLock();

    @WrapMethod(
            method = "getEntry"
    )
    private BiomeCache.Block bettercaves_vanillaBiomeCache_getEntry(int x, int z, Operation<BiomeCache.Block> original) {
        if(!Configuration.multithreadBetterCavesNoise) {
            return original.call(x, z);
        }

        this.bettercaves$cacheLock.lock();
        try {
            return original.call(x, z);
        }
        finally {
            this.bettercaves$cacheLock.unlock();
        }
    }

    @WrapMethod(
            method = "cleanupCache"
    )
    private void bettercaves_vanillaBiomeCache_cleanupCache(Operation<Void> original) {
        if(!Configuration.multithreadBetterCavesNoise) {
            original.call();
            return;
        }

        this.bettercaves$cacheLock.lock();
        try {
            original.call();
        }
        finally {
            this.bettercaves$cacheLock.unlock();
        }
    }
}