package dev.pixirora.janerator.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.core.HolderSet;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;

@Mixin(ChunkStatus.class)
public class ChunkStatusMixin {
    private static final FlatLevelSource generator = defaultSource(overworldLayers());
    private static final FlatLevelSource netherGenerator = defaultSource(netherLayers());
    private static final FlatLevelSource endGenerator = defaultSource(endLayers());

	@ModifyArgs(
        method = "generate(Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Z)Ljava/util/concurrent/CompletableFuture;", 
        at = @At(
            value="INVOKE",
            target="Lnet/minecraft/world/level/chunk/ChunkStatus$GenerationTask;doWork(Lnet/minecraft/world/level/chunk/ChunkStatus;Ljava/util/concurrent/Executor;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/server/level/ThreadedLevelLightEngine;Ljava/util/function/Function;Ljava/util/List;Lnet/minecraft/world/level/chunk/ChunkAccess;Z)Ljava/util/concurrent/CompletableFuture;"
            )
        )
	public void overrideGeneratorType(Args args) {
		ChunkAccess chunkAccess = args.get(8);
        ServerLevel serverLevel = args.get(2);

        if (chunkAccess.getPos().getRegionX() >= 0) {
            ResourceKey<Level> dimension = serverLevel.dimension();

            if (dimension == Level.OVERWORLD) {
                args.set(3, generator);
            } else if (dimension == Level.NETHER) {
                args.set(3, netherGenerator);
            } else if (dimension == Level.END) {
                args.set(3, endGenerator);
            }
        }
	}

    private static List<FlatLayerInfo> overworldLayers() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(63, Blocks.DEEPSLATE));

        layers.add(new FlatLayerInfo(60, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return layers;
    }

    private static List<FlatLayerInfo> netherLayers() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(30, Blocks.NETHERRACK));
        layers.add(new FlatLayerInfo(1, Blocks.WARPED_NYLIUM));

        return layers;
    }

    private static List<FlatLayerInfo> endLayers() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));

        layers.add(new FlatLayerInfo(59, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return layers;
    }

    private static FlatLevelSource defaultSource(List<FlatLayerInfo> layers) {
        Optional<HolderSet<StructureSet>> optional = Optional.empty();
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(optional, BuiltinRegistries.BIOME).withLayers(layers, optional);

        ResourceKey<Biome> biomeResourceKey = Biomes.MUSHROOM_FIELDS;
        settings.setBiome(BuiltinRegistries.BIOME.getOrCreateHolderOrThrow(biomeResourceKey));

        return new FlatLevelSource(BuiltinRegistries.STRUCTURE_SETS, settings);
    }
}
