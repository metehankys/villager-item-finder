package com.mkys.client.finder;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Finds where the selected villager's lectern is / should go.
 *
 * <p>The important rule for trading halls where villagers stand right next to each other:
 * a spot is only accepted if the <b>selected villager is clearly the closest villager</b> to it.
 * Otherwise a neighbouring unemployed villager could claim the lectern instead.
 */
public final class LecternLocator {
	/** The selected villager must be at least this much (blocks) closer than any other villager. */
	private static final double EXCLUSIVITY_MARGIN = 0.5;
	private static final double OTHER_VILLAGER_SCAN = 8.0;

	private LecternLocator() {
	}

	/** Where to click to place a block at {@code target}. */
	public record Placement(BlockPos target, BlockPos clickedPos, Direction clickedFace, Vec3 hitVec) {
		public BlockHitResult toHitResult() {
			return new BlockHitResult(hitVec, clickedFace, clickedPos, false);
		}
	}

	/** Finds an existing lectern that clearly belongs to (is closest to) the given villager. */
	public static @Nullable BlockPos findExistingLectern(Level level, Villager villager) {
		BlockPos base = villager.blockPosition();
		List<Villager> others = otherVillagers(level, villager);
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 2, 2))) {
			if (level.getBlockState(pos).getBlock() != Blocks.LECTERN) {
				continue;
			}
			if (!isExclusiveTo(pos, villager, others)) {
				continue;
			}
			double score = anchor(pos).distanceToSqr(villager.position());
			if (score < bestScore) {
				bestScore = score;
				best = pos.immutable();
			}
		}
		return best;
	}

	/**
	 * Finds the best free spot next to the villager where a lectern can be placed by the player.
	 * Adjacent spots (including diagonals) are tried first; a 2-block radius is the fallback.
	 */
	public static @Nullable Placement findPlacement(Level level, LocalPlayer player, Villager villager) {
		BlockPos base = villager.blockPosition();
		List<Villager> others = otherVillagers(level, villager);

		for (int radius = 1; radius <= 2; radius++) {
			Placement best = null;
			double bestScore = Double.MAX_VALUE;

			for (int dy : new int[]{0, -1, 1}) {
				for (int dx = -radius; dx <= radius; dx++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (dx == 0 && dz == 0) {
							continue; // the villager's own column
						}
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
							continue; // ring only; inner ring was handled by previous radius
						}
						BlockPos pos = base.offset(dx, dy, dz);
						if (!isFreeForLectern(level, pos)) {
							continue;
						}
						if (!isExclusiveTo(pos, villager, others)) {
							continue;
						}
						Placement placement = placementFor(level, player, pos);
						if (placement == null) {
							continue;
						}

						double score = anchor(pos).distanceTo(villager.position()) * 10.0;
						score += switch (dy) { // feet level is the classic setup, then one below, then head level
							case 0 -> 0;
							case -1 -> 2;
							default -> 6;
						};
						if (dx != 0 && dz != 0) {
							score += 3; // prefer straight in front over diagonal
						}
						if (!hasLineOfSight(level, player, placement)) {
							score += 4;
						}
						score += player.getEyePosition().distanceTo(placement.hitVec()) * 0.1;

						if (score < bestScore) {
							bestScore = score;
							best = placement;
						}
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	/**
	 * Computes how the player can place a block at {@code target}: picks a solid neighbouring
	 * block face within reach (block below preferred). Returns null if impossible.
	 */
	public static @Nullable Placement placementFor(Level level, LocalPlayer player, BlockPos target) {
		Vec3 eye = player.getEyePosition();
		double reach = player.blockInteractionRange() - 0.25;
		Placement best = null;
		double bestScore = Double.MAX_VALUE;

		Direction[] order = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
		for (int i = 0; i < order.length; i++) {
			Direction toNeighbour = order[i];
			BlockPos neighbour = target.relative(toNeighbour);
			BlockState state = level.getBlockState(neighbour);
			if (state.canBeReplaced() || state.getCollisionShape(level, neighbour).isEmpty()) {
				continue;
			}
			Vec3 hit = Vec3.atCenterOf(target).relative(toNeighbour, 0.5);
			double dist = eye.distanceTo(hit);
			if (dist > reach) {
				continue;
			}
			Placement p = new Placement(target, neighbour, toNeighbour.getOpposite(), hit);
			double score = i + (hasLineOfSight(level, player, p) ? 0 : 10);
			if (score < bestScore) {
				bestScore = score;
				best = p;
			}
		}
		return best;
	}

	public static boolean isFreeForLectern(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
			return false;
		}
		// Placement fails if a mob or the player stands in the block.
		return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty();
	}

	private static boolean hasLineOfSight(Level level, LocalPlayer player, Placement placement) {
		Vec3 eye = player.getEyePosition();
		// Aim slightly into the clicked block so the ray actually ends on it.
		Vec3 end = placement.hitVec().relative(placement.clickedFace().getOpposite(), 0.05);
		BlockHitResult result = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(placement.clickedPos());
	}

	private static List<Villager> otherVillagers(Level level, Villager selected) {
		return level.getEntitiesOfClass(Villager.class, selected.getBoundingBox().inflate(OTHER_VILLAGER_SCAN),
				v -> v != selected && v.isAlive());
	}

	/** True if the selected villager is clearly closer to {@code pos} than every other villager. */
	private static boolean isExclusiveTo(BlockPos pos, Villager selected, List<Villager> others) {
		Vec3 a = anchor(pos);
		double mine = a.distanceTo(selected.position());
		for (Villager other : others) {
			if (a.distanceTo(other.position()) < mine + EXCLUSIVITY_MARGIN) {
				return false;
			}
		}
		return true;
	}

	private static Vec3 anchor(BlockPos pos) {
		return Vec3.atBottomCenterOf(pos);
	}
}
