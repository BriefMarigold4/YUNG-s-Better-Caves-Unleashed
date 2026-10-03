package com.yungnickyoung.minecraft.bettercaves.config;

import net.minecraftforge.common.config.Config;

public class ConfigMultithreading {
	
	@Config.Comment("Minimum size of the available common thread pool to run multithreading")
	@Config.Name("Thread Pool Minimum Size")
	@Config.RangeInt(min = 1)
	public int threadPoolMinimumSize = 4;
		
	@Config.Comment("If Better Caves should multithread noise generation")
	@Config.Name("Multithread BetterCaves Noise Generation")
	public boolean multithreadBetterCavesNoise = true;
}