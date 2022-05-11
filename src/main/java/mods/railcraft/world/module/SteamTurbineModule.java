package mods.railcraft.world.module;

import mods.railcraft.api.charge.Charge;
import mods.railcraft.tags.RailcraftTags;
import mods.railcraft.util.MiscTools;
import mods.railcraft.util.container.AdvancedContainer;
import mods.railcraft.world.item.RailcraftItems;
import mods.railcraft.world.level.block.entity.SteamTurbineBlockEntity;
import mods.railcraft.world.level.material.fluid.FluidTools;
import mods.railcraft.world.level.material.fluid.RailcraftFluids;
import mods.railcraft.world.level.material.fluid.tank.FilteredTank;
import mods.railcraft.world.level.material.fluid.tank.StandardTank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class SteamTurbineModule extends ChargeModule<SteamTurbineBlockEntity> {

  private static final int ROTOR_DAMAGE_CHANCE = 200;

  public static final int CHARGE_OUTPUT = 225;
  private static final int STEAM_USAGE = 360;
  public static final int TANK_STEAM = 0;
  public static final int TANK_WATER = 1;

  private final StandardTank steamTank;
  private final StandardTank waterTank;

  private final AdvancedContainer rotorContainer = new AdvancedContainer(1);
  private float operatingRatio;
  private int energy;

  private int syncTicks;

  private final LazyOptional<IFluidHandler> fluidHandler = LazyOptional.of(FluidHandler::new);

  public SteamTurbineModule(SteamTurbineBlockEntity provider, Charge network) {
    super(provider, network);
    this.rotorContainer.listener(this.provider);
    this.steamTank = new FilteredTank(FluidTools.BUCKET_VOLUME * 4)
        .setFilterTag(RailcraftTags.Fluids.STEAM)
        .disableDrain();
    this.waterTank = new FilteredTank(FluidTools.BUCKET_VOLUME * 4)
        .setFilterTag(FluidTags.WATER)
        .disableFill();
  }

  public LazyOptional<IFluidHandler> getFluidHandler() {
    return this.fluidHandler;
  }

  @Override
  public void serverTick() {
    super.serverTick();
    var addedEnergy = false;
    if (this.energy < CHARGE_OUTPUT) {
      var steam = this.steamTank.internalDrain(STEAM_USAGE, FluidAction.SIMULATE);
      if (steam != null && steam.getAmount() >= STEAM_USAGE) {
        var rotorStack = this.rotorContainer.getItem(0);
        if (rotorStack.is(RailcraftItems.TURBINE_ROTOR.get())) {
          addedEnergy = true;
          this.energy += CHARGE_OUTPUT;
          this.steamTank.internalDrain(STEAM_USAGE, FluidAction.EXECUTE);
          this.waterTank.internalFill(new FluidStack(Fluids.WATER, 2), FluidAction.EXECUTE);
          this.rotorContainer.setItem(0, useRotor(rotorStack));
        }
      }
    }

    var thisTick = addedEnergy ? 1.0F : 0.0F;
    this.operatingRatio = (thisTick - this.operatingRatio) * 0.05F + this.operatingRatio;

    if (this.syncTicks++ >= 4) {
      this.syncTicks = 0;
      this.provider.syncToClient();
    }

    var chargeStorage = this.storage();
    if (!chargeStorage.isFull()) {
      chargeStorage.receiveEnergy(this.energy, false);
      this.energy = 0;
    }
  }

  public float getOperatingRatio() {
    return this.operatingRatio;
  }

  public Container getRotorContainer() {
    return this.rotorContainer;
  }

  public boolean needsMaintenance() {
    var rotorStack = this.rotorContainer.getItem(0);
    return rotorStack.isEmpty()
        || !rotorStack.is(RailcraftItems.TURBINE_ROTOR.get())
        || rotorStack.getDamageValue() / (float) rotorStack.getMaxDamage() > 0.75F;
  }

  public float readGauge(float previousValue) {
    return (previousValue * 14.0F + this.operatingRatio) / 15.0F;
  }

  @Override
  public void writeToBuf(FriendlyByteBuf out) {
    out.writeFloat(this.operatingRatio);
  }

  @Override
  public void readFromBuf(FriendlyByteBuf in) {
    this.operatingRatio = in.readFloat();
  }

  @Override
  public CompoundTag serializeNBT() {
    var tag = super.serializeNBT();
    tag.put("steamTank", this.steamTank.writeToNBT(new CompoundTag()));
    tag.put("waterTank", this.waterTank.writeToNBT(new CompoundTag()));
    tag.put("rotorContainer", this.rotorContainer.createTag());
    tag.putInt("energy", this.energy);
    tag.putFloat("operatingRatio", this.operatingRatio);
    return tag;
  }

  @Override
  public void deserializeNBT(CompoundTag tag) {
    super.deserializeNBT(tag);
    this.steamTank.readFromNBT(tag.getCompound("steamTank"));
    this.waterTank.readFromNBT(tag.getCompound("waterTank"));
    this.rotorContainer.fromTag(tag.getList("rotorContainer", Tag.TAG_COMPOUND));
    this.energy = tag.getInt("energy");
    this.operatingRatio = tag.getFloat("operatingRatio");
  }

  public static ItemStack useRotor(ItemStack stack) {
    return MiscTools.RANDOM.nextInt(ROTOR_DAMAGE_CHANCE) == 0
        && stack.hurt(1, MiscTools.RANDOM, null) ? ItemStack.EMPTY : stack;
  }

  public class FluidHandler implements IFluidHandler {

    @Override
    public int getTanks() {
      return 2;
    }

    private IFluidTank getTank(int tank) {
      return tank == 0 ? SteamTurbineModule.this.steamTank : SteamTurbineModule.this.waterTank;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
      return this.getTank(tank).getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
      return this.getTank(tank).getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
      return this.getTank(tank).isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      return SteamTurbineModule.this.steamTank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
      return SteamTurbineModule.this.waterTank.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
      return SteamTurbineModule.this.waterTank.drain(maxDrain, action);
    }
  }
}