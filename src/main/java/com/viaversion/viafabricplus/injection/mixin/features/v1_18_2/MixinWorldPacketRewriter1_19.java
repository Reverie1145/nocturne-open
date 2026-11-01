package com.viaversion.viafabricplus.injection.mixin.features.v1_18_2;

import com.viaversion.viafabricplus.features.v1_18_2.BlockStateTranslator;
import com.viaversion.viafabricplus.injection.access.v1_18_2.IMultiPlayerGameMode;
import com.viaversion.viafabricplus.util.network.SyncTasks;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_17_1to1_18.packet.ClientboundPackets1_18;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.Protocol1_18_2To1_19;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ClientboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.rewriter.WorldPacketRewriter1_19;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WorldPacketRewriter1_19.class, remap = false)
public abstract class MixinWorldPacketRewriter1_19 {

    @Redirect(method = "register", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/protocols/v1_18_2to1_19/Protocol1_18_2To1_19;cancelClientbound(Lcom/viaversion/viaversion/api/protocol/packet/ClientboundPacketType;)V"))
    private static void handleLegacyAcknowledgePlayerDigging(Protocol1_18_2To1_19 instance, ClientboundPacketType clientboundPacketType) {
        instance.registerClientbound(ClientboundPackets1_18.BLOCK_BREAK_ACK, ClientboundPackets1_19.CUSTOM_PAYLOAD, wrapper -> {
            wrapper.resetReader();

            final String uuid = SyncTasks.executeSyncTask(data -> {
                try {
                    final BlockPos pos = data.readBlockPos();
                    final BlockState blockState = BlockStateTranslator.via1_18_2toMc(data.readVarInt());
                    final ServerboundPlayerActionPacket.Action action = data.readEnum(ServerboundPlayerActionPacket.Action.class);
                    final boolean allGood = data.readBoolean();

                    final IMultiPlayerGameMode mixinInteractionManager = (IMultiPlayerGameMode) Minecraft.getInstance().gameMode;
                    mixinInteractionManager.viaFabricPlus$get1_18_2InteractionManager().handleBlockBreakAck(pos, blockState, action, allGood);
                } catch (Throwable t) {
                    throw new RuntimeException("Failed to handle BlockBreakAck packet data", t);
                }
            });
            wrapper.write(Types.STRING, SyncTasks.PACKET_SYNC_IDENTIFIER);
            wrapper.write(Types.STRING, uuid);
        });
    }

}
