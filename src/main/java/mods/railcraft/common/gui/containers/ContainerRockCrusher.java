/*------------------------------------------------------------------------------
 Copyright (c) CovertJaguar, 2011-2017
 http://railcraft.info

 This code is the property of CovertJaguar
 and may only be used with explicit written
 permission unless otherwise specified on the
 license page at http://railcraft.info/wiki/info:license.
 -----------------------------------------------------------------------------*/
package mods.railcraft.common.gui.containers;

import mods.railcraft.api.crafting.RailcraftCraftingManager;
import mods.railcraft.common.blocks.multi.TileRockCrusher;
import mods.railcraft.common.gui.slots.SlotOutput;
import mods.railcraft.common.gui.slots.SlotRailcraft;
import mods.railcraft.common.gui.widgets.ChargeNetworkIndicator;
import mods.railcraft.common.gui.widgets.IndicatorWidget;
import mods.railcraft.common.util.inventory.InvTools;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

public class ContainerRockCrusher extends RailcraftContainer {

    private final TileRockCrusher tile;
    private int lastProcessTime;

    public ContainerRockCrusher(InventoryPlayer inventoryplayer, TileRockCrusher crusher) {
        super(crusher);
        this.tile = crusher;

//        if (tile.rfIndicator != null) TODO: charge
//            addWidget(new IndicatorWidget(tile.rfIndicator, 157, 23, 176, 53, 6, 48));
        addWidget(new IndicatorWidget(new ChargeNetworkIndicator(tile.getWorld(), tile.getPos()), 157, 23, 176, 53, 6, 48));

        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                addSlot(new SlotRockCrusher(crusher, i * 3 + k, 8 + k * 18, 21 + i * 18));
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                addSlot(new SlotOutput(crusher, 9 + i * 3 + k, 98 + k * 18, 21 + i * 18));
            }
        }
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(inventoryplayer, k + i * 9 + 9, 8 + k * 18, 84 + i * 18));
            }

        }

        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inventoryplayer, j, 8 + j * 18, 142));
        }

    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendProgressBarUpdate(this, 0, tile.getProcessTime());
    }

    @Override
    public void sendUpdateToClient() {
        super.sendUpdateToClient();
        for (IContainerListener listener : listeners) {
            if (lastProcessTime != tile.getProcessTime())
                listener.sendProgressBarUpdate(this, 0, tile.getProcessTime());
        }

        lastProcessTime = tile.getProcessTime();
    }

    @Override
    public void updateProgressBar(int id, int data) {
        switch (id) {
            case 0:
                tile.setProcessTime(data);
                break;
        }
    }

    public class SlotRockCrusher extends SlotRailcraft {

        public SlotRockCrusher(IInventory iinventory, int slotIndex, int posX, int posY) {
            super(iinventory, slotIndex, posX, posY);
        }

        @Override
        public boolean isItemValid(@Nullable ItemStack stack) {
            return !InvTools.isEmpty(stack) && RailcraftCraftingManager.rockCrusher.getRecipe(stack) != null;
        }

    }
}
