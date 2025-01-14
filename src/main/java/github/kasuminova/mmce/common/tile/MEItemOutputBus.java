package github.kasuminova.mmce.common.tile;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.util.Platform;
import github.kasuminova.mmce.common.tile.base.MEItemBus;
import hellfirepvp.modularmachinery.common.lib.ItemsMM;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.util.IOInventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class MEItemOutputBus extends MEItemBus {

    @Override
    public IOInventory buildInventory() {
        int size = 36;

        int[] slotIDs = new int[size];
        for (int slotID = 0; slotID < size; slotID++) {
            slotIDs[slotID] = slotID;
        }
        return new IOInventory(this, new int[]{}, slotIDs);
    }

    @Override
    public ItemStack getVisualItemStack() {
        return new ItemStack(ItemsMM.meItemOutputBus);
    }

    @Nullable
    @Override
    public MachineComponent<IOInventory> provideComponent() {
        return new MachineComponent.ItemBus(IOType.OUTPUT) {
            @Override
            public IOInventory getContainerProvider() {
                return inventory;
            }
        };
    }

    @Nonnull
    @Override
    public TickingRequest getTickingRequest(@Nonnull final IGridNode node) {
        return new TickingRequest(5, 60, !hasItem(), true);
    }

    @Nonnull
    @Override
    public TickRateModulation tickingRequest(@Nonnull final IGridNode node, final int ticksSinceLastCall) {
        if (!proxy.isActive()) {
            return TickRateModulation.IDLE;
        }

        boolean successAtLeastOnce = false;

        try {
            IMEMonitor<IAEItemStack> inv = proxy.getStorage().getInventory(channel);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (stack == ItemStack.EMPTY) {
                    continue;
                }

                ItemStack extracted = inventory.extractItem(i, stack.getCount(), false);

                IAEItemStack aeStack = channel.createStack(extracted);
                if (aeStack == null) {
                    continue;
                }

                IAEItemStack left = Platform.poweredInsert(proxy.getEnergy(), inv, aeStack, source);

                if (left != null) {
                    inventory.setStackInSlot(i, left.createItemStack());

                    if (aeStack.getStackSize() != left.getStackSize()) {
                        successAtLeastOnce = true;
                    }
                } else {
                    successAtLeastOnce = true;
                }
            }
        } catch (GridAccessException e) {
            return TickRateModulation.IDLE;
        }

        return successAtLeastOnce ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    public boolean hasItem() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != ItemStack.EMPTY) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void markForUpdate() {
        if (proxy.isActive() && hasItem()) {
            try {
                proxy.getTick().alertDevice(proxy.getNode());
            } catch (GridAccessException e) {
                // NO-OP
            }
        }

        super.markForUpdate();
    }


}
