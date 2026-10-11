package com.yungnickyoung.minecraft.bettercaves.world.carver.cave;

import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCavesUtils;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverSettings;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverUtils;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cave.CaveCarverBuilder;
import com.yungnickyoung.minecraft.bettercaves.world.carver.ICarver;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseColumnNew;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseGenNew;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseTupleNew;
import com.yungnickyoung.minecraft.bettercaves.util.HeightExtensionCheck;

/**
 * BetterCaves Cave carver
 */
public class CaveCarver implements ICarver {
    private CarverSettings settings;
    private World world;

    /** Surface cutoff depth */
    private int surfaceCutoff;

    /** Cave bottom y-coordinate */
    private int bottomY;

    /* Cave bottom y-coordinate TODO */
    private int topY;

    /**
     * Set true to perform pre-processing on noise values, adjusting them to increase ...
     * ... headroom in the y direction.
     */
    private boolean enableYAdjust;

    /** Adjustment value for the block immediately above. Must be between 0 and 1.0 */
    private float yAdjustF1;

    /** Adjustment value for the block two blocks above. Must be between 0 and 1.0 */
    private float yAdjustF2;

    private NoiseGenNew noiseGenNew;

    private int maxY;
    private int minY;

    public CaveCarver(final CaveCarverBuilder builder) {
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
        surfaceCutoff = builder.getSurfaceCutoff();
        bottomY = builder.getBottomY();
        topY = builder.getTopY();
        minY = HeightExtensionCheck.getMinY(builder.getSettings().getWorld());
        maxY = HeightExtensionCheck.getMaxY(builder.getSettings().getWorld());
        enableYAdjust = builder.isEnableYAdjust();
        yAdjustF1 = builder.getyAdjustF1();
        yAdjustF2 = builder.getyAdjustF2();
        if (bottomY > topY) {
            BetterCaves.LOGGER.warn("Warning: Min altitude for caves should not be greater than max altitude.");
            BetterCaves.LOGGER.warn("Using default values...");
            this.bottomY = 1;
            this.topY = 80;
        }
    }

    public void carveColumnNew(ChunkPrimer primer, BlockPos colPos, int topY, NoiseColumnNew noises, IBlockState liquidBlock, boolean flooded) {
        if(this.bottomY >= minY && this.bottomY <= maxY) {
            if(topY >= minY && topY <= maxY) {
                int localX = BetterCavesUtils.getLocal(colPos.getX());
                int localZ = BetterCavesUtils.getLocal(colPos.getZ());
                if(localX >= 0 && localX <= 15) {
                    if(localZ >= 0 && localZ <= 15) {
                        int transitionBoundary = topY - this.surfaceCutoff;
                        if(transitionBoundary < this.bottomY) transitionBoundary = this.bottomY;

                        float[] thresholds = this.generateThresholdsArray(topY, this.bottomY, transitionBoundary);
                        if(this.enableYAdjust) {
                            this.preprocessCaveNoiseColArray(noises, topY, this.bottomY, thresholds, this.settings.getNumGens());
                        }

                        for(int y = topY; y >= this.bottomY && (y > this.settings.getLiquidAltitude() || liquidBlock != null); y--) {
                            boolean digBlock = true;
                            for(double noise : noises.get(y).getNoiseValuesArray()) {
                                if(noise < (double)thresholds[y - bottomY]) {
                                    digBlock = false;
                                    break;
                                }
                            }
                            if(this.settings.isEnableDebugVisualizer()) {
                                BlockPos blockPos = new BlockPos(localX, y, localZ);
                                CarverUtils.debugDigBlock(primer, blockPos, this.settings.getDebugBlock(), digBlock);
                            }
                            else if(digBlock) {
                                IBlockState airBlockState = flooded && y < this.world.getSeaLevel() ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                                BlockPos blockPos = new BlockPos(localX, y, localZ);
                                CarverUtils.digBlock(this.settings.getWorld(), primer, blockPos, airBlockState, liquidBlock, this.settings.getLiquidAltitude(), this.settings.isReplaceFloatingGravel());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Preprocessing performed on a column of noise to adjust its values before comparing them to the threshold.
     * This function adjusts the noise value of blocks based on the noise values of blocks below.
     * This has the effect of raising the ceilings of caves, giving the player more headroom.
     * Big shoutouts to the guys behind Worley's Caves for this great idea.
     * @param noises The column of noises as a map, mapping the y-coordinate of a block to its NoiseTuple
     * @param topY Top y-coordinate of the noise column
     * @param bottomY Bottom y-coordinate of the noise column
     * @param thresholds Map of y-coordinates to noise thresholds. This is the output of the generateThresholds method.
     * @param numGens Number of noise values to create per block. This is equal to the number of floats held
     *                in each NoiseTuple for each block in the noise column.
     */
    private void preprocessCaveNoiseColArray(NoiseColumnNew noises, int topY, int bottomY, float[] thresholds, int numGens) {
        for(int realY = topY; realY >= bottomY; realY--) {
            NoiseTupleNew noiseBlock = noises.get(realY);
            boolean valid = true;
            for(double noise : noiseBlock.getNoiseValuesArray()) {
                if(noise < (double)thresholds[realY - bottomY]) {
                    valid = false;
                    break;
                }
            }

            if(valid) {
                float f1 = this.yAdjustF1;
                float f2 = this.yAdjustF2;
                if(realY < topY) {
                    NoiseTupleNew tupleAbove = noises.get(realY + 1);
                    for(int i = 0; i < numGens; ++i) {
                        tupleAbove.set(i, (double)(1.0F - f1) * tupleAbove.get(i) + (double)f1 * noiseBlock.get(i));
                    }
                }
                if(realY < topY - 1) {
                    NoiseTupleNew tupleTwoAbove = noises.get(realY + 2);
                    for(int i = 0; i < numGens; ++i) {
                        tupleTwoAbove.set(i, (double)(1.0F - f2) * tupleTwoAbove.get(i) + (double)f2 * noiseBlock.get(i));
                    }
                }
            }
        }
    }

    /**
     * Generate a map of y-coordinates to thresholds for a column of blocks.
     * This is useful because the threshold will decrease near the surface, and it is useful (and more accurate)
     * to have a precomputed threshold value when doing y-adjustments for caves.
     * @param topY Top y-coordinate of the column
     * @param bottomY Bottom y-coordinate of the column
     * @param transitionBoundary The y-coordinate at which the caves start to close off
     * @return Map of y-coordinates to noise thresholds
     */
    private float[] generateThresholdsArray(int topY, int bottomY, int transitionBoundary) {
        float[] thresholds = new float[Math.max(topY - bottomY + 1, 0)];
        for(int realY = bottomY; realY <= topY; realY++) {
            float noiseThreshold = this.settings.getNoiseThreshold();
            if(realY >= transitionBoundary) {
                noiseThreshold *= 1.0F + 0.3F * ((float)(realY - transitionBoundary) / (float)(topY - transitionBoundary));
            }
            thresholds[realY - bottomY] = noiseThreshold;
        }
        return thresholds;
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
        return this.bottomY;
    }

    public int getTopY() {
        return this.topY;
    }
}