package mods.railcraft.util;

import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.logging.log4j.util.Strings;
import com.mojang.authlib.GameProfile;
import mods.railcraft.api.core.RailcraftConstantsAPI;
import mods.railcraft.api.core.RailcraftFakePlayer;
import mods.railcraft.api.item.ActivationBlockingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * @author CovertJaguar <https://www.railcraft.info/>
 */
public final class PlayerUtil {

  public static void writeOwnerToNBT(CompoundTag nbt, GameProfile owner) {
    if (owner.getName() != null)
      nbt.putString("owner", owner.getName());
    if (owner.getId() != null)
      nbt.putString("ownerId", owner.getId().toString());
  }

  public static GameProfile readOwnerFromNBT(CompoundTag nbt) {
    String ownerName = RailcraftConstantsAPI.UNKNOWN_PLAYER;
    if (nbt.contains("owner", Tag.TAG_STRING))
      ownerName = nbt.getString("owner");
    UUID ownerUUID = null;
    if (nbt.hasUUID("ownerId"))
      ownerUUID = nbt.getUUID("ownerId");
    return new GameProfile(ownerUUID, ownerName);
  }

  public static @Nullable Player getPlayer(Level world, GameProfile gameProfile) {
    UUID playerId = gameProfile.getId();
    if (playerId != null) {
      Player player = world.getPlayerByUUID(playerId);
      if (player != null)
        return player;
    }
    return null;
  }

  public static Player getItemThrower(ItemEntity item) {
    UUID thrower = item.getThrower();
    Player player = null;
    if (thrower != null)
      player = item.level.getPlayerByUUID(item.getThrower());
    if (player == null)
      player = RailcraftFakePlayer.get((ServerLevel) item.level, item.blockPosition());
    return player;
  }

  public static Player getOwnerEntity(GameProfile owner, ServerLevel world, BlockPos pos) {
    Player player = null;
    if (!RailcraftConstantsAPI.UNKNOWN_PLAYER.equals(owner.getName()))
      player = PlayerUtil.getPlayer(world, owner);
    if (player == null)
      player = RailcraftFakePlayer.get(world, pos);
    return player;
  }

  public static Component getUsername(Level world, GameProfile gameProfile) {
    UUID playerId = gameProfile.getId();
    if (playerId != null) {
      Player player = world.getPlayerByUUID(playerId);
      if (player != null)
        return player.getDisplayName();
    }
    String username = gameProfile.getName();
    return new TextComponent(
        Strings.isEmpty(username) ? RailcraftConstantsAPI.UNKNOWN_PLAYER : username);
  }

  public static Component getUsername(Level world, @Nullable UUID playerId) {
    if (playerId != null) {
      Player player = world.getPlayerByUUID(playerId);
      if (player != null)
        return player.getDisplayName();
    }
    return new TextComponent(RailcraftConstantsAPI.UNKNOWN_PLAYER);
  }

  public static boolean isOwnerOrOp(GameProfile owner, Player player) {
    return player.getGameProfile().equals(owner) || player.hasPermissions(2);
  }

  public static boolean isSamePlayer(GameProfile a, GameProfile b) {
    if (a.getId() != null && b.getId() != null)
      return a.getId().equals(b.getId());
    return a.getName() != null && a.getName().equals(b.getName());
  }

  public static void swingArm(Player player, InteractionHand hand) {
    player.swing(hand);
  }

  public static boolean doesItemBlockActivation(Player player, InteractionHand hand) {
    if (player.isShiftKeyDown() || hand == InteractionHand.OFF_HAND)
      return true;
    ItemStack heldItem = player.getItemInHand(hand);
    if (!heldItem.isEmpty()) {
      return TrackTools.isRail(heldItem)
          || Annotations.isAnnotatedDeepSearch(ActivationBlockingItem.class, heldItem.getItem());
    }
    return false;
  }
}