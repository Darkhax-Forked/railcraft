package mods.railcraft.world.item;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import mods.railcraft.util.LevelUtil;
import mods.railcraft.util.container.ContainerTools;
import mods.railcraft.world.entity.FirestoneItemEntity;
import mods.railcraft.world.level.block.RailcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Material;

/**
 * @author CovertJaguar <https://www.railcraft.info/>
 */
public class FirestoneItem extends Item {

  public static final Predicate<ItemStack> SPAWNS_FIRE = stack -> {
    if (stack.isEmpty())
      return false;
    if (RailcraftItems.RAW_FIRESTONE.get() == stack.getItem())
      return true;
    if (RailcraftItems.CUT_FIRESTONE.get() == stack.getItem())
      return true;
    if (RailcraftItems.CRACKED_FIRESTONE.get() == stack.getItem())
      return true;
    return ContainerTools.isStackEqualToBlock(stack, RailcraftBlocks.FIRESTONE.get());
  };

  public FirestoneItem(Properties properties) {
    super(properties);
  }

  /**
   * Determines if this Item has a special entity for when they are in the world. Is called when a
   * EntityItem is spawned in the world, if true and Item#createCustomEntity returns non null, the
   * EntityItem will be destroyed and the new Entity will be added to the world.
   *
   * @param stack The current item stack
   * @return True of the item has a custom entity, If true, Item#createCustomEntity will be called
   */
  @Override
  public boolean hasCustomEntity(ItemStack stack) {
    return true;
  }

  /**
   * This function should return a new entity to replace the dropped item. Returning null here will
   * not kill the EntityItem and will leave it to function normally. Called when the item it placed
   * in a world.
   *
   * @param world The world object
   * @param original The EntityItem object, useful for getting the position of the entity
   * @param stack The current item stack
   * @return A new Entity object to spawn or null
   */
  @Override
  public FirestoneItemEntity createEntity(Level world, Entity original, ItemStack stack) {
    return createEntityItem(world, original, stack);
  }

  /**
   * Called by CraftingManager to determine if an item is reparable.
   *
   * @return Always returns false for ItemFirestoneBase
   */
  @Override
  public boolean isRepairable(ItemStack itemStack) {
    return false;
  }

  public static FirestoneItemEntity createEntityItem(Level world, Entity original,
      ItemStack stack) {
    FirestoneItemEntity entity =
        new FirestoneItemEntity(original.getX(), original.getY(), original.getZ(), world, stack);
    entity.setThrower(((ItemEntity) original).getThrower());
    entity.setDeltaMovement(original.getDeltaMovement());
    entity.setDefaultPickUpDelay();
    return entity;
  }

  public static boolean trySpawnFire(Level world, BlockPos pos, ItemStack stack,
      Player holder) {
    if (stack.isEmpty() || !SPAWNS_FIRE.test(stack))
      return false;
    boolean spawnedFire = false;
    for (int i = 0; i < stack.getCount(); i++) {
      spawnedFire |= spawnFire(world, pos, holder);
    }
    if (spawnedFire && stack.isDamageableItem()
        && stack.getDamageValue() < stack.getMaxDamage() - 1)
      stack.hurtAndBreak(1, holder, __ -> {
      });
    return spawnedFire;
  }

  public static boolean spawnFire(Level level, BlockPos pos, @Nullable Player holder) {
    int x = pos.getX() - 5 + level.getRandom().nextInt(12);
    int y = pos.getY() - 5 + level.getRandom().nextInt(12);
    int z = pos.getZ() - 5 + level.getRandom().nextInt(12);

    if (y < 1)
      y = 1;
    if (y > level.getHeight())
      y = level.getHeight() - 2;

    BlockPos firePos = new BlockPos(x, y, z);
    return canBurn(level, firePos)
        && LevelUtil.setBlockState(level, firePos, Blocks.FIRE.defaultBlockState(), holder);
  }

  private static boolean canBurn(Level world, BlockPos pos) {
    if (world.getBlockState(pos).isAir()) {
      return false;
    }
    for (var side : Direction.values()) {
      var offset = pos.relative(side);
      var offsetBlockState = world.getBlockState(offset);
      if (!offsetBlockState.isAir() && offsetBlockState.getMaterial() != Material.FIRE) {
        return true;
      }
    }
    return false;
  }
}