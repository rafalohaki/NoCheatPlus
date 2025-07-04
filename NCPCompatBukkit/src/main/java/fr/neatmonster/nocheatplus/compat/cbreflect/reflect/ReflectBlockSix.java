/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.compat.cbreflect.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.compat.cbreflect.reflect.ReflectHelper.ReflectFailureException;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Classic Block with 6 consecutive getter methods for the shape and
 * updateShape.
 * 
 * @author asofold
 *
 */
public class ReflectBlockSix implements IReflectBlock {

    /** Obfuscated nms names, allowing to find the order in the source code under certain circumstances. */
    private static final List<String> possibleNames = new ArrayList<String>();
    private static final java.util.Map<String, Integer> possibleNameIndices = new java.util.HashMap<String, Integer>();

    static {
        // These might suffice for a while.
        int index = 0;
        for (char c : "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()) {
            String name = "" + c;
            possibleNames.add(name);
            possibleNameIndices.put(name, index++);
        }
    }

    // Reference.
    private final ReflectBlockPosition reflectBlockPosition;

    /** (static) */
    public final Method nmsGetById;
    public final Object nmsById;

    public final Method nmsGetMaterial;
    public final boolean useBlockPosition;


    public final Method nmsUpdateShape;
    // Block bounds in the order the methods (used to) appear in the nms class.
    /** If this is null, all other nmsGetMin/Max... methods are null too. */
    public final Method nmsGetMinX;
    public final Method nmsGetMaxX;
    public final Method nmsGetMinY;
    public final Method nmsGetMaxY;
    public final Method nmsGetMinZ;
    public final Method nmsGetMaxZ;

    public ReflectBlockSix(ReflectBase base, ReflectBlockPosition reflectBlockPosition) {
        this.reflectBlockPosition = reflectBlockPosition;
        final Class<?> clazz;
        try {
            clazz = Class.forName(base.nmsPackageName + ".Block");
        } catch (ClassNotFoundException ex) {
            throw new ReflectFailureException();
        }
        // byID (static)
        nmsGetById = ReflectionUtil.getMethod(clazz, "getById", int.class);

        final Class<?> blockArray = Array.newInstance(clazz, 0).getClass();
        final Field byIdField = ReflectionUtil.getField(clazz, "byId", blockArray);
        nmsById = byIdField == null ? null : ReflectionUtil.get(byIdField, blockArray, null);

        // getMaterial
        nmsGetMaterial = ReflectionUtil.getMethodNoArgs(clazz, "getMaterial");
        // updateShape
        Method method = null;
        Class<?> clazzIBlockAccess;
        try {
            clazzIBlockAccess = Class.forName(base.nmsPackageName + ".IBlockAccess");
        } catch (ClassNotFoundException ex) {
            throw new ReflectFailureException();
        }
        if (reflectBlockPosition != null) {
            method = ReflectionUtil.getMethod(clazz, "updateShape", clazzIBlockAccess, reflectBlockPosition.nmsClass);
        }
        if (method == null) {
            method = ReflectionUtil.getMethod(clazz, "updateShape", clazzIBlockAccess, int.class, int.class, int.class);
            useBlockPosition = false;
        } else {
            useBlockPosition = true;
        }
        nmsUpdateShape = method;
        // Block bounds fetching. The array uses the order the methods (used to) appear in the nms class.
        String[] names = new String[] {"getMinX", "getMaxX", "getMinY", "getMaxY", "getMinZ", "getMaxZ"}; // FUTURE GUESS.
        Method[] methods = tryBoundsMethods(clazz, names);
        if (methods == null) {
            names = guessBoundsMethodNames(clazz);
            if (names != null) {
                methods = tryBoundsMethods(clazz, names);
            }
            if (methods == null) {
                methods = new Method[] {null, null, null, null, null, null};
            }
        }
        // Consider testing which method is which and allow configuration, also
        // store used ones to config by Minecraft version.
        // Dynamically test these? This might need an extra world or space to
        // place blocks in.
        if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_STATUS)) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().debug(Streams.INIT, "ReflectBlock: Use methods for shape: " + StringUtil.join(Arrays.asList(names), ", "));
        }
        this.nmsGetMinX = methods[0];
        this.nmsGetMaxX = methods[1];
        this.nmsGetMinY = methods[2];
        this.nmsGetMaxY = methods[3];
        this.nmsGetMinZ = methods[4];
        this.nmsGetMaxZ = methods[5];
    }

    /**
     * 
     * @param names
     * @return null on failure, otherwise the methods in order.
     */
    private Method[] tryBoundsMethods(Class<?> clazz, String[] names) {
        Method[] methods = new Method[6];
        for (int i = 0; i < 6; i++) {
            methods[i] = ReflectionUtil.getMethodNoArgs(clazz, names[i], double.class);
            if (methods[i] == null) {
                return null;
            }
        }
        return methods;
    }

    /**
     * Determine the getter method names for the block bounds. Candidate names
     * are taken from {@link #possibleNames} and sorted by their position within
     * that list. The assumption is that {@code possibleNames} contains every
     * possible obfuscated name used for these methods.
     *
     * @param clazz the Block class to inspect
     * @return the ordered method names or {@code null} if the order cannot be
     *         resolved
     */
    private String[] guessBoundsMethodNames(Class<?> clazz) {
        List<String> names = collectCandidateNames(clazz);
        if (names.size() < 6) {
            return null;
        }
        names.sort(Comparator.comparingInt(possibleNameIndices::get));
        int startIndex = findConsecutiveStart(names);
        if (startIndex == -1) {
            return null;
        }
        String[] res = new String[6];
        for (int i = 0; i < 6; i++) {
            res[i] = names.get(startIndex + i);
        }
        return res;
    }

    private List<String> collectCandidateNames(Class<?> clazz) {
        List<String> names = new ArrayList<String>();
        for (Method method : clazz.getMethods()) {
            boolean hasCorrectSignature = method.getReturnType() == double.class
                    && method.getParameterTypes().length == 0;
            if (hasCorrectSignature && possibleNames.contains(method.getName())) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private int findConsecutiveStart(List<String> names) {
        if (names.size() == 6) {
            return 0;
        }
        int startIndex = -1;
        int lastIndex = -2;
        int currentStart = -1;
        for (int i = 0; i < names.size(); i++) {
            Integer nameIndexObj = possibleNameIndices.get(names.get(i));
            if (nameIndexObj == null) {
                continue;
            }
            int nameIndex = nameIndexObj.intValue();
            if (nameIndex - lastIndex == 1) {
                if (currentStart == -1) {
                    currentStart = nameIndex - 1;
                } else {
                    int length = nameIndex - currentStart + 1;
                    if (length > 6) {
                        return -1;
                    } else if (length == 6) {
                        if (startIndex != -1) {
                            return -1;
                        }
                        startIndex = i + 1 - length;
                    }
                }
            } else {
                currentStart = -1;
            }
            lastIndex = nameIndex;
        }
        return startIndex;
    }

    /**
     * Quick fail with exception.
     */
    private void fail() {
        throw new ReflectFailureException();
    }

    private Object nmsBlockPosition(final int x, final int y, final int z) {
        if (!this.useBlockPosition || this.reflectBlockPosition.new_nmsBlockPosition == null) {
            fail();
        }
        Object blockPos = ReflectionUtil.newInstance(this.reflectBlockPosition.new_nmsBlockPosition, x, y, z);
        if (blockPos == null) {
            fail();
        }
        return blockPos;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object nms_getByMaterial(final Material id) {
        if (this.nmsById != null) {
            return Array.get(nmsById, id.getId());
        }
        if (this.nmsGetById == null) {
            fail();
        }
        return ReflectionUtil.invokeMethod(this.nmsGetById, null, id.getId());
    }

    @Override
    public Object nms_getMaterial(final Object block) {
        if (this.nmsGetMaterial == null) {
            fail();
        }
        return ReflectionUtil.invokeMethodNoArgs(this.nmsGetMaterial, block);
    }

    public void nms_updateShape(final Object block, final Object iBlockAccess, 
            final int x, final int y, final int z) {
        if (this.nmsUpdateShape == null) {
            fail();
        }
        if (this.useBlockPosition) {
            ReflectionUtil.invokeMethod(this.nmsUpdateShape, block, iBlockAccess, nmsBlockPosition(x, y, z));
        } else {
            ReflectionUtil.invokeMethod(this.nmsUpdateShape, block, iBlockAccess, x, y, z);
        }
    }

    @Override
    public double[] nms_fetchBounds(final Object nmsWorld, final Object nmsBlock,
            final int x, final int y, final int z) {
        nms_updateShape(nmsBlock, nmsWorld, x, y, z);
        // The invoked methods might return null; consider adding try-catch
        // handling here.
        return new double[] {
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMinX, nmsBlock)).doubleValue(),
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMinY, nmsBlock)).doubleValue(),
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMinZ, nmsBlock)).doubleValue(),
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMaxX, nmsBlock)).doubleValue(),
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMaxY, nmsBlock)).doubleValue(),
                ((Number) ReflectionUtil.invokeMethodNoArgs(this.nmsGetMaxZ, nmsBlock)).doubleValue(),
        };
    }

    @Override
    public boolean isFetchBoundsAvailable() {
        return (nmsGetById != null || nmsById != null) && nmsUpdateShape != null && nmsGetMinX != null;
    }

}
