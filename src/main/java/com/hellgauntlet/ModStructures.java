package com.hellgauntlet;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, HellGauntletMod.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<HellSkullStructure>> HELLSKULL =
            STRUCTURE_TYPES.register("hellskull", () -> typeOf(HellSkullStructure.CODEC));

    private static <T extends Structure> StructureType<T> typeOf(MapCodec<T> codec) {
        return () -> codec;
    }
}
