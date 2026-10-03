package com.yungnickyoung.minecraft.bettercaves.world;

import com.yungnickyoung.minecraft.bettercaves.BetterCaves;
import com.yungnickyoung.minecraft.bettercaves.config.util.ConfigHolder;
import com.yungnickyoung.minecraft.bettercaves.enums.CaveType;
import com.yungnickyoung.minecraft.bettercaves.enums.RegionSize;
import com.yungnickyoung.minecraft.bettercaves.noise.FastNoise;
import com.yungnickyoung.minecraft.bettercaves.world.carver.CarverNoiseRange;
import com.yungnickyoung.minecraft.bettercaves.world.carver.ICarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cave.CaveCarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.cave.CaveCarverBuilder;
import com.yungnickyoung.minecraft.bettercaves.world.carver.vanilla.VanillaCaveCarver;
import com.yungnickyoung.minecraft.bettercaves.world.carver.vanilla.VanillaCaveCarverBuilder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.common.BiomeDictionary;
import com.yungnickyoung.minecraft.bettercaves.config.Configuration;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.ColumnCarverHolder;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseColumnNew;
import com.yungnickyoung.minecraft.bettercaves.util.bettercaves.NoiseCubeNew;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class CaveCarverController {
    private World world;
    private VanillaCaveCarver surfaceCaveCarver; // only used if surface caves enabled
    private FastNoise caveRegionController;
    private List<CarverNoiseRange> noiseRanges = new ArrayList<>();

    // Vars from config
    private boolean isDebugViewEnabled;
    private boolean isOverrideSurfaceDetectionEnabled;
    private boolean isSurfaceCavesEnabled;
    private boolean isFloodedUndergroundEnabled;

    private boolean shouldCarveVanillaCaves = false;
    private boolean[][] vanillaCarvingMask = null;
    private ColumnCarverHolder[][][][] columnCarverHolders = null;

    public CaveCarverController(World worldIn, ConfigHolder config) {
        this.world = worldIn;
        this.isDebugViewEnabled = config.debugVisualizer.get();
        this.isOverrideSurfaceDetectionEnabled = config.overrideSurfaceDetection.get();
        this.isSurfaceCavesEnabled = config.isSurfaceCavesEnabled.get();
        this.isFloodedUndergroundEnabled = config.enableFloodedUnderground.get();
        this.surfaceCaveCarver = new VanillaCaveCarverBuilder()
            .bottomY(config.surfaceCaveBottom.get())
            .topY(config.surfaceCaveTop.get())
            .density(config.surfaceCaveDensity.get())
            .liquidAltitude(config.liquidAltitude.get())
            .replaceGravel(config.replaceFloatingGravel.get())
            .floodedUnderground(config.enableFloodedUnderground.get())
            .debugVisualizerEnabled(config.debugVisualizer.get())
            .debugVisualizerBlock(Blocks.EMERALD_BLOCK.getDefaultState())
            .build();

        // Configure cave region controller, which determines what type of cave should be
        // carved in any given region
        float caveRegionSize = calcCaveRegionSize(config.caveRegionSize.get(), config.caveRegionCustomSize.get());
        this.caveRegionController = new FastNoise();
        this.caveRegionController.SetSeed((int)worldIn.getSeed() + 222);
        this.caveRegionController.SetFrequency(caveRegionSize);
        this.caveRegionController.SetNoiseType(FastNoise.NoiseType.Cellular);
        this.caveRegionController.SetCellularDistanceFunction(FastNoise.CellularDistanceFunction.Natural);

        // Initialize all carvers using config options
        List<ICarver> carvers = new ArrayList<>();
        // Type 1 caves
        carvers.add(new CaveCarverBuilder(worldIn)
            .ofTypeFromConfig(CaveType.CUBIC, config)
            .debugVisualizerBlock(Blocks.PLANKS.getDefaultState())
            .build()
        );
        // Type 2 caves
        carvers.add(new CaveCarverBuilder(worldIn)
            .ofTypeFromConfig(CaveType.SIMPLEX, config)
            .debugVisualizerBlock(Blocks.COBBLESTONE.getDefaultState())
            .build()
        );
        // Vanilla caves
        carvers.add(new VanillaCaveCarverBuilder()
            .bottomY(config.vanillaCaveBottom.get())
            .topY(config.vanillaCaveTop.get())
            .density(config.vanillaCaveDensity.get())
            .priority(config.vanillaCavePriority.get())
            .liquidAltitude(config.liquidAltitude.get())
            .replaceGravel(config.replaceFloatingGravel.get())
            .floodedUnderground(config.enableFloodedUnderground.get())
            .debugVisualizerEnabled(config.debugVisualizer.get())
            .debugVisualizerBlock(Blocks.BRICK_BLOCK.getDefaultState())
            .build());

        // Remove carvers with no priority
        carvers.removeIf(carver -> carver.getPriority() == 0);

        // Initialize vars for calculating controller noise thresholds
        float maxPossibleNoiseThreshold = config.caveSpawnChance.get() * .01f * 2 - 1;
        int totalPriority = carvers.stream().map(ICarver::getPriority).reduce(0, Integer::sum);
        float totalRangeLength = maxPossibleNoiseThreshold - -1f;
        float currNoise = -1f;

        BetterCaves.LOGGER.debug("CAVE INFORMATION");
        BetterCaves.LOGGER.debug("--> MAX POSSIBLE THRESHOLD: " + maxPossibleNoiseThreshold);
        BetterCaves.LOGGER.debug("--> TOTAL PRIORITY: " + totalPriority);
        BetterCaves.LOGGER.debug("--> TOTAL RANGE LENGTH: " + totalRangeLength);

        for (ICarver carver : carvers) {
            BetterCaves.LOGGER.debug("--> CARVER");
            float noiseRangeLength = (float)carver.getPriority() / totalPriority * totalRangeLength;
            float rangeTop = currNoise + noiseRangeLength;
            CarverNoiseRange range = new CarverNoiseRange(currNoise, rangeTop, carver);
            currNoise = rangeTop;
            noiseRanges.add(range);

            BetterCaves.LOGGER.debug("    --> RANGE FOUND: " + range);
        }
    }

    public void carveChunk(ChunkPrimer primer, int chunkX, int chunkZ, int[][] surfaceAltitudes, IBlockState[][] liquidBlocks) {
        if(noiseRanges.isEmpty() && !isSurfaceCavesEnabled) return;

        if(!Configuration.multithreadSettings.multithreadBetterCavesNoise) {
            //Allow for just utilizing the improved performance of the rewrite without multithreading
            this.carveChunkOriginal(primer, chunkX, chunkZ, surfaceAltitudes, liquidBlocks);
            return;
        }

        this.shouldCarveVanillaCaves = false;
        this.vanillaCarvingMask = new boolean[16][16];
        this.columnCarverHolders = new ColumnCarverHolder[4][4][4][4];

        try {
            //Last 16 pos iterations rely on the same noisecube based on order, so group them to be handled in the same thread each
            IntStream.range(0, 16).parallel().forEach(subIndex -> this.genChunkCarverSection(chunkX, chunkZ, surfaceAltitudes, liquidBlocks, subIndex/4, subIndex%4));
        }
        catch(Exception ex) {
            BetterCaves.LOGGER.log(Level.ERROR, "BetterCaves Multithreaded Noise encountered an error: " + ex.getMessage(), ex);
            //Just run the original carving instead, since nothing would have actually been carved yet here
            this.carveChunkOriginal(primer, chunkX, chunkZ, surfaceAltitudes, liquidBlocks);
            this.vanillaCarvingMask = null;
            this.columnCarverHolders = null;
            return;
        }

        //Gross and can probably be better but im tired
        for(int subX = 0; subX < 4; subX++) {
            for(int subZ = 0; subZ < 4; subZ++) {
                for(int offsetX = 0; offsetX < 4; offsetX++) {
                    for(int offsetZ = 0; offsetZ < 4; offsetZ++) {
                        //Don't need to iterate noiseRanges as only one is ever selected due to break
                        ColumnCarverHolder columnCarverHolder = this.columnCarverHolders[subX][subZ][offsetX][offsetZ];
                        if(columnCarverHolder == null) continue;
                        //Reconvene and carve in correct order just incase
                        ((CaveCarver)this.noiseRanges.get(columnCarverHolder.carverIndex).getCarver())
                                .carveColumnNew(primer,
                                        columnCarverHolder.colPos,
                                        columnCarverHolder.topY,
                                        columnCarverHolder.noiseColumn,
                                        columnCarverHolder.liquidBlock,
                                        columnCarverHolder.flooded);
                    }
                }
            }
        }

        //Vanilla carving should be effectively the same
        if(this.shouldCarveVanillaCaves) {
            VanillaCaveCarver carver = null;
            for(CarverNoiseRange range : this.noiseRanges) {
                if(range.getCarver() instanceof VanillaCaveCarver) {
                    carver = (VanillaCaveCarver)range.getCarver();
                    break;
                }
            }
            if(carver != null) {
                carver.generate(this.world, chunkX, chunkZ, primer, true, liquidBlocks, this.vanillaCarvingMask);
            }
        }
        if(this.isSurfaceCavesEnabled) {
            this.surfaceCaveCarver.generate(this.world, chunkX, chunkZ, primer, false, liquidBlocks);
        }

        this.vanillaCarvingMask = null;
        this.columnCarverHolders = null;
    }

    /**
     * @return frequency value for cave region controller
     */
    private float calcCaveRegionSize(RegionSize caveRegionSize, float caveRegionCustomSize) {
        switch (caveRegionSize) {
            case Small:
                return .008f;
            case Large:
                return .0032f;
            case ExtraLarge:
                return .001f;
            case Custom:
                return caveRegionCustomSize;
            default: // Medium
                return .005f;
        }
    }

    private void genChunkCarverSection(int chunkX, int chunkZ, int[][] surfaceAltitudes, IBlockState[][] liquidBlocks, int subX, int subZ) {
        int startX = subX * 4;
        int startZ = subZ * 4;
        int endX = startX + 4 - 1;
        int endZ = startZ + 4 - 1;
        int startPosX = chunkX * 16 + startX;
        int startPosZ = chunkZ * 16 + startZ;
        int endPosX = chunkX * 16 + endX;
        int endPosZ = chunkZ * 16 + endZ;

        int maxHeight = 0;
        if(!isOverrideSurfaceDetectionEnabled) {
            for(int x = startX; x < endX; x++) {
                for(int z = startZ; z < endZ; z++) {
                    maxHeight = Math.max(maxHeight, surfaceAltitudes[x][z]);
                }
            }
            for(CarverNoiseRange range : noiseRanges) {
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
                boolean flooded = isFloodedUndergroundEnabled && !isDebugViewEnabled && BiomeDictionary.hasType(world.getBiome(colPos), BiomeDictionary.Type.OCEAN);
                if(flooded) {
                    if(!BiomeDictionary.hasType(world.getBiome(colPos.east()), BiomeDictionary.Type.OCEAN) ||
                            !BiomeDictionary.hasType(world.getBiome(colPos.north()), BiomeDictionary.Type.OCEAN) ||
                            !BiomeDictionary.hasType(world.getBiome(colPos.west()), BiomeDictionary.Type.OCEAN) ||
                            !BiomeDictionary.hasType(world.getBiome(colPos.south()), BiomeDictionary.Type.OCEAN)
                    ) continue;
                }

                int surfaceAltitude = surfaceAltitudes[localX][localZ];
                IBlockState liquidBlock = liquidBlocks[localX][localZ];

                float caveRegionNoise = caveRegionController.GetNoise(colPos.getX(), colPos.getZ());
                for(int rangeIndex = 0; rangeIndex < this.noiseRanges.size(); rangeIndex++) {
                    CarverNoiseRange range = this.noiseRanges.get(rangeIndex);
                    if(!range.contains(caveRegionNoise)) continue;
                    if(range.getCarver() instanceof CaveCarver) {
                        CaveCarver carver = (CaveCarver)range.getCarver();
                        int bottomY = carver.getBottomY();
                        int topY = Math.min(surfaceAltitude, carver.getTopY());
                        if(this.isOverrideSurfaceDetectionEnabled) {
                            topY = carver.getTopY();
                            maxHeight = carver.getTopY();
                        }
                        if(this.isDebugViewEnabled) {
                            topY = 128;
                            maxHeight = 128;
                        }
                        if(noiseCubes[rangeIndex] == null) {
                            noiseCubes[rangeIndex] = carver.getNoiseGenNew().interpolateNoiseCube(startPosX, startPosZ, endPosX, endPosZ, bottomY, maxHeight);
                        }
                        NoiseColumnNew noiseColumn = noiseCubes[rangeIndex].getArray(offsetX)[offsetZ];
                        //Store the needed data for carving, only carve after multithreading finishes
                        this.columnCarverHolders[subX][subZ][offsetX][offsetZ] = new ColumnCarverHolder(rangeIndex, colPos, topY, noiseColumn, liquidBlock, flooded);
                        break;
                    }
                    else if(range.getCarver() instanceof VanillaCaveCarver) {
                        this.shouldCarveVanillaCaves = true;
                        this.vanillaCarvingMask[localX][localZ] = true;
                    }
                }
            }
        }
    }

    private void carveChunkOriginal(ChunkPrimer primer, int chunkX, int chunkZ, int[][] surfaceAltitudes, IBlockState[][] liquidBlocks) {
        if(noiseRanges.isEmpty() && !isSurfaceCavesEnabled) return;

        boolean shouldCarveVanillaCaves = false;
        boolean[][] vanillaCarvingMask = new boolean[16][16];

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
                if(!isOverrideSurfaceDetectionEnabled) {
                    for(int x = startX; x < endX; x++) {
                        for(int z = startZ; z < endZ; z++) {
                            maxHeight = Math.max(maxHeight, surfaceAltitudes[x][z]);
                        }
                    }
                    for(CarverNoiseRange range : noiseRanges) {
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
                        boolean flooded = this.isFloodedUndergroundEnabled && !this.isDebugViewEnabled && BiomeDictionary.hasType(this.world.getBiome(colPos), BiomeDictionary.Type.OCEAN);
                        if(!flooded || BiomeDictionary.hasType(this.world.getBiome(colPos.east()), BiomeDictionary.Type.OCEAN) && BiomeDictionary.hasType(this.world.getBiome(colPos.north()), BiomeDictionary.Type.OCEAN) && BiomeDictionary.hasType(this.world.getBiome(colPos.west()), BiomeDictionary.Type.OCEAN) && BiomeDictionary.hasType(this.world.getBiome(colPos.south()), BiomeDictionary.Type.OCEAN)) {
                            int surfaceAltitude = surfaceAltitudes[localX][localZ];
                            IBlockState liquidBlock = liquidBlocks[localX][localZ];
                            float caveRegionNoise = this.caveRegionController.GetNoise((float)colPos.getX(), (float)colPos.getZ());
                            for(int rangeIndex = 0; rangeIndex < this.noiseRanges.size(); rangeIndex++) {
                                CarverNoiseRange range = this.noiseRanges.get(rangeIndex);
                                if(range.contains(caveRegionNoise)) {
                                    if(range.getCarver() instanceof CaveCarver) {
                                        CaveCarver carver = (CaveCarver)range.getCarver();
                                        int bottomY = carver.getBottomY();
                                        int topY = Math.min(surfaceAltitude, carver.getTopY());
                                        if(this.isOverrideSurfaceDetectionEnabled) {
                                            topY = carver.getTopY();
                                            maxHeight = carver.getTopY();
                                        }
                                        if(this.isDebugViewEnabled) {
                                            topY = 128;
                                            maxHeight = 128;
                                        }
                                        if(noiseCubes[rangeIndex] == null) {
                                            noiseCubes[rangeIndex] = carver.getNoiseGenNew().interpolateNoiseCube(startPosX, startPosZ, endPosX, endPosZ, bottomY, maxHeight);
                                        }
                                        NoiseColumnNew noiseColumn = noiseCubes[rangeIndex].getArray(offsetX)[offsetZ];
                                        carver.carveColumnNew(primer, colPos, topY, noiseColumn, liquidBlock, flooded);
                                        break;
                                    }
                                    if(range.getCarver() instanceof VanillaCaveCarver) {
                                        vanillaCarvingMask[localX][localZ] = true;
                                        shouldCarveVanillaCaves = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if(shouldCarveVanillaCaves) {
            VanillaCaveCarver carver = null;
            for(CarverNoiseRange range : this.noiseRanges) {
                if(range.getCarver() instanceof VanillaCaveCarver) {
                    carver = (VanillaCaveCarver)range.getCarver();
                    break;
                }
            }
            if(carver != null) {
                carver.generate(this.world, chunkX, chunkZ, primer, true, liquidBlocks, vanillaCarvingMask);
            }
        }
        if(this.isSurfaceCavesEnabled) {
            this.surfaceCaveCarver.generate(this.world, chunkX, chunkZ, primer, false, liquidBlocks);
        }
    }
}
