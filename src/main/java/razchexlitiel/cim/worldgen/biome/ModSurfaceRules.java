package razchexlitiel.cim.worldgen.biome; // Поменяй на свой пакет

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import razchexlitiel.cim.block.basic.ModBlocks;
import razchexlitiel.cim.worldgen.biome.ModBiomes; // Твой класс с ключом биома

public class ModSurfaceRules {

    // Вспомогательный метод для удобства
    private static SurfaceRules.RuleSource makeStateRule(Block block) {
        return SurfaceRules.state(block.defaultBlockState());
    }

    public static SurfaceRules.RuleSource makeRules() {
        SurfaceRules.ConditionSource isSequoiaBiome = SurfaceRules.isBiome(ModBiomes.SEQUOIA_GROVE);

        // --- МАГИЯ ШУМА: Создаем реалистичные пятна на земле ---
        SurfaceRules.RuleSource groundMix = SurfaceRules.sequence(

                // От -1.0 до -0.3: Крупные полянки твоего кастомного мха/хвои
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -1.0D, -0.3D), makeStateRule(ModBlocks.SEQUOIA_BIOME_MOSS.get())),

                // От -0.3 до 0.2: Пятна ванильного мха
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -0.3D, 0.2D), makeStateRule(Blocks.MOSS_BLOCK)),

                // От 0.2 до 0.6: Островки подзола
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.2D, 0.6D), makeStateRule(Blocks.PODZOL)),

                // Всё остальное (фон) заливаем сочным дёрном!
                makeStateRule(Blocks.GRASS_BLOCK)

        );

        SurfaceRules.RuleSource sequoiaSurface = SurfaceRules.sequence(
                // Проверяем, что мы под открытым небом (чтобы не залить пещеры)
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(),
                        SurfaceRules.sequence(
                                // На самом полу используем наш "Шумный Микс"
                                SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, groundMix),
                                // Прямо под полом оставляем обычную землю
                                SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, makeStateRule(Blocks.DIRT))
                        )
                )
        );

        return SurfaceRules.sequence(
                SurfaceRules.ifTrue(isSequoiaBiome, sequoiaSurface)
        );
    }
}
