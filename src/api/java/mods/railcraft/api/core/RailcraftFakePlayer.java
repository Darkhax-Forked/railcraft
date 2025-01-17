/*------------------------------------------------------------------------------
 Copyright (c) Railcraft Reborn, 2023+

 This work (the API) is licensed under the "MIT" License,
 see LICENSE.md for details.
 -----------------------------------------------------------------------------*/
package mods.railcraft.api.core;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;

public final class RailcraftFakePlayer {
  private RailcraftFakePlayer() {}

  public static final GameProfile RAILCRAFT_USER_PROFILE =
      new GameProfile(UUID.nameUUIDFromBytes(RailcraftConstants.RAILCRAFT_PLAYER.getBytes()),
          RailcraftConstants.RAILCRAFT_PLAYER);

  public static ServerPlayer get(final ServerLevel world, final double x, final double y,
      final double z) {
    ServerPlayer player = FakePlayerFactory.get(world, RAILCRAFT_USER_PROFILE);
    player.setPos(x, y, z);
    return player;
  }

  public static ServerPlayer get(final ServerLevel world, final BlockPos pos) {
    ServerPlayer player = FakePlayerFactory.get(world, RAILCRAFT_USER_PROFILE);
    player.setPos(pos.getX(), pos.getY(), pos.getZ());
    return player;
  }

  public static ServerPlayer get(ServerLevel world, double x, double y, double z,
      ItemStack stack, InteractionHand hand) {
    ServerPlayer player = get(world, x, y, z);
    player.setItemInHand(hand, stack);
    return player;
  }

  public static ServerPlayer get(ServerLevel world, BlockPos pos, ItemStack stack,
      InteractionHand hand) {
    ServerPlayer player = get(world, pos);
    player.setItemInHand(hand, stack);
    return player;
  }
}
