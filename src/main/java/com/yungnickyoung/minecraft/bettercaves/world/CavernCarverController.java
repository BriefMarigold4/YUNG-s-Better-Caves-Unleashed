package com.yungnickyoung.minecraft.bettercaves.world;

import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.config.util.ConfigHolder;
import com.yungnickyoung.minecraft.bettercaves.enums.CavernType;
import com.yungnickyoung.minecraft.bettercaves.enums.RegionSize;
import com.yungnickyoung.minecraft.bettercaves.noise.FastNoise;
import com.yungnickyoung.minecraft.bettercaves.noise.NoiseUtils;
import com.yungnickyoung.minecraft.bettercaves.util.BetterCavesUtils;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverNoiseRange;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cavern.CavernCarverBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.common.BiomeDictionary;

import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseColumnNew;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseCubeNew;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CavernCarverController {
    private World world;
    private FastNoise cavernRegionController;
    private List<CarverNoiseRange> noiseRanges = new ArrayList<>();

    // Vars from config
    private boolean isDebugViewEnabled;
    private boolean isOverrideSurfaceDetectionEnabled;
    private boolean isFloodedUndergroundEnabled;

    // Equality checking functions used for closing off flooded caves
    private static Predicate<Biome> isOcean = b -> BiomeDictionary.hasType(b, BiomeDictionary.Type.OCEAN);
    private static Predicate<Biome> isNotOcean = b -> !isOcean.test(b);

    public CavernCarverController(World worldIn, ConfigHolder config) {
        this.world = worldIn;
        this.isDebugViewEnabled = config.debugVisualizer.get();
        this.isOverrideSurfaceDetectionEnabled = config.overrideSurfaceDetection.get();
        this.isFloodedUndergroundEnabled = config.enableFloodedUnderground.get();

        // Configure cavern region controller, which determines what type of cavern should be carved in any given region
        float cavernRegionSize = calcCavernRegionSize(config.cavernRegionSize.get(), config.cavernRegionCustomSize.get());
        this.cavernRegionController = new FastNoise();
        this.cavernRegionController.SetSeed((int)worldIn.getSeed() + 333);
        this.cavernRegionController.SetFrequency(cavernRegionSize);

        // Initialize all carvers using config options
        List<CavernCarver> carvers = new ArrayList<>();
        carvers.add(new CavernCarverBuilder(worldIn)
            .ofTypeFromConfig(CavernType.LIQUID, config)
            .debugVisualizerBlock(Blocks.REDSTONE_BLOCK.getDefaultState())
            .build()
        );
        carvers.add(new CavernCarverBuilder(worldIn)
            .ofTypeFromConfig(CavernType.FLOORED, config)
            .debugVisualizerBlock(Blocks.GOLD_BLOCK.getDefaultState())
            .build()
        );

        float spawnChance = config.cavernSpawnChance.get() / 100f;
        int totalPriority = carvers.stream().map(CavernCarver::getPriority).reduce(0, Integer::sum);

        BetterCaves.LOGGER.debug("CAVERN INFORMATION");
        BetterCaves.LOGGER.debug("--> SPAWN CHANCE SET TO: " + spawnChance);
        BetterCaves.LOGGER.debug("--> TOTAL PRIORITY: " + totalPriority);

        carvers.removeIf(carver -> carver.getPriority() == 0);
        float totalDeadzonePercent = 1 - spawnChance;
        float deadzonePercent = carvers.size() > 1
                ? totalDeadzonePercent / (carvers.size() - 1)
                : totalDeadzonePercent;

        BetterCaves.LOGGER.debug("--> DEADZONE PERCENT: " + deadzonePercent + "(" + totalDeadzonePercent + " TOTAL)");

        float currNoise = -1f;

        for (CavernCarver carver : carvers) {
            BetterCaves.LOGGER.debug("--> CARVER");
            float rangeCDFPercent = (float)carver.getPriority() / totalPriority * spawnChance;
            float topNoise = NoiseUtils.simplexNoiseOffsetByPercent(currNoise, rangeCDFPercent);
            CarverNoiseRange range = new CarverNoiseRange(currNoise, topNoise, carver);
            noiseRanges.add(range);

            // Offset currNoise for deadzone region
            currNoise = NoiseUtils.simplexNoiseOffsetByPercent(topNoise, deadzonePercent);

            BetterCaves.LOGGER.debug("    --> RANGE PERCENT LENGTH WANTED: " + rangeCDFPercent);
            BetterCaves.LOGGER.debug("    --> RANGE FOUND: " + range);
        }
    }

    public void carveChunk(ChunkPrimer primer, int chunkX, int chunkZ, int[][] surfaceAltitudes, IBlockState[][] liquidBlocks) {
        if(this.noiseRanges.isEmpty()) return;
        for(int subX = 0; subX < 4; subX++) {
            for(int subZ = 0; subZ < 4; subZ++) {
                int startX = subX * 4;
                int startZ = subZ * 4;
                int endX = startX + 4 - 1;
                int endZ = startZ + 4 - 1;
                int startPosX = chunkX * 16 + startX;
                int startPosZ = chunkZ * 16 + startZ;
                int endPosX = chunkX * 16 + endX;
                int endPosZ = chunkZ * 16 + endZ;
                int maxHeight = 0;
                if(!this.isOverrideSurfaceDetectionEnabled) {
                    for(int x = startX; x < endX; x++) {
                        for(int z = startZ; z < endZ; z++) {
                            maxHeight = Math.max(maxHeight, surfaceAltitudes[x][z]);
                        }
                    }
                    for(CarverNoiseRange range : this.noiseRanges) {
                        maxHeight = Math.max(maxHeight, range.getCarver().getTopY());
                    }
                }
                //NoiseCube isn't actually used outside of this section of iteration, so does not need to be stored in noiseRanges
                NoiseCubeNew[] noiseCubes = new NoiseCubeNew[this.noiseRanges.size()];
                for(int offsetX = 0; offsetX < 4; offsetX++) {
                    for(int offsetZ = 0; offsetZ < 4; offsetZ++) {
                        int localX = startX + offsetX;
                        int localZ = startZ + offsetZ;
                        BlockPos colPos = new BlockPos(chunkX * 16 + localX, 1, chunkZ * 16 + localZ);
                        boolean flooded = false;
                        float smoothAmpFactor = 1;
                        if(this.isFloodedUndergroundEnabled && !this.isDebugViewEnabled) {
                            flooded = BiomeDictionary.hasType(this.world.getBiome(colPos), BiomeDictionary.Type.OCEAN);
                            smoothAmpFactor = BetterCavesUtils.biomeDistanceFactor(this.world, colPos, 2, flooded ? isNotOcean : isOcean);
                            if(smoothAmpFactor <= 0) continue;
                        }
                        int surfaceAltitude = surfaceAltitudes[localX][localZ];
                        IBlockState liquidBlock = liquidBlocks[localX][localZ];
                        float cavernRegionNoise = this.cavernRegionController.GetNoise(colPos.getX(), colPos.getZ());
                        for(int rangeIndex = 0; rangeIndex < this.noiseRanges.size(); rangeIndex++) {
                            CarverNoiseRange range = this.noiseRanges.get(rangeIndex);
                            if(range.contains(cavernRegionNoise)) {
                                CavernCarver carver = (CavernCarver)range.getCarver();
                                int bottomY = carver.getBottomY();
                                int topY = this.isDebugViewEnabled ? carver.getTopY() : Math.min(surfaceAltitude, carver.getTopY());
                                if(this.isOverrideSurfaceDetectionEnabled) {
                                    topY = carver.getTopY();
                                    maxHeight = carver.getTopY();
                                }
                                float smoothAmp = range.getSmoothAmp(cavernRegionNoise) * smoothAmpFactor;
                                if(noiseCubes[rangeIndex] == null) {
                                    noiseCubes[rangeIndex] = carver.getNoiseGenNew().interpolateNoiseCube(startPosX, startPosZ, endPosX, endPosZ, bottomY, maxHeight);
                                }
                                NoiseColumnNew noiseColumn = noiseCubes[rangeIndex].getArray(offsetX)[offsetZ];
                                carver.carveColumnNew(primer, colPos, topY, smoothAmp, noiseColumn, liquidBlock, flooded);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * @return frequency value for cavern region controller
     */
    private float calcCavernRegionSize(RegionSize cavernRegionSize, float cavernRegionCustomSize) {
        switch (cavernRegionSize) {
            case Small:
                return .01f;
            case Large:
                return .005f;
            case ExtraLarge:
                return .001f;
            case Custom:
                return cavernRegionCustomSize;
            default: // Medium
                return .007f;
        }
    }
}
