package com.yungnickyoung.minecraft.bettercaves.world.carver.cavern;

import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.enums.CavernType;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCavesUtils;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverSettings;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverUtils;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarverBuilder;
import com.yungnickyoung.minecraft.bettercaves.world.carver.ICarver;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseColumnNew;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseGenNew;
import com.yungnickyoung.minecraft.bettercaves.util.HeightExtensionCheck;

/**
 * BetterCaves Cavern carver.
 * Caverns are large openings generated at the bottom of the world.
 */
public class CavernCarver implements ICarver {
    private CarverSettings settings;
    private World world;

    private CavernType cavernType;
    private int bottomY;
    private int topY;

    private NoiseGenNew noiseGenNew;

    private int maxY;
    private int minY;

    public CavernCarver(final CavernCarverBuilder builder) {
        settings = builder.getSettings();
        noiseGenNew = new NoiseGenNew(
                settings.getWorld(),
                settings.isFastNoise(),
                settings.getNoiseSettings(),
                settings.getNumGens(),
                settings.getyCompression(),
                settings.getXzCompression()
        );
        world = builder.getSettings().getWorld();
        cavernType = builder.getCavernType();
        bottomY = builder.getBottomY();
        topY = builder.getTopY();
        minY = HeightExtensionCheck.getMinY(builder.getSettings().getWorld());
        maxY = HeightExtensionCheck.getMaxY(builder.getSettings().getWorld());
        if (bottomY > topY) {
            BetterCaves.LOGGER.warn("Warning: Min altitude for caverns should not be greater than max altitude.");
            BetterCaves.LOGGER.warn("Using default values...");
            this.bottomY = 1;
            this.topY = 35;
        }
    }

    public void carveColumnNew(ChunkPrimer primer, BlockPos colPos, int topY, float smoothAmp, NoiseColumnNew noises, IBlockState liquidBlock, boolean flooded) {
        if(this.bottomY >= minY && this.bottomY <= maxY) {
            int localX = BetterCavesUtils.getLocal(colPos.getX());
            int localZ = BetterCavesUtils.getLocal(colPos.getZ());
            if(localX >= 0 && localX <= 15) {
                if(localZ >= 0 && localZ <= 15) {
                    if(topY <= maxY) {
                        topY -= 2;
                        int topTransitionBoundary = Math.max(topY - 6, 1);
                        int bottomTransitionBoundary = this.bottomY + 3;
                        if(this.cavernType == CavernType.FLOORED) {
                            bottomTransitionBoundary = this.bottomY < this.settings.getLiquidAltitude() ? this.settings.getLiquidAltitude() + 8 : this.bottomY + 7;
                        }
                        bottomTransitionBoundary = Math.min(bottomTransitionBoundary, maxY);
                        for(int y = topY; y >= this.bottomY && (y > this.settings.getLiquidAltitude() || liquidBlock != null); y--) {
                            boolean digBlock = false;

                            float noise = 1.0F;
                            for(double n : noises.get(y).getNoiseValuesArray()) {
                                noise *= (float)n;
                            }

                            float noiseThreshold = this.settings.getNoiseThreshold();
                            if(y >= topTransitionBoundary) {
                                noiseThreshold *= (float)(y - topY) / (float)(topTransitionBoundary - topY);
                            }

                            if(y < bottomTransitionBoundary) {
                                noiseThreshold *= (float)(y - this.bottomY) / (float)(bottomTransitionBoundary - this.bottomY);
                            }

                            if(smoothAmp < 1.0F) {
                                noiseThreshold *= smoothAmp;
                            }

                            if(noise < noiseThreshold) {
                                digBlock = true;
                            }

                            if(this.settings.isEnableDebugVisualizer()) {
                                BlockPos blockPos = new BlockPos(localX, y, localZ);
                                CarverUtils.debugDigBlock(primer, blockPos, this.settings.getDebugBlock(), digBlock);
                            }
                            else if(digBlock) {
                                BlockPos blockPos = new BlockPos(localX, y, localZ);
                                IBlockState airBlockState = flooded && y < this.world.getSeaLevel() ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                                CarverUtils.digBlock(this.settings.getWorld(), primer, blockPos, airBlockState, liquidBlock, this.settings.getLiquidAltitude(), this.settings.isReplaceFloatingGravel());
                            }
                        }
                    }
                }
            }
        }
    }

    public NoiseGenNew getNoiseGenNew() {
        return this.noiseGenNew;
    }

    public CarverSettings getSettings() {
        return settings;
    }

    public int getPriority() {
        return settings.getPriority();
    }

    public int getBottomY() {
        return bottomY;
    }

    public int getTopY() {
        return topY;
    }
}
