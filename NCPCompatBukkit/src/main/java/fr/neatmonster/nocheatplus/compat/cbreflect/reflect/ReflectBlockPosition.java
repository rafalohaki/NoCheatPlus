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

import java.lang.reflect.Constructor;

import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class ReflectBlockPosition {
    
    public final Class<?> nmsClass;

    public final Constructor<?> new_nmsBlockPosition;

    public ReflectBlockPosition(ReflectBase base) {
        try {
            nmsClass = Class.forName(base.nmsPackageName + ".BlockPosition");
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
        new_nmsBlockPosition = ReflectionUtil.getConstructor(nmsClass, int.class, int.class, int.class);
    }

}
