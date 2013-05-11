package com.bergerkiller.bukkit.tc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.avaje.ebeaninternal.server.deploy.BeanDescriptor.EntityType;
import com.bergerkiller.bukkit.common.MaterialTypeProperty;
import com.bergerkiller.bukkit.common.conversion.Conversion;
import com.bergerkiller.bukkit.common.inventory.ItemParser;
import com.bergerkiller.bukkit.common.reflection.ClassTemplate;
import com.bergerkiller.bukkit.common.reflection.FieldAccessor;
import com.bergerkiller.bukkit.common.reflection.MethodAccessor;
import com.bergerkiller.bukkit.common.reflection.NMSClassTemplate;
import com.bergerkiller.bukkit.common.reflection.SafeField;
import com.bergerkiller.bukkit.common.reflection.classes.EntityPlayerRef;
import com.bergerkiller.bukkit.common.reflection.classes.VectorRef;
import com.bergerkiller.bukkit.common.reflection.classes.WorldRef;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.EntityUtil;
import com.bergerkiller.bukkit.common.utils.FaceUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;
import com.bergerkiller.bukkit.tc.properties.IParsable;
import com.bergerkiller.bukkit.tc.properties.IProperties;
import com.bergerkiller.bukkit.tc.properties.IPropertiesHolder;
import com.bergerkiller.bukkit.tc.utils.AveragedItemParser;

public class Util {
	public static final MaterialTypeProperty ISVERTRAIL = new MaterialTypeProperty(Material.LADDER);
	public static final MaterialTypeProperty ISTCRAIL = new MaterialTypeProperty(ISVERTRAIL, MaterialUtil.ISRAILS, MaterialUtil.ISPRESSUREPLATE);
	private static final String SEPARATOR_REGEX = "[|/\\\\]";

	public static void setItemMaxSize(Material material, int maxstacksize) {
		SafeField.set(Conversion.toItemHandle.convert(material), "maxStackSize", maxstacksize);
	}

	/**
	 * Splits a text into separate parts delimited by the separator characters
	 * 
	 * @param text to split
	 * @param limit of the split text
	 * @return split parts
	 */
	public static String[] splitBySeparator(String text) {
		return text.split(SEPARATOR_REGEX);
	}

	/**
	 * Gets the BlockFace.UP or BlockFace.DOWN based on a boolean input
	 * 
	 * @param up - True to get UP, False to get DOWN
	 * @return UP or DOWN
	 */
	public static BlockFace getVerticalFace(boolean up) {
		return up ? BlockFace.UP : BlockFace.DOWN;
	}

	/**
	 * Snaps a block face to one of the 8 possible radial block faces (NESW/NE/etc.)
	 * 
	 * @param face to snap to a nearby valid face
	 * @return Snapped block face
	 */
	public static BlockFace snapFace(BlockFace face) {
		switch (face) {
			case NORTH_NORTH_EAST:
				return BlockFace.NORTH_EAST;
			case EAST_NORTH_EAST:
				return BlockFace.EAST;
			case EAST_SOUTH_EAST:
				return BlockFace.SOUTH_EAST;
			case SOUTH_SOUTH_EAST:
				return BlockFace.SOUTH;
			case SOUTH_SOUTH_WEST:
				return BlockFace.SOUTH_WEST;
			case WEST_SOUTH_WEST:
				return BlockFace.WEST;
			case WEST_NORTH_WEST:
				return BlockFace.NORTH_WEST;
			case NORTH_NORTH_WEST:
				return BlockFace.NORTH;
			default: 
				return face;
		}
	}

	private static BlockFace[] possibleFaces = {BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
	private static List<Block> blockbuff = new ArrayList<Block>();
	public static List<Block> getSignsFromRails(Block railsblock) {
		return getSignsFromRails(blockbuff, railsblock);
	}
	
	public static List<Block> getSignsFromRails(List<Block> rval, Block railsblock) {
		rval.clear();
		addSignsFromRails(rval, railsblock);
		return rval;
	}

	public static void addSignsFromRails(List<Block> rval, Block railsblock) {
		if (ISVERTRAIL.get(railsblock)) {
			BlockFace dir = getVerticalRailDirection(railsblock.getData());
			railsblock = railsblock.getRelative(dir);
			// Loop into the direction to find signs
			while (true) {
				if (addAttachedSigns(railsblock, rval)) {
					railsblock = railsblock.getRelative(dir);
				} else {
					break;
				}
			}
		} else {
			//ignore mid-sections
			railsblock = railsblock.getRelative(BlockFace.DOWN);
			addAttachedSigns(railsblock, rval);
			railsblock = railsblock.getRelative(BlockFace.DOWN);
			//loop downwards
			while (true) {
				if (railsblock.getTypeId() == Material.SIGN_POST.getId()) {
					rval.add(railsblock);
					railsblock = railsblock.getRelative(BlockFace.DOWN);
				} else if (addAttachedSigns(railsblock, rval)) {
					railsblock = railsblock.getRelative(BlockFace.DOWN);
				} else {
					break;
				}
			}
		}
	}

	public static boolean hasAttachedSigns(final Block middle) {
		return addAttachedSigns(middle, null);
	}
	public static boolean addAttachedSigns(final Block middle, final Collection<Block> rval) {
		boolean found = false;
		Block b;
		for (BlockFace face : FaceUtil.AXIS) {
			b = middle.getRelative(face);
			if (b.getTypeId() == Material.WALL_SIGN.getId()) {
				if (BlockUtil.getAttachedFace(b) == face.getOppositeFace()) {
					if (rval != null) {
						rval.add(b);
					}
					found = true;
				}
			}
		}
		return found;
	}

	public static Block getRailsFromSign(Block signblock) {
		if (signblock == null) {
			return null;
		}

		final int id = signblock.getTypeId();
		final Block mainBlock;
		if (id == Material.WALL_SIGN.getId()) {
			mainBlock = BlockUtil.getAttachedBlock(signblock);
		} else if (id == Material.SIGN_POST.getId()) {
			mainBlock = signblock.getRelative(BlockFace.UP);
		} else {
			return null;
		}
		boolean hasSigns;
		for (BlockFace dir : possibleFaces) {
			Block block = mainBlock;
			hasSigns = true;
			while (true) {
				// Go to the next block
				block = block.getRelative(dir);

				// Check for rails
				if (dir == BlockFace.UP ? ISTCRAIL.get(block) : ISVERTRAIL.get(block)) {
					return block;
				}

				// End of the loop?
				if (!hasSigns) {
					break;
				}

				// Go to the next block
				hasSigns = hasAttachedSigns(block);
			}
		}
		return null;
	}

	public static Block findRailsVertical(Block from, BlockFace mode) {
		int sy = from.getY();
		int x = from.getX();
		int z = from.getZ();
		World world = from.getWorld();
		if (mode == BlockFace.DOWN) {
			for (int y = sy - 1; y > 0; --y) {
				if (ISTCRAIL.get(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		} else if (mode == BlockFace.UP) {
			int height = world.getMaxHeight();
			for (int y = sy + 1; y < height; y++) {
				if (ISTCRAIL.get(world.getBlockTypeIdAt(x, y, z))) {
					return world.getBlockAt(x, y, z);
				}
			}
		}
		return null;
	}

	public static ItemParser[] getParsers(String... items) {
		return getParsers(StringUtil.combine(";", items));
	}

	public static ItemParser[] getParsers(final String items) {
		List<ItemParser> parsers = new ArrayList<ItemParser>();
		int multiIndex, multiplier = -1;
		for (String type : items.split(";")) {
			type = type.trim();
			if (type.isEmpty()) {
				continue;
			}
			// Check to see whether this is a multiplier
			multiIndex = type.indexOf('#');
			if (multiIndex != -1) {
				multiplier = ParseUtil.parseInt(type.substring(0, multiIndex), -1);
				type = type.substring(multiIndex + 1);
			}
			// Parse the amount and a possible multiplier from it
			int amount = -1;
			int idx = StringUtil.firstIndexOf(type, "x", "X", " ", "*");
			if (idx > 0) {
				amount = ParseUtil.parseInt(type.substring(0, idx), -1);
				if (amount != -1) {
					type = type.substring(idx + 1);
				}
			}
			// Obtain the item parsers for this type and amount, apply a possible multiplier
			ItemParser[] keyparsers = TrainCarts.plugin.getParsers(type, amount);
			if (multiIndex != -1) {
				// Convert to proper multiplied versions
				for (int i = 0; i < keyparsers.length; i++) {
					keyparsers[i] = new AveragedItemParser(keyparsers[i], multiplier);
				}
			}
			// Add the parsers
			parsers.addAll(Arrays.asList(keyparsers));
		}
		if (parsers.isEmpty()) {
			parsers.add(new ItemParser(null));
		}
		return parsers.toArray(new ItemParser[0]);
	}

	public static Block getRailsBlock(Block from) {
		if (ISTCRAIL.get(from)) {
			return from;
		} else {
			from = from.getRelative(BlockFace.DOWN);
			return ISTCRAIL.get(from) ? from : null;
		}
	}

	/**
	 * Parses a long time value to a readable time String
	 * 
	 * @param time to parse
	 * @return time in the hh:mm:ss format
	 */
	public static String getTimeString(long time) {
		if (time == 0) {
			return "00:00:00";
		}
		time = (long) Math.ceil(0.001 * time); // msec -> sec
		int seconds = (int) (time % 60);
		int minutes = (int) ((time % 3600) / 60);
		int hours = (int) (time / 3600);
		StringBuilder rval = new StringBuilder(8);
		// Hours
		if (hours < 10) {
			rval.append('0');
		}
		rval.append(hours).append(':');
		// Minutes
		if (minutes < 10) {
			rval.append('0');
		}
		rval.append(minutes).append(':');
		// Seconds
		if (seconds < 10) {
			rval.append('0');
		}
		rval.append(seconds);
		return rval.toString();
	}

	private static boolean isRailsAt(Block block, BlockFace direction) {
		return getRailsBlock(block.getRelative(direction)) != null;
	}

	/**
	 * This will return:
	 * South or west if it's a straight piece
	 * Self if it is a cross-intersection
	 */
	public static BlockFace getPlateDirection(Block plate) {
		boolean s = isRailsAt(plate, BlockFace.NORTH) || isRailsAt(plate, BlockFace.SOUTH);
		boolean w = isRailsAt(plate, BlockFace.EAST) || isRailsAt(plate, BlockFace.WEST);
		if (s && w) {
			return BlockFace.SELF;
		} else if (w) {
			return BlockFace.EAST;
		} else if (s) {
			return BlockFace.SOUTH;
		} else {
			return BlockFace.SELF;
		}
	}

	/**
	 * Checks if a given rail is sloped
	 * 
	 * @param railsData of the rails
	 * @return True if sloped, False if not
	 */
	public static boolean isSloped(int railsData) {
		railsData &= 0x7;
		return railsData >= 0x2 && railsData <= 0x5;
	}

	/**
	 * Checks if a given rails block has a vertical rail above facing the direction specified
	 * 
	 * @param rails to check
	 * @param direction of the vertical rail
	 * @return True if a vertical rail is above, False if not
	 */
	public static boolean isVerticalAbove(Block rails, BlockFace direction) {
		Block above = rails.getRelative(BlockFace.UP);
		return Util.ISVERTRAIL.get(above) && getVerticalRailDirection(above.getData()) == direction;
	}

	/**
	 * Gets the direction a vertical rail pushes the minecart (the wall side)
	 * 
	 * @param raildata of the vertical rail
	 * @return the direction the minecart is pushed
	 */
	public static BlockFace getVerticalRailDirection(int raildata) {
		switch (raildata) {
			case 0x2:
				return BlockFace.SOUTH;
			case 0x3:
				return BlockFace.NORTH;
			case 0x4:
				return BlockFace.EAST;
			default:
			case 0x5:
				return BlockFace.WEST;
		}
	}

	public static int getOperatorIndex(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (isOperator(text.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	public static boolean isOperator(char character) {
		return LogicUtil.containsChar(character, '!', '=', '<', '>');
	}

	/**
	 * Gets if a given Entity can be a passenger of a Minecart
	 * 
	 * @param entity to check
	 * @return True if it can be a passenger, False if not
	 */
	public static boolean canBePassenger(Entity entity) {
		return entity instanceof LivingEntity;
	}

	public static boolean matchText(Collection<String> textValues, String expression) {
		if (textValues.isEmpty() || expression.isEmpty()) {
			return false;
		} else if (expression.startsWith("!")) {
			return !matchText(textValues, expression.substring(1));
		} else {
			String[] elements = expression.split("\\*");
			boolean first = expression.startsWith("*");
			boolean last = expression.endsWith("*");
			for (String text : textValues) {
				if (matchText(text, elements, first, last)) {
					return true;
				}
			}
			return false;
		}
	}
	public static boolean matchText(String text, String expression) {
		if (expression.isEmpty()) {
			return false;
		} else if (expression.startsWith("!")) {
			return !matchText(text, expression.substring(1));
		} else {
			return matchText(text, expression.split("\\*"), expression.startsWith("*"), expression.endsWith("*"));
		}
	}
	public static boolean matchText(String text, String[] elements, boolean firstAny, boolean lastAny) {
		if (elements == null|| elements.length == 0) {
			return true;
		}
		int index = 0;
		boolean has = true;
		boolean first = true;
		for (int i = 0; i < elements.length; i++) {
			if (elements[i].length() == 0) continue;
			index = text.indexOf(elements[i], index);
			if (index == -1 || (first && !firstAny && index != 0)) {
				has = false;
				break;
			} else {
				index += elements[i].length();
			}
			first = false;
		}
		if (has) {
			if (lastAny || index == text.length()) {
				return true;
			}
		}
		return false;
	}

	public static boolean evaluate(double value, String text) {
		if (text == null || text.isEmpty()) {
			return false; //no valid input
		}
		int idx = getOperatorIndex(text);
		if (idx == -1) {
			return value > 0; //no operators, just perform a 'has'
		} else {
			text = text.substring(idx);
		}
		if (text.startsWith(">=") || text.startsWith("=>")) {
			return value >= ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("<=") || text.startsWith("=<")) {
			return value <= ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("==")) {
			return value == ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith("!=") || text.startsWith("<>") || text.startsWith("><")) {
			return value != ParseUtil.parseDouble(text.substring(2), 0.0);
		} else if (text.startsWith(">")) {
			return value > ParseUtil.parseDouble(text.substring(1), 0.0);
		} else if (text.startsWith("<")) {
			return value < ParseUtil.parseDouble(text.substring(1), 0.0);
		} else if (text.startsWith("=")) {
			return value == ParseUtil.parseDouble(text.substring(1), 0.0);
		} else {
			return false;
		}
	}

	public static boolean canInstantlyBuild(Entity entity) {
		return entity instanceof HumanEntity && EntityUtil.getAbilities((HumanEntity) entity).canInstantlyBuild();
	}

	public static boolean isSupported(Block block) {
		return MaterialUtil.ISSOLID.get(BlockUtil.getAttachedBlock(block));
	}

	public static boolean isValidEntity(String entityName) {
		try {
			return EntityType.valueOf(entityName) != null;
		} catch (Exception ex) {
			return false;
		}
	}

	public static Vector parseVector(String text, Vector def) {
		String[] offsettext = splitBySeparator(text);
		Vector offset = new Vector();
		if (offsettext.length == 3) {
			offset.setX(ParseUtil.parseDouble(offsettext[0], 0.0));
			offset.setY(ParseUtil.parseDouble(offsettext[1], 0.0));
			offset.setZ(ParseUtil.parseDouble(offsettext[2], 0.0));
		} else if (offsettext.length == 2) {
			offset.setX(ParseUtil.parseDouble(offsettext[0], 0.0));
			offset.setZ(ParseUtil.parseDouble(offsettext[1], 0.0));
		} else if (offsettext.length == 1) {
			offset.setY(ParseUtil.parseDouble(offsettext[0], 0.0));
		} else {
			return def;
		}
		if (offset.length() > TrainCarts.maxEjectDistance) {
			offset.normalize().multiply(TrainCarts.maxEjectDistance);
		}
		return offset;
	}

	public static boolean parseProperties(IParsable properties, String key, String args) {
		IProperties prop;
		IPropertiesHolder holder;
		if (properties instanceof IPropertiesHolder) {
			holder = ((IPropertiesHolder) properties);
			prop = holder.getProperties();
		} else if (properties instanceof IProperties) {
			prop = (IProperties) properties;
			holder = prop.getHolder();
		} else {
			return false;
		}
		if (holder == null) {
			return prop.parseSet(key, args);
		} else if (prop.parseSet(key, args) || holder.parseSet(key, args))  {
			holder.onPropertiesChanged();
			return true;
		} else {
			return false;
		}
	}

	/*
	 * The below code will be moved to BKCommonLib during 1.5.2 - DO NOT FORGET!
	 */
	private static final ClassTemplate<?> movingObjectPosTemplate = NMSClassTemplate.create("MovingObjectPosition");
	private static final Class<?> vec3DClass = CommonUtil.getNMSClass("Vec3D");
	private static final MethodAccessor<Object> worldRayTrace = WorldRef.TEMPLATE.getMethod("rayTrace", vec3DClass, vec3DClass, boolean.class);
	private static final FieldAccessor<Integer> mobObjPosX = movingObjectPosTemplate.getField("b");
	private static final FieldAccessor<Integer> mobObjPosY = movingObjectPosTemplate.getField("c");
	private static final FieldAccessor<Integer> mobObjPosZ = movingObjectPosTemplate.getField("d");
	private static final MethodAccessor<Float> entityPlayerGetheadHeight = EntityPlayerRef.TEMPLATE.getMethod("getHeadHeight");

	public static Block rayTrace(World world, double startX, double startY, double startZ, double endX, double endY, double endZ) {
		Object startVec = VectorRef.newVec(startX, startY, startZ);
		Object endVec = VectorRef.newVec(endX, endY, endZ);
		Object movingObjectPosition = worldRayTrace.invoke(Conversion.toWorldHandle.convert(world), startVec, endVec, false);
		if (movingObjectPosition == null) {
			return null;
		}
		return world.getBlockAt(mobObjPosX.get(movingObjectPosition), mobObjPosY.get(movingObjectPosition), mobObjPosZ.get(movingObjectPosition));
	}

	public static Block rayTrace(Location startLocation, Vector direction, double maxLength) {
		final double startX = startLocation.getX();
		final double startY = startLocation.getY();
		final double startZ = startLocation.getZ();
		final double endX = startX + direction.getX() * maxLength;
		final double endY = startY + direction.getY() * maxLength;
		final double endZ = startZ + direction.getZ() * maxLength;
		return rayTrace(startLocation.getWorld(), startX, startY, startZ, endX, endY, endZ);
	}

	public static Block rayTrace(Location startLocation, double maxLength) {
		return rayTrace(startLocation, startLocation.getDirection(), maxLength);
	}

	public static Block rayTrace(Player player) {
		return rayTrace(player.getLocation().add(0.0, entityPlayerGetheadHeight.invoke(Conversion.toEntityHandle.convert(player)), 0.0), 5.0);
	}
}
