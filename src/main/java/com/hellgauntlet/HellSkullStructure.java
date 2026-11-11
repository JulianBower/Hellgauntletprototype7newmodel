package com.hellgauntlet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.List;
import java.util.Optional;

/**
 * A jigsaw structure that first looks for real open ground. The Nether has no usable heightmap (it all
 * points at the bedrock roof), so a random height put the skull inside basalt pillars. This scans the
 * terrain column for a floor with free air above it and a roughly flat surroundings, and only then
 * hands over to the normal jigsaw placement at that height. If no spot is found the chunk gets nothing.
 */
public class HellSkullStructure extends Structure {
    public static final MapCodec<HellSkullStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    settingsCodec(instance),
                    StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
                    Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.size),
                    Codec.intRange(0, 255).fieldOf("search_min_y").forGetter(s -> s.searchMinY),
                    Codec.intRange(0, 255).fieldOf("search_max_y").forGetter(s -> s.searchMaxY),
                    Codec.intRange(1, 64).fieldOf("clearance").forGetter(s -> s.clearance)
            ).apply(instance, HellSkullStructure::new));

    private final Structure.StructureSettings baseSettings;
    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final int searchMinY;
    private final int searchMaxY;
    private final int clearance;

    public HellSkullStructure(Structure.StructureSettings settings, Holder<StructureTemplatePool> startPool,
                              int size, int searchMinY, int searchMaxY, int clearance) {
        super(settings);
        this.baseSettings = settings;
        this.startPool = startPool;
        this.size = size;
        this.searchMinY = searchMinY;
        this.searchMaxY = searchMaxY;
        this.clearance = clearance;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int minY = Math.max(this.searchMinY, context.chunkGenerator().getSeaLevel() + 1);
        int maxY = Math.min(this.searchMaxY, context.heightAccessor().getMaxBuildHeight() - this.clearance - 1);

        for (int attempt = 0; attempt < 16; attempt++) {
            int x = chunk.getMiddleBlockX() + context.random().nextInt(9) - 4;
            int z = chunk.getMiddleBlockZ() + context.random().nextInt(9) - 4;
            int y = findFloor(context, x, z, minY, maxY);
            if (y < 0) {
                continue;
            }
            // flat enough around: ground below and open air above at four spots 8 blocks away
            boolean ok = true;
            int[][] offsets = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}};
            for (int[] o : offsets) {
                if (!isFloor(context, x + o[0], z + o[1], y, 4)) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            JigsawStructure jigsaw = new JigsawStructure(this.baseSettings, this.startPool, Optional.empty(), this.size,
                    ConstantHeight.of(VerticalAnchor.absolute(y)), false, Optional.empty(), 80, List.of(),
                    JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
            return jigsaw.findGenerationPoint(context);
        }
        return Optional.empty();
    }

    /** Lowest y in range with solid ground just below and enough air above, or -1. */
    private int findFloor(GenerationContext context, int x, int z, int minY, int maxY) {
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        for (int y = minY; y <= maxY; y++) {
            if (solid(column.getBlock(y - 1)) && airAbove(column, y, this.clearance)) {
                return y;
            }
        }
        return -1;
    }

    /** Ground right under y (within 3 blocks lower) and at least `air` free blocks above y. */
    private boolean isFloor(GenerationContext context, int x, int z, int y, int air) {
        NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
        boolean ground = false;
        for (int dy = 0; dy <= 3; dy++) {
            if (solid(column.getBlock(y - 1 - dy))) {
                ground = true;
                break;
            }
        }
        return ground && airAbove(column, y, air);
    }

    private static boolean airAbove(NoiseColumn column, int y, int count) {
        for (int dy = 0; dy < count; dy++) {
            BlockState state = column.getBlock(y + dy);
            if (!state.isAir()) {
                return false;
            }
        }
        return true;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HELLSKULL.get();
    }
}
