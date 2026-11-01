package com.viaversion.viafabricplus.features.v1_18_2;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class PendingUpdateManager1_18_2 extends BlockStatePredictionHandler {

    @Override
    public void retainKnownServerState(BlockPos pos, BlockState state, LocalPlayer player) {
    }

    @Override
    public boolean updateKnownServerState(BlockPos pos, BlockState state) {
        return false;
    }

    @Override
    public void endPredictionsUpTo(int maxProcessableSequence, ClientLevel world) {
    }

    @Override
    public BlockStatePredictionHandler startPredicting() {
        return this;
    }

    @Override
    public void close() {
    }

    @Override
    public int currentSequence() {
        return 0;
    }

    @Override
    public boolean isPredicting() {
        return false;
    }

}
