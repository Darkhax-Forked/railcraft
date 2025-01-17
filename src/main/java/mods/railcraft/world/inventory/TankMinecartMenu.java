package mods.railcraft.world.inventory;

import mods.railcraft.gui.widget.FluidGaugeWidget;
import mods.railcraft.util.container.StackFilter;
import mods.railcraft.world.entity.vehicle.TankMinecart;
import mods.railcraft.world.inventory.slot.FluidFilterSlot;
import mods.railcraft.world.inventory.slot.ItemFilterSlot;
import mods.railcraft.world.inventory.slot.OutputSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class TankMinecartMenu extends RailcraftMenu {

  private final FluidGaugeWidget fluidGauge;

  public TankMinecartMenu(int id, Inventory inventory,
      TankMinecart tankMinecart) {
    super(RailcraftMenuTypes.TANK_MINECART.get(), id, inventory.player, tankMinecart::stillValid);

    this.addWidget(this.fluidGauge =
        new FluidGaugeWidget(tankMinecart.getTankManager(), 35, 23, 176, 0, 16, 47));

    this.addSlot(new FluidFilterSlot(tankMinecart.getFilterInv(),
        TankMinecart.SLOT_INPUT, 80, 21));
    this.addSlot(new ItemFilterSlot(StackFilter.FLUID_CONTAINER, tankMinecart.getInvLiquids(),
        TankMinecart.SLOT_INPUT, 116, 21));
    this.addSlot(new OutputSlot(tankMinecart.getInvLiquids(),
        TankMinecart.SLOT_PROCESSING, 116, 57));
    this.addSlot(new OutputSlot(tankMinecart.getInvLiquids(),
        TankMinecart.SLOT_OUTPUT, 80, 57));

    for (int y = 0; y < 3; y++) {
      for (int x = 0; x < 9; x++) {
        this.addSlot(new Slot(inventory, x + y * 9 + 9, 8 + x * 18, 84 + y * 18));
      }
    }

    for (int x = 0; x < 9; x++) {
      this.addSlot(new Slot(inventory, x, 8 + x * 18, 142));
    }
  }

  public FluidGaugeWidget getFluidGauge() {
    return this.fluidGauge;
  }

  public static TankMinecartMenu create(int id, Inventory playerInventory, FriendlyByteBuf data) {
    int entityId = data.readVarInt();
    var entity = playerInventory.player.level().getEntity(entityId);
    if (entity instanceof TankMinecart tankMinecart) {
      return new TankMinecartMenu(id, playerInventory, tankMinecart);
    }
    throw new IllegalStateException("Cannot find tank minecart with ID: " + entityId);
  }
}
