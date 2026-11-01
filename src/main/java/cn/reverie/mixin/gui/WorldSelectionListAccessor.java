package cn.reverie.mixin.gui;

import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(WorldSelectionList.class)
public interface WorldSelectionListAccessor {

    @Invoker("pollLevelsIgnoreErrors")
    List<LevelSummary> nocturne$pollLevelsIgnoreErrors();

    @Invoker("handleNewLevels")
    void nocturne$handleNewLevels(List<LevelSummary> levels);
}
