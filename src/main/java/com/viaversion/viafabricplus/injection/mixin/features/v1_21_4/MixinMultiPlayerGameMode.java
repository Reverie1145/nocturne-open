package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.v1_21_4.IAbstractContainerMenu;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.version.VersionedTypes;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.Protocol1_16_4To1_17;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@SuppressWarnings("DataFlowIssue")
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private ItemStack viaFabricPlus$oldCursorStack;

    @Unique
    private List<ItemStack> viaFabricPlus$oldItems;

    @ModifyVariable(method = "handleContainerInput", at = @At(value = "STORE"), name = "itemsBeforeClick")
    private List<ItemStack> captureOldItems(List<ItemStack> itemsBeforeClick) {
        viaFabricPlus$oldCursorStack = minecraft.player.containerMenu.getCarried().copy();
        return this.viaFabricPlus$oldItems = itemsBeforeClick;
    }

    @WrapWithCondition(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean handleWindowClick(ClientPacketListener instance, Packet<?> packet) {
        final ServerboundContainerClickPacket clickSlotPacket = (ServerboundContainerClickPacket) packet;

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {

            viaFabricPlus$clickSlot1_16_5(clickSlotPacket);
            return false;
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {

            viaFabricPlus$clickSlot1_21_4(clickSlotPacket);
            return false;
        }

        return true;
    }

    @Unique
    private void viaFabricPlus$clickSlot1_21_4(final ServerboundContainerClickPacket packet) {
        final PacketWrapper containerClick = PacketWrapper.create(ServerboundPackets1_21_4.CONTAINER_CLICK, ViaFabricPlus.api().userConnection());
        containerClick.write(Types.VAR_INT, packet.containerId());
        containerClick.write(Types.VAR_INT, packet.stateId());
        containerClick.write(Types.SHORT, packet.slotNum());
        containerClick.write(Types.BYTE, packet.buttonNum());
        containerClick.write(Types.VAR_INT, packet.containerInput().id());

        final Int2ObjectMap<HashedStack> modifiedStacks = packet.changedSlots();
        containerClick.write(Types.VAR_INT, modifiedStacks.size());
        for (Int2ObjectMap.Entry<HashedStack> entry : modifiedStacks.int2ObjectEntrySet()) {
            final ItemStack itemStack = minecraft.player.containerMenu.slots.get(entry.getIntKey()).getItem();
            containerClick.write(Types.SHORT, (short) entry.getIntKey());
            containerClick.write(VersionedTypes.V1_21_4.item, ViaFabricPlus.api().conversions().translateItem(itemStack, ProtocolVersion.v1_21_4));
        }

        final ItemStack cursorStack = minecraft.player.containerMenu.getCarried();
        containerClick.write(VersionedTypes.V1_21_4.item, ViaFabricPlus.api().conversions().translateItem(cursorStack, ProtocolVersion.v1_21_4));
        containerClick.scheduleSendToServer(Protocol1_21_4To1_21_5.class);
    }

    @Unique
    private void viaFabricPlus$clickSlot1_16_5(final ServerboundContainerClickPacket packet) {
        ItemStack slotItemBeforeModification;
        if (this.viaFabricPlus$shouldBeEmpty(packet.containerInput(), packet.slotNum())) {
            slotItemBeforeModification = ItemStack.EMPTY;
        } else if (packet.slotNum() < 0 || packet.slotNum() >= viaFabricPlus$oldItems.size()) {
            slotItemBeforeModification = viaFabricPlus$oldCursorStack;
        } else {
            slotItemBeforeModification = viaFabricPlus$oldItems.get(packet.slotNum());
        }

        final PacketWrapper containerClick = PacketWrapper.create(ServerboundPackets1_16_2.CONTAINER_CLICK, ViaFabricPlus.api().userConnection());
        containerClick.write(Types.BYTE, (byte) packet.containerId());
        containerClick.write(Types.SHORT, packet.slotNum());
        containerClick.write(Types.BYTE, packet.buttonNum());
        containerClick.write(Types.SHORT, ((IAbstractContainerMenu) minecraft.player.containerMenu).viaFabricPlus$incrementAndGetActionId());
        containerClick.write(Types.VAR_INT, packet.containerInput().ordinal());
        containerClick.write(Types.ITEM1_13_2, ViaFabricPlus.api().conversions().translateItem(slotItemBeforeModification, ProtocolVersion.v1_16_4));
        containerClick.scheduleSendToServer(Protocol1_16_4To1_17.class);

        viaFabricPlus$oldCursorStack = null;
        viaFabricPlus$oldItems = null;
    }

    @Unique
    private boolean viaFabricPlus$shouldBeEmpty(final ContainerInput type, final int slot) {

        if (type == ContainerInput.QUICK_CRAFT) return true;

        if (type == ContainerInput.THROW) return true;

        if (type == ContainerInput.QUICK_MOVE && ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_11_1))
            return true;

        return type == ContainerInput.PICKUP && slot == -999;
    }

}
