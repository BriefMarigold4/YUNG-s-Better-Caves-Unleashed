package com.yungnickyoung.minecraft.bettercaves.util.bettercaves;

/**
 * Modified from https://github.com/YUNG-GANG/YUNGs-Better-Caves/blob/1.12.2/src/main/java/com/yungnickyoung/minecraft/bettercaves/noise/NoiseColumn.java licensed LGPLv3
 * Modified to improve performance (Fix unneeded boxing/lists/hashing/etc)
 */
public class NoiseColumnNew {
	
	private final NoiseTupleNew[] columnValuesArray;
	private final int minHeight;
	
	public NoiseColumnNew(int minHeight, int maxHeight) {
		this.minHeight = minHeight;
		this.columnValuesArray = new NoiseTupleNew[Math.max(maxHeight - minHeight + 1, 0)];
	}
	
	public void put(int y, NoiseTupleNew noiseTuple) {
		this.columnValuesArray[y - this.minHeight] = noiseTuple;
	}
	
	public NoiseTupleNew get(int y) {
		return this.columnValuesArray[y - this.minHeight];
	}
}