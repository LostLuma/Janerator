package dev.pixirora.janerator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import dev.pixirora.janerator.worldgen.GeneratorFinder;
import dev.pixirora.janerator.worldgen.MultiGenerator;

public class Janerator {
    private static MinecraftServer server = null;
    public static final Logger LOGGER = LoggerFactory.getLogger("Janerator");
    private static Map<ResourceKey<Level>, ChunkGenerator> generators = Maps.newHashMapWithExpectedSize(3);

    private static double phi = (1.0 + Math.sqrt(5)) / 2.0;
    private static double log10 = Math.log(10);

    public static boolean shouldOverride(double x, double z) {
        x += 0.01; // We do this because at X=0, the formula can never be greater than 0 creating a line of false
                   // Example at https://www.desmos.com/calculator/vsxlf0u9mt

        double distanceSquared = Math.pow(x, 2) + Math.pow(z, 2);
        double logDistanceSquared = Math.log(distanceSquared) / log10;

        double base = Math.max(Math.min(phi, 0.8 + 2 * 3.17 / logDistanceSquared), 1.5);

        double angle = Math.PI / 4 * Math.log(distanceSquared + Math.pow(phi, 2.0)) / Math.log(base) - Math.PI;
        double tanAngle = Math.tan(angle);

        double spiralResult = x * tanAngle - z;

        return (Math.sqrt(distanceSquared) - Math.abs(spiralResult)) * Math.signum(x) < 0;
    }

    public static int normalize(int value, int divisor) {
        return value - divisor * Math.floorDiv(value, divisor);
    }

    public static int toListCoordinate(int x, int z, int divisor) {
        return divisor * normalize(x, divisor) + normalize(z, divisor);
    }

    public static int toListCoordinate(int x, int z) {
        return toListCoordinate(x, z, 16);
    }

    public static boolean shouldOverride(ChunkPos chunkPos) {
        return shouldOverride(chunkPos.x*16, chunkPos.z*16);
    }

    public static boolean shouldOverride(BlockPos pos) {
        return shouldOverride(pos.getX(), pos.getZ());
    }

    public static void setMinecraftServer(MinecraftServer server) {
        Janerator.server = server;
    }

    public static synchronized ChunkGenerator getGenerator(ResourceKey<Level> dimension) {
        if (Janerator.generators.isEmpty()) {
            initializeGenerators();
        }

        ChunkGenerator generator = Janerator.generators.get(dimension);
        return generator != null ? generator : Janerator.generators.get(Level.OVERWORLD);
    }

    public static ChunkGenerator getGeneratorAt(
        ResourceKey<Level> dimension,
        ChunkGenerator defaultGenerator,
        ChunkAccess chunk
    ) {
        if (chunk instanceof LevelChunk || chunk instanceof ImposterProtoChunk) {
            return defaultGenerator;
        }

        ChunkGenerator modifiedGenerator = Janerator.getGenerator(dimension);

        GeneratorFinder generators = new GeneratorFinder(defaultGenerator, modifiedGenerator, chunk);

        if (generators.size() > 1) {
            return new MultiGenerator(modifiedGenerator.getBiomeSource(), generators);
        } else {
            return generators.getDefault();
        }
    }

    public static <T> Registry<T> getRegistry(ResourceKey<? extends Registry<? extends T>> registryKey) {
        return Janerator.server.registryAccess().registryOrThrow(registryKey);
    }

    private static void initializeGenerators() {
        Janerator.generators.put(Level.OVERWORLD, createOverworldGenerator());
        Janerator.generators.put(Level.NETHER, createNetherGenerator());
        Janerator.generators.put(Level.END, createEndGenerator());
    }

    public static void cleanup() {
        Janerator.generators.clear();
    }

    private static FlatLevelSource createOverworldGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));
        layers.add(new FlatLayerInfo(63, Blocks.DEEPSLATE));

        layers.add(new FlatLayerInfo(60, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return createGenerator(layers, Biomes.MUSHROOM_FIELDS);
    }

    private static FlatLevelSource createNetherGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));

        layers.add(new FlatLayerInfo(30, Blocks.NETHERRACK));
        layers.add(new FlatLayerInfo(1, Blocks.WARPED_NYLIUM));

        return createGenerator(layers, Biomes.DEEP_DARK);
    }

    private static FlatLevelSource createEndGenerator() {
        List<FlatLayerInfo> layers = new ArrayList<>();

        layers.add(new FlatLayerInfo(1, Blocks.BEDROCK));

        layers.add(new FlatLayerInfo(59, Blocks.STONE));
        layers.add(new FlatLayerInfo(2, Blocks.DIRT));
        layers.add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));

        return createGenerator(layers, Biomes.DEEP_DARK);
    }

    private static FlatLevelSource createGenerator(List<FlatLayerInfo> layers, ResourceKey<Biome> biome) {
        List<Holder<PlacedFeature>> placedFeatures = List.of();
        Optional<HolderSet<StructureSet>> optional = Optional.of(HolderSet.direct());

        Holder<Biome> biomeHolder = Janerator.getRegistry(Registries.BIOME).getHolderOrThrow(biome);
        return new FlatLevelSource(new FlatLevelGeneratorSettings(optional, biomeHolder, placedFeatures)
                .withBiomeAndLayers(layers, optional, biomeHolder));
    }
}
