package mods.railcraft.world.entity.vehicle.locomotive;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import com.mojang.authlib.GameProfile;
import mods.railcraft.RailcraftConfig;
import mods.railcraft.advancements.RailcraftCriteriaTriggers;
import mods.railcraft.api.carts.IPaintedCart;
import mods.railcraft.api.carts.IRoutableCart;
import mods.railcraft.api.carts.LinkageHandler;
import mods.railcraft.api.core.Lockable;
import mods.railcraft.client.ClientEffects;
import mods.railcraft.client.gui.widget.button.ButtonTexture;
import mods.railcraft.client.gui.widget.button.SimpleTexturePosition;
import mods.railcraft.client.gui.widget.button.TexturePosition;
import mods.railcraft.gui.button.ButtonState;
import mods.railcraft.network.RailcraftDataSerializers;
import mods.railcraft.season.Seasons;
import mods.railcraft.util.MathTools;
import mods.railcraft.util.PlayerUtil;
import mods.railcraft.util.RCEntitySelectors;
import mods.railcraft.util.collections.Streams;
import mods.railcraft.util.container.ContainerTools;
import mods.railcraft.world.damagesource.RailcraftDamageSource;
import mods.railcraft.world.entity.vehicle.CartTools;
import mods.railcraft.world.entity.vehicle.IDirectionalCart;
import mods.railcraft.world.entity.vehicle.Link;
import mods.railcraft.world.entity.vehicle.LinkageManagerImpl;
import mods.railcraft.world.entity.vehicle.MaintenanceMinecart;
import mods.railcraft.world.entity.vehicle.RailcraftMinecart;
import mods.railcraft.world.entity.vehicle.Train;
import mods.railcraft.world.item.LocomotiveItem;
import mods.railcraft.world.item.RailcraftItems;
import mods.railcraft.world.item.TicketItem;
import mods.railcraft.world.level.block.track.behaivor.HighSpeedTools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Locmotive class, for trains that does the push/pulling.
 * 
 * @author CovertJaguar (https://www.railcraft.info)
 */
public abstract class Locomotive extends RailcraftMinecart
    implements IDirectionalCart, LinkageHandler, Lockable,
    IPaintedCart, IRoutableCart {

  private static final EntityDataAccessor<Boolean> HAS_FUEL =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<Byte> MODE =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);
  private static final EntityDataAccessor<Byte> SPEED =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);
  private static final EntityDataAccessor<Byte> LOCK =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BYTE);
  private static final EntityDataAccessor<Boolean> REVERSE =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.BOOLEAN);
  private static final EntityDataAccessor<Integer> PRIMARY_COLOR =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<Integer> SECONDARY_COLOR =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.INT);
  private static final EntityDataAccessor<String> EMBLEM =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<String> DESTINATION =
      SynchedEntityData.defineId(Locomotive.class, EntityDataSerializers.STRING);
  private static final EntityDataAccessor<Optional<GameProfile>> OWNER =
      SynchedEntityData.defineId(Locomotive.class,
          RailcraftDataSerializers.OPTIONAL_GAME_PROFILE);

  private static final double DRAG_FACTOR = 0.9;
  private static final float HS_FORCE_BONUS = 3.5F;
  private static final byte FUEL_USE_INTERVAL = 8;
  private static final byte KNOCKBACK = 1;
  private static final int WHISTLE_INTERVAL = 256;
  private static final int WHISTLE_DELAY = 160;
  private static final int WHISTLE_CHANCE = 4;
  private static final Set<Mode> SUPPORTED_MODES =
      Collections.unmodifiableSet(EnumSet.allOf(Mode.class));

  protected float renderYaw;
  private int fuel;
  private int whistleDelay;
  private int tempIdle;
  private float whistlePitch = getNewWhistlePitch();

  protected Locomotive(EntityType<?> type, Level world) {
    super(type, world);
  }

  protected Locomotive(ItemStack itemStack, EntityType<?> type, double x,
      double y, double z, ServerLevel level) {
    super(type, x, y, z, level);
    this.loadFromItemStack(itemStack);
  }

  @Override
  protected void defineSynchedData() {
    super.defineSynchedData();
    this.entityData.define(HAS_FUEL, false);
    this.entityData.define(PRIMARY_COLOR, this.getDefaultPrimaryColor().getId());
    this.entityData.define(SECONDARY_COLOR, this.getDefaultSecondaryColor().getId());
    this.entityData.define(MODE, (byte) Mode.SHUTDOWN.ordinal());
    this.entityData.define(SPEED, (byte) Speed.NORMAL.ordinal());
    this.entityData.define(LOCK, (byte) Lock.UNLOCKED.ordinal());
    this.entityData.define(REVERSE, false);
    this.entityData.define(EMBLEM, "");
    this.entityData.define(DESTINATION, "");
    this.entityData.define(OWNER, Optional.empty());
  }

  // purple and black, no ""texture"".
  protected DyeColor getDefaultPrimaryColor() {
    return DyeColor.PURPLE;
  }

  protected DyeColor getDefaultSecondaryColor() {
    return DyeColor.BLACK;
  }

  protected void loadFromItemStack(ItemStack itemStack) {
    CompoundTag tag = itemStack.getTag();
    if (tag == null || !(itemStack.getItem() instanceof LocomotiveItem)) {
      return;
    }

    this.setPrimaryColor(LocomotiveItem.getPrimaryColor(itemStack));
    this.setSecondaryColor(LocomotiveItem.getSecondaryColor(itemStack));

    if (tag.contains("whistlePitch")) {
      this.whistlePitch = tag.getFloat("whistlePitch");
    }

    if (tag.contains("owner", Tag.TAG_COMPOUND)) {
      GameProfile ownerProfile = NbtUtils.readGameProfile(tag.getCompound("owner"));
      this.setOwner(ownerProfile);
      this.setLock(Lock.LOCKED);
    }

    if (tag.contains("lock", Tag.TAG_STRING)) {
      this.setLock(Lock.getByName(tag.getString("lock")).get());
    }

    if (tag.contains("emblem", Tag.TAG_STRING)) {
      this.setEmblem(tag.getString("emblem"));
    }
  }

  @Override
  public Optional<GameProfile> getOwner() {
    return this.entityData.get(OWNER);
  }

  @Override
  public void setOwner(@Nullable GameProfile owner) {
    this.entityData.set(OWNER, Optional.ofNullable(owner));
  }

  private float getNewWhistlePitch() {
    return 1f + (float) this.random.nextGaussian() * 0.2f;
  }

  /**
   * Returns the cart's actual item.
   */
  protected abstract Item getItem();

  @Override
  public ItemStack getPickResult() {
    ItemStack itemStack = this.getItem().getDefaultInstance();
    if (this.isLocked()) {
      LocomotiveItem.setOwnerData(itemStack, this.getOwnerOrThrow());
    }
    LocomotiveItem.setItemWhistleData(itemStack, this.whistlePitch);
    LocomotiveItem.setEmblem(itemStack, this.getEmblem());
    if (this.hasCustomName()) {
      itemStack.setHoverName(this.getCustomName());
    }
    return itemStack;
  }

  @Override
  public InteractionResult interact(Player player, InteractionHand hand) {
    if (this.level.isClientSide()) {
      return InteractionResult.CONSUME;
    }

    ItemStack itemStack = player.getItemInHand(hand);
    if (!itemStack.isEmpty() && itemStack.getItem() == RailcraftItems.WHISTLE_TUNER.get()) {
      if (this.whistleDelay <= 0) {
        this.whistlePitch = this.getNewWhistlePitch();
        this.whistle();
        itemStack.hurtAndBreak(1, (ServerPlayer) player,
            serverPlayerEntity -> player.broadcastBreakEvent(hand));
      }
      return InteractionResult.CONSUME;
    }
    if (this.canControl(player)) {
      super.interact(player, hand); // open gui
    }
    return InteractionResult.CONSUME;
  }

  /**
   * Indicates if this object is locked.
   *
   * @return true if it's secured.
   */
  @Override
  public boolean isLocked() {
    return this.getLock() == Lock.LOCKED || this.isPrivate();
  }

  /**
   * Indicates if this object is set to private.
   *
   * @return true if it's private.
   */
  public boolean isPrivate() {
    return this.getLock() == Lock.PRIVATE;
  }

  /**
   * Can the user use this locomotive?.
   *
   * @param gameProfile The user.
   * @return TRUE if they can.
   * @see mods.railcraft.world.entity.vehicle.locomotive.Locomotive#isSecure isSecure
   * @see mods.railcraft.world.entity.vehicle.locomotive.Locomotive#isPrivate isPrivate
   */
  public boolean canControl(Player player) {
    return !this.isPrivate()
        || PlayerUtil.isOwnerOrOp(this.getOwner().orElseThrow(
            () -> new IllegalStateException("Locomotive is private but has no owner.")),
            player);
  }

  /**
   * Gets the lock status.
   */
  public Lock getLock() {
    return Lock.values()[this.entityData.get(LOCK)];
  }

  /**
   * Sets the lock from the status.
   */
  public void setLock(Lock lock) {
    this.entityData.set(LOCK, (byte) lock.ordinal());
  }

  public String getEmblem() {
    return this.getEntityData().get(EMBLEM);
  }

  public void setEmblem(String emblem) {
    if (!getEmblem().equals(emblem)) {
      this.getEntityData().set(EMBLEM, emblem);
    }
  }

  /**
   * Gets the destination ticket item.
   */
  public ItemStack getDestItem() {
    return getTicketInventory().getItem(1);
  }

  @Override
  public String getDestination() {
    return this.getEntityData().get(DESTINATION);
  }

  /**
   * Set the destination used by routing the train around your train network.
   */
  public void setDestination(String destination) {
    this.getEntityData().set(DESTINATION, destination);
  }

  /**
   * Alternative to {@link Locomotive#setDestination(String destination)} (void return), sets the
   * destination based on the ticket the user has.
   */
  public boolean setDestination(ItemStack ticket) {
    if (ticket.getItem() instanceof TicketItem) {
      if (this.isLocked()) {
        var ticketOwner = TicketItem.getOwner(ticket);
        if (!this.getOwnerOrThrow().equals(ticketOwner)
            && (!(this.level instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().getPlayerList().isOp(ticketOwner))) {
          return false;
        }
      }
      var destination = TicketItem.getDestination(ticket);
      if (!destination.equals(this.getDestination())) {
        this.setDestination(destination);
        this.getTicketInventory().setItem(1, TicketItem.copyTicket(ticket));
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the current train's mode. Returns an enum mode.
   */
  public Mode getMode() {
    return RailcraftDataSerializers.getEnum(this.getEntityData(), MODE, Mode.values());
  }

  /**
   * Sets the current train's mode.
   */
  public void setMode(Mode mode) {
    if (!this.isAllowedMode(mode)) {
      return;
    }
    RailcraftDataSerializers.setEnum(this.getEntityData(), MODE, mode);
  }

  /**
   * Modes of operation that this {@link Locomotive} supports.
   *
   * @return a {@link Set} of supported {@link Mode}s.
   */
  public Set<Mode> getSupportedModes() {
    return SUPPORTED_MODES;
  }

  /**
   * Determines if the specified {@link Mode} is allowed in the {@link Locomotive}'s current state.
   *
   * @param mode - the {@link Mode} to check
   * @return {@code true} if the specified mode is allowed
   */
  public boolean isAllowedMode(Mode mode) {
    return this.getSupportedModes().contains(mode);
  }

  /**
   * Get the train's max speed, accelerate speed, and breaking/reverse speed.
   * 
   * @see Speed
   */
  public Speed getSpeed() {
    return RailcraftDataSerializers.getEnum(this.getEntityData(), SPEED, Speed.values());
  }

  /**
   * Sets the train's speed level.
   * 
   * @see Speed
   */
  public void setSpeed(Speed speed) {
    if (this.isReverse() && (speed.getLevel() > this.getMaxReverseSpeed().getLevel())) {
      return;
    }
    RailcraftDataSerializers.setEnum(this.getEntityData(), SPEED, speed);
  }

  /**
   * Gets the train's reverse speed from the {@link Speed} enum.
   */
  public Speed getMaxReverseSpeed() {
    return Speed.NORMAL;
  }

  /**
   * Shifts the train speed up.
   * 
   * @see Speed
   */
  public void increaseSpeed() {
    this.setSpeed(this.getSpeed().shiftUp());
  }

  /**
   * Shifts the train speed down.
   * 
   * @see Speed
   */
  public void decreaseSpeed() {
    this.setSpeed(this.getSpeed().shiftDown());
  }

  public boolean hasFuel() {
    return this.getEntityData().get(HAS_FUEL);
  }

  public void setHasFuel(boolean hasFuel) {
    this.getEntityData().set(HAS_FUEL, hasFuel);
  }

  public boolean isReverse() {
    return this.getEntityData().get(REVERSE);
  }

  public void setReverse(boolean reverse) {
    this.getEntityData().set(REVERSE, reverse);
  }

  public boolean isRunning() {
    return this.fuel > 0 && this.getMode() == Mode.RUNNING && !(this.isIdle() || this.isShutdown());
  }

  /**
   * Is the train idle? Returns true if it is.
   */
  public boolean isIdle() {
    return !this.isShutdown()
        && (this.tempIdle > 0 || this.getMode() == Mode.IDLE
            || Train.get(this).map(Train::isIdle).orElse(false));
  }

  public boolean isShutdown() {
    return this.getMode() == Mode.SHUTDOWN || Train.get(this).map(Train::isStopped).orElse(false);
  }

  public void forceIdle(int ticks) {
    this.tempIdle = Math.max(this.tempIdle, ticks);
  }

  @Override
  public void reverse() {
    this.setYRot(this.getYRot() + 180.0F);
    this.setDeltaMovement(this.getDeltaMovement().multiply(-1.0D, 1.0D, -1.0D));
  }

  @Override
  public void setRenderYaw(float yaw) {
    this.renderYaw = yaw;
  }

  public abstract SoundEvent getWhistleSound();

  /**
   * Play a whistle sfx.
   */
  public final void whistle() {
    if (this.whistleDelay <= 0) {
      this.level.playSound(null, this, this.getWhistleSound(), this.getSoundSource(),
          1.0F, 1.0F);
      this.whistleDelay = WHISTLE_DELAY;
    }
  }

  @Override
  public void tick() {
    super.tick();

    if (this.level.isClientSide()) {
      if (Seasons.isPolarExpress(this)
          && (!MathTools.nearZero(this.getDeltaMovement().x())
              || !MathTools.nearZero(this.getDeltaMovement().z()))) {
        ClientEffects.INSTANCE.snowEffect(
            this.getX(), this.getBoundingBox().minY - this.getY(), this.getZ());
      }
      return;
    }

    this.processTicket();
    this.updateFuel();

    if (this.whistleDelay > 0) {
      this.whistleDelay--;
    }

    if (this.tempIdle > 0) {
      this.tempIdle--;
    }

    if ((this.tickCount % WHISTLE_INTERVAL) == 0
        && this.isRunning()
        && this.random.nextInt(WHISTLE_CHANCE) == 0) {
      this.whistle();
    }
  }

  protected abstract Container getTicketInventory();

  private void processTicket() {
    Container invTicket = this.getTicketInventory();
    ItemStack stack = invTicket.getItem(0);
    if (stack.getItem() instanceof TicketItem) {
      if (setDestination(stack)) {
        invTicket.setItem(0, ContainerTools.depleteItem(stack));
      }
    } else {
      invTicket.setItem(0, ItemStack.EMPTY);
    }
  }

  @Override
  protected void applyNaturalSlowdown() {
    this.setDeltaMovement(this.getDeltaMovement().multiply(getDrag(), 0.0D, getDrag()));

    if (isReverse() && getSpeed().getLevel() > getMaxReverseSpeed().getLevel()) {
      setSpeed(getMaxReverseSpeed());
    }

    Speed speed = getSpeed();
    if (isRunning()) {
      double force = RailcraftConfig.server.locomotiveHorsepower.get() * 0.01F;
      if (isReverse()) {
        force = -force;
      }
      switch (speed) {
        case MAX:
          boolean highSpeed = HighSpeedTools.isTravellingHighSpeed(this);
          if (highSpeed) {
            force *= HS_FORCE_BONUS;
          }
          break;
        default:
          break;
      }
      double yaw = this.getYRot() * Math.PI / 180D;
      this.setDeltaMovement(
          this.getDeltaMovement().add(Math.cos(yaw) * force, 0, Math.sin(yaw) * force));
    }

    if (speed != Speed.MAX) {
      float limit = 0.4f;
      switch (speed) {
        case SLOWEST:
          limit = 0.1f;
          break;
        case SLOWER:
          limit = 0.2f;
          break;
        case NORMAL:
          limit = 0.3f;
          break;
        default:
          break;
      }

      Vec3 motion = this.getDeltaMovement();

      this.setDeltaMovement(
          Math.copySign(Math.min(Math.abs(motion.x()), limit), motion.x()),
          motion.y(),
          Math.copySign(Math.min(Math.abs(motion.z()), limit), motion.z()));
    }
  }

  private int getFuelUse() {
    if (isRunning()) {
      Speed speed = getSpeed();
      switch (speed) {
        case SLOWEST:
          return 2;
        case SLOWER:
          return 4;
        case NORMAL:
          return 6;
        default:
          return 8;
      }
    } else if (isIdle()) {
      return getIdleFuelUse();
    }
    return 0;
  }

  protected int getIdleFuelUse() {
    return 1;
  }

  protected void updateFuel() {
    if (this.tickCount % FUEL_USE_INTERVAL == 0 && this.fuel > 0) {
      this.fuel -= this.getFuelUse();
      if (this.fuel < 0) {
        this.fuel = 0;
      }
    }
    while (this.fuel <= FUEL_USE_INTERVAL && !this.isShutdown()) {
      int newFuel = this.retrieveFuel();
      if (newFuel <= 0) {
        break;
      }
      this.fuel += newFuel;
    }
    this.setHasFuel(this.fuel > 0);
  }

  protected abstract int retrieveFuel();

  public int getDamageToRoadKill(LivingEntity entity) {
    if (entity instanceof Player) {
      ItemStack pants = entity.getItemBySlot(EquipmentSlot.LEGS);
      if (RailcraftItems.OVERALLS.get() == pants.getItem()) {
        pants.hurtAndBreak(5, entity,
            unusedThing -> entity.broadcastBreakEvent(EquipmentSlot.LEGS));
        return 4;
      }
    }
    return 25;
  }

  @Override
  public void push(Entity entity) {
    if (!this.level.isClientSide()) {
      if (!entity.isAlive()) {
        return;
      }
      if (Train.streamCarts(this).noneMatch(t -> t.hasPassenger(entity))
          && (isVelocityHigherThan(0.2f) || HighSpeedTools.isTravellingHighSpeed(this))
          && RCEntitySelectors.KILLABLE.test(entity)) {
        LivingEntity living = (LivingEntity) entity;
        if (RailcraftConfig.server.locomotiveDamageMobs.get()) {
          living.hurt(RailcraftDamageSource.TRAIN, getDamageToRoadKill(living));
        }
        if (living.getHealth() > 0) {
          float yaw = (this.getYRot() - 90) * (float) Math.PI / 180.0F;
          this.setDeltaMovement(
              this.getDeltaMovement().add(-Mth.sin(yaw) * KNOCKBACK * 0.5F, 0.2D,
                  Mth.cos(yaw) * KNOCKBACK * 0.5F));
        } else {
          if (living instanceof ServerPlayer) {
            RailcraftCriteriaTriggers.KILLED_BY_LOCOMOTIVE.trigger(
                (ServerPlayer) living, this);
          }
        }
        return;
      }
      if (this.collidedWithOtherLocomotive(entity)) {
        Locomotive otherLoco = (Locomotive) entity;
        this.explode();
        if (otherLoco.isAlive()) {
          otherLoco.explode();
        }
        return;
      }
    }
    super.push(entity);
  }

  private boolean collidedWithOtherLocomotive(Entity entity) {
    if (!(entity instanceof Locomotive)) {
      return false;
    }
    Locomotive otherLoco = (Locomotive) entity;
    if (getUUID().equals(entity.getUUID())) {
      return false;
    }
    if (Train.areInSameTrain(this, otherLoco)) {
      return false;
    }

    Vec3 motion = this.getDeltaMovement();
    Vec3 otherMotion = entity.getDeltaMovement();
    return isVelocityHigherThan(0.2f) && otherLoco.isVelocityHigherThan(0.2f)
        && (Math.abs(motion.x() - otherMotion.x()) > 0.3f
            || Math.abs(motion.z() - otherMotion.z()) > 0.3f);
  }

  private boolean isVelocityHigherThan(float velocity) {
    return Math.abs(this.getDeltaMovement().x()) > velocity
        || Math.abs(this.getDeltaMovement().z()) > velocity;
  }

  @Override
  public void remove(RemovalReason reason) {
    this.getTicketInventory().setItem(1, ItemStack.EMPTY);
    super.remove(reason);
  }

  public void explode() {
    CartTools.explodeCart(this);
    this.remove(RemovalReason.KILLED);
  }

  public double getDrag() {
    return DRAG_FACTOR;
  }

  @Override
  public void addAdditionalSaveData(CompoundTag data) {
    super.addAdditionalSaveData(data);

    data.putBoolean("flipped", this.flipped);

    data.putString("emblem", getEmblem());

    data.putString("dest", StringUtils.defaultIfBlank(getDestination(), ""));

    data.putString("mode", this.getMode().getSerializedName());
    data.putString("speed", this.getSpeed().getSerializedName());
    data.putString("lock", this.getLock().getSerializedName());

    data.putString("primaryColor",
        DyeColor.byId(this.entityData.get(PRIMARY_COLOR)).getSerializedName());
    data.putString("secondaryColor",
        DyeColor.byId(this.entityData.get(SECONDARY_COLOR)).getSerializedName());

    data.putFloat("whistlePitch", this.whistlePitch);

    data.putInt("fuel", this.fuel);

    data.putBoolean("reverse", this.isReverse());
  }

  @Override
  public void readAdditionalSaveData(CompoundTag data) {
    super.readAdditionalSaveData(data);

    this.flipped = data.getBoolean("flipped");

    this.setEmblem(data.getString("emblem"));

    this.setDestination(data.getString("dest"));

    this.setMode(Mode.getByName(data.getString("mode")).orElse(Mode.IDLE));
    this.setSpeed(Speed.getByName(data.getString("speed")).orElse(Speed.NORMAL));
    this.setLock(Lock.getByName(data.getString("lock")).orElse(Lock.UNLOCKED));

    this.setPrimaryColor(data.contains("primaryColor", Tag.TAG_STRING)
        ? DyeColor.byName(data.getString("primaryColor"), this.getDefaultPrimaryColor())
        : this.getDefaultPrimaryColor());
    this.setSecondaryColor(data.contains("secondaryColor", Tag.TAG_STRING)
        ? DyeColor.byName(data.getString("secondaryColor"), this.getDefaultSecondaryColor())
        : this.getDefaultSecondaryColor());

    this.whistlePitch = data.getFloat("whistlePitch");

    this.fuel = data.getInt("fuel");

    if (data.contains("reverse", Tag.TAG_BYTE)) {
      this.getEntityData().set(REVERSE, data.getBoolean("reverse"));
    }
  }

  public static void applyAction(Player player, AbstractMinecart cart,
      boolean single, Consumer<Locomotive> action) {
    var locos = Train.streamCarts(cart)
        .flatMap(Streams.ofType(Locomotive.class))
        .filter(loco -> loco.canControl(player));
    if (single) {
      locos.findAny().ifPresent(action);
    } else {
      locos.forEach(action);
    }
  }

  @Override
  public boolean canBeRidden() {
    return false;
  }

  @Override
  public int getContainerSize() {
    return 0;
  }

  @Override
  public boolean isPoweredCart() {
    return true;
  }

  @Override
  public boolean isLinkable() {
    return true;
  }

  @Override
  public boolean canLink(AbstractMinecart cart) {
    if (isExemptFromLinkLimits(cart)) {
      return true;
    }

    if (StreamSupport
        .stream(LinkageManagerImpl.INSTANCE.linkIterator(this, Link.FRONT).spliterator(), false)
        .anyMatch(linked -> !isExemptFromLinkLimits(linked))) {
      return false;
    }
    return StreamSupport
        .stream(LinkageManagerImpl.INSTANCE.linkIterator(this, Link.BACK).spliterator(), false)
        .allMatch(this::isExemptFromLinkLimits);
  }

  private boolean isExemptFromLinkLimits(AbstractMinecart cart) {
    return cart instanceof Locomotive || cart instanceof MaintenanceMinecart;
  }

  @Override
  public float getLinkageDistance(AbstractMinecart cart) {
    return LinkageManagerImpl.LINKAGE_DISTANCE;
  }

  @Override
  public float getOptimalDistance(AbstractMinecart cart) {
    return 0.9f;
  }

  @Override
  public void onLinkCreated(AbstractMinecart cart) {
    // Moved from linkage manager - this should not be there
    if (getSpeed().compareTo(Speed.SLOWEST) > 0) {
      setSpeed(Speed.SLOWEST);
    }
  }

  @Override
  public boolean canPassItemRequests(ItemStack stack) {
    return true;
  }

  @Override
  public final float[] getPrimaryColor() {
    return DyeColor.byId(this.entityData.get(PRIMARY_COLOR)).getTextureDiffuseColors();
  }

  public final void setPrimaryColor(DyeColor color) {
    this.entityData.set(PRIMARY_COLOR, color.getId());
  }

  @Override
  public final float[] getSecondaryColor() {
    return DyeColor.byId(this.entityData.get(SECONDARY_COLOR)).getTextureDiffuseColors();
  }

  public final void setSecondaryColor(DyeColor color) {
    this.entityData.set(SECONDARY_COLOR, color.getId());
  }

  /**
   * The train states.
   */
  public enum Mode implements StringRepresentable {

    SHUTDOWN("shutdown"),
    IDLE("idle"),
    RUNNING("running");

    private static final Map<String, Mode> byName = Arrays.stream(values())
        .collect(Collectors.toMap(Mode::getSerializedName, Function.identity()));

    private final String name;

    private Mode(String name) {
      this.name = name;
    }

    @Override
    public String getSerializedName() {
      return this.name;
    }

    public static Optional<Mode> getByName(String name) {
      return Optional.ofNullable(byName.get(name));
    }
  }

  /**
   * The train's current speed settings.
   */
  public enum Speed implements StringRepresentable {

    SLOWEST("slowest", 1, 1, 0),
    SLOWER("slower", 2, 1, -1),
    NORMAL("normal", 3, 1, -1),
    MAX("max", 4, 0, -1);

    private static final Map<String, Speed> byName = Arrays.stream(values())
        .collect(Collectors.toMap(Speed::getSerializedName, Function.identity()));

    private final String name;
    private final int shiftUp;
    private final int shiftDown;
    private final int level;

    private Speed(String name, int level, int shiftUp, int shiftDown) {
      this.name = name;
      this.level = level;
      this.shiftUp = shiftUp;
      this.shiftDown = shiftDown;
    }

    public int getLevel() {
      return this.level;
    }

    @Override
    public String getSerializedName() {
      return this.name;
    }

    public Speed shiftUp() {
      return values()[this.ordinal() + shiftUp];
    }

    public Speed shiftDown() {
      return values()[this.ordinal() + shiftDown];
    }

    public static Optional<Speed> getByName(String name) {
      return Optional.ofNullable(byName.get(name));
    }

    public static Speed fromLevel(int level) {
      return values()[level - 1];
    }
  }

  public enum Lock implements ButtonState<Lock>, StringRepresentable {

    UNLOCKED("unlocked", ButtonTexture.UNLOCKED_BUTTON),
    LOCKED("locked", ButtonTexture.LOCKED_BUTTON),
    PRIVATE("private", new SimpleTexturePosition(240, 48, 16, 16));

    private static final Map<String, Lock> byName = Arrays.stream(values())
        .collect(Collectors.toMap(Lock::getSerializedName, Function.identity()));

    private final String name;
    private final TexturePosition texture;

    private Lock(String name, TexturePosition texture) {
      this.name = name;
      this.texture = texture;
    }

    @Override
    public Component getLabel() {
      return TextComponent.EMPTY;
    }

    @Override
    public TexturePosition getTexturePosition() {
      return this.texture;
    }

    @Override
    public Lock getNext() {
      return values()[(this.ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
      return this.name;
    }

    public static Optional<Lock> getByName(String name) {
      return Optional.ofNullable(byName.get(name));
    }
  }
}