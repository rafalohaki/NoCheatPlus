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
package fr.neatmonster.nocheatplus.utilities;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Auxiliary methods for dealing with reflection.
 * 
 * @author asofold
 *
 */
public class ReflectionUtil {

    private static final Logger LOG =
            Logger.getLogger(ReflectionUtil.class.getName());

    /**
     * Convenience method to check if members exist and fail if not. This checks
     * getField(...) == null.
     * 
     * @param prefix
     * @param specs
     * @throws RuntimeException
     *             If any member is not present.
     */
    public static void checkMembers(String prefix, String[]... specs){
        try {
            for (String[] spec : specs){
                Class<?> clazz = Class.forName(prefix + spec[0]);
                for (int i = 1; i < spec.length; i++){
                    if (clazz.getField(spec[i]) == null) {
                        throw new NoSuchFieldException(prefix + spec[0] + " : " + spec[i]);
                    }
                }
            }
        } catch (SecurityException e) {
            LOG.log(Level.FINE, "Security exception during member check", e);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Convenience method to check if members exist and fail if not. This checks
     * getField(...) == null.
     * 
     * @param clazz
     *            The class for which to check members for.
     * @param type
     *            The expected type of fields.
     * @param fieldNames
     *            The field names.
     * @throws RuntimeException
     *             If any member is not present or of wrong type.
     */
    public static void checkMembers(Class<?> clazz, Class<?> type, String... fieldNames){
        try {
            for (String fieldName : fieldNames){
                Field field = clazz.getField(fieldName);
                if (field == null) {
                    throw new NoSuchFieldException(clazz.getName() + "." + fieldName + " does not exist.");
                }
                else if (field.getType() != type) {
                    throw new NoSuchFieldException(
                            clazz.getName() + "." + fieldName
                                    + " has wrong type: " + field.getType());
                }
            }
        } catch (SecurityException e) {
            LOG.log(Level.FINE, "Security exception during member check", e);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Check for the given names if the method returns the desired type of
     * result (exact check).
     * 
     * @param methodNames
     * @param returnType
     * @throws RuntimeException
     *             If one method is not existing or not matching return type or
     *             has arguments.
     */
    public static void checkMethodReturnTypesNoArgs(Class<?> objClass, String[] methodNames, Class<?> returnType){
        // Potential enhancement: verify the method is static.
        // Consider supporting overloaded methods.
        try {
            for (String methodName : methodNames){
                Method m = objClass.getMethod(methodName);
                if (m.getParameterTypes().length != 0) {
                    throw new RuntimeException(
                            "Expect method without arguments for " + objClass.getName()
                                    + "." + methodName);
                }
                if (m.getReturnType() != returnType){
                    throw new RuntimeException("Wrong return type for: " + objClass.getName() + "." + methodName);
                }
            }
        } catch (SecurityException e) {
            LOG.log(Level.FINE, "Security exception during method return type check", e);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Dirty method to call a declared method with a generic parameter type.
     * Does try+catch for method invocation and should not throw anything for
     * the normal case. Purpose for this is generic factory registration, having
     * methods with type Object alongside methods with more specialized types.
     * 
     * @param obj
     * @param methodName
     * @param arg
     *            Argument or invoke the method with.
     * @return null in case of errors (can not be distinguished).
     */
    public static Object invokeGenericMethodOneArg(final Object obj, final String methodName, final Object arg){
        // A dedicated helper might exist for this operation.
        final Class<?> objClass = obj.getClass();
        final Class<?> argClass = arg.getClass();
        Method candidate = null;
        Method objectMethod = null;
        for (final Method method : objClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 1) {
                    if (parameterTypes[0] == Object.class) {
                        objectMethod = method;
                    }
                    if (parameterTypes[0].isAssignableFrom(argClass)) {
                        if (candidate == null || candidate.getParameterTypes()[0].isAssignableFrom(parameterTypes[0])) {
                            candidate = method;
                        }
                    }
                }
            }
        }
        final Method target = candidate != null ? candidate : objectMethod;
        if (target != null) {
            try {
                return target.invoke(obj, arg);
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed invoking method " + methodName + " on " + objClass, t);
                return null;
            }
        }
        LOG.warning("Method " + methodName + " not found for class " + objClass);
        return null;
    }

    /**
     * Invoke a method without arguments, get the method matching the return
     * types best, i.e. first type is preferred. At present a result is
     * returned, even if the return type does not match at all.
     * 
     * @param obj
     * @param methodName
     * @param returnTypePreference
     *            Most preferred return type first, might return null, might
     *            return a method with a completely different return type,
     *            comparison with ==, no isAssignableForm. Is this really intended?
     * @return
     */
    public static Object invokeMethodNoArgs(final Object obj,
            final String methodName,
            final Class<?>... returnTypePreference) {
        // This could potentially be replaced by a one-line convenience call.
        final Class<?> objClass = obj.getClass();
        // Try to get it directly first.
        Method methodFound = getMethodNoArgs(objClass, methodName, returnTypePreference);
        if (methodFound == null){
            // Fall-back to seek it.
            methodFound = seekMethodNoArgs(objClass, methodName, returnTypePreference);
        }
        // Invoke if found.
        if (methodFound != null) {
            try {
                return methodFound.invoke(obj);
            } catch (Throwable t) {
                LOG.log(Level.WARNING,
                        "Failed invoking method " + methodName + " on " + objClass,
                        t);
                return null;
            }
        } else {
            LOG.warning("Method " + methodName + " not found for class " + objClass);
            return null;
        }
    }

    /**
     * More fail-safe method invocation.
     * 
     * @param method
     * @param object
     * @return null in case of failures (!).
     */
    public static Object invokeMethodNoArgs(Method method, Object object) {
        try {
            return method.invoke(object);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            LOG.log(Level.FINE, "Failed reflective invocation", e);
            return null;
        }
    }

    /**
     * Fail-safe call.
     * 
     * @param method
     * @param object
     * @param arguments
     * @return null in case of errors.
     */
    public static Object invokeMethod(Method method, Object object, Object... arguments) {
        try {
            return method.invoke(object, arguments);
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            LOG.log(Level.FINE, "Failed reflective invocation", e);
            return null;
        }
    }

    /**
     * Direct getMethod attempt.
     * 
     * @param objClass
     * @param methodName
     * @param returnTypePreference
     * @return
     */
    public static Method getMethodNoArgs(final Class<?> objClass,
            final String methodName,
            final Class<?>... returnTypePreference) {
        try {
            final Method methodFound = objClass.getMethod(methodName);
            if (methodFound != null) {
                if (returnTypePreference == null || returnTypePreference.length == 0) {
                    return methodFound;
                }
                final Class<?> returnType = methodFound.getReturnType();
                for (int i = 0; i < returnTypePreference.length; i++){
                    if (returnType == returnTypePreference[i]){
                        return methodFound;
                    }
                }
            }
        } catch (SecurityException e) {
            LOG.log(Level.FINE, "Security exception while retrieving method", e);
        } catch (NoSuchMethodException e) {
            LOG.log(Level.FINE, "Method not found", e);
        }
        return null;
    }

    /**
     * Iterate over all methods, attempt to return best matching return type
     * (earliest in array).
     * 
     * @param objClass
     * @param methodName
     * @param returnTypePreference
     * @return
     */
    public static Method seekMethodNoArgs(final Class<?> objClass, final String methodName, 
            final Class<?>[] returnTypePreference) {
        return seekMethodNoArgs(objClass, methodName, false, returnTypePreference);
    }

    /**
     * Iterate over all methods, attempt to return best matching return type
     * (earliest in array).
     * 
     * @param objClass
     * @param methodName
     * @param returnTypePreference
     * @return
     */
    public static Method seekMethodIgnoreArgs(final Class<?> objClass, final String methodName, 
            final Class<?>... returnTypePreference) {
        return seekMethodNoArgs(objClass, methodName, true, returnTypePreference);
    }

    private static Method seekMethodNoArgs(final Class<?> objClass, final String methodName, 
            boolean ignoreArgs, final Class<?>... returnTypePreference) {
        // Collect methods that might work.
        Method methodFound = null;
        int returnTypeIndex = returnTypePreference.length; // This can be 0 for no preferences given.
        // Consider caching methods retrieved by name to improve performance.
        for (final Method method : objClass.getMethods()){
            if (method.getName().equals(methodName)){
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (ignoreArgs || parameterTypes.length == 0){
                    // Override the found method if none found yet or if the return type matches the preferred policy.
                    final Class<?> returnType = method.getReturnType();
                    if (methodFound == null){
                        methodFound = method;
                        for (int i = 0; i < returnTypeIndex; i++){
                            if (returnTypePreference[i] == returnType){
                                returnTypeIndex = i;
                                break;
                            }
                        }
                    }
                    else{
                        // Check if the return type is preferred over previously found ones.
                        for (int i = 0; i < returnTypeIndex; i++){
                            if (returnTypePreference[i] == returnType){
                                methodFound = method;
                                returnTypeIndex = i;
                                break;
                            }
                        }
                    }
                    if (returnTypeIndex == 0){
                        // "Quick" return.
                        break;
                    }
                }
            }
        }
        return methodFound;
    }

    /**
     * Get the field by name (and type). Failsafe.
     * 
     * @param clazz
     * @param fieldName
     * @param type
     *            Set to null to get any type of field.
     * @return Field or null.
     */
    public static Field getField(Class<?> clazz, String fieldName, Class<?> type) {
        try {
            Field field = clazz.getField(fieldName);
            if (type == null || field.getType() == type) {
                return field;
            }
        } catch (NoSuchFieldException | SecurityException e) {
            LOG.log(Level.FINE, "Failed to retrieve field", e);
        }
        return null;
    }

    /**
     * Set the field fail-safe.
     * 
     * @param field
     * @param object
     * @param value
     * @return
     */
    public static boolean set(Field field, Object object, Object value) {
        try {
            field.set(object, value);
            return true;
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to set field", e);
        }
        return false;
    }

    public static boolean getBoolean(Field field, Object object, boolean defaultValue) {
        try {
            return field.getBoolean(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to get boolean field", e);
        }
        return defaultValue;
    }

    public static int getInt(Field field, Object object, int defaultValue) {
        try {
            return field.getInt(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to get int field", e);
        }
        return defaultValue;
    }

    public static float getFloat(Field field, Object object, float defaultValue) {
        try {
            return field.getFloat(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to get float field", e);
        }
        return defaultValue;
    }

    public static double getDouble(Field field, Object object, double defaultValue) {
        try {
            return field.getDouble(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to get double field", e);
        }
        return defaultValue;
    }

    public static Object get(Field field, Object object, Object defaultValue) {
        try {
            return field.get(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            LOG.log(Level.FINE, "Failed to get field", e);
        }
        return defaultValue;
    }

    /**
     * Fail-safe getMethod.
     * 
     * @param clazz
     * @param methodName
     * @param arguments
     * @return null in case of errors.
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... arguments) {
        try {
            return clazz.getMethod(methodName, arguments);
        } catch (NoSuchMethodException | SecurityException e) {
            LOG.log(Level.FINE, "Failed to retrieve method", e);
        }
        return null;
    }

    /**
     * Get a method matching one of the declared argument specifications.
     * 
     * @param clazz
     * @param methodName
     * @param argumentLists
     * @return The first matching method (given order).
     */
    public static Method getMethod(Class<?> clazz, String methodName, Class<?>[]... argumentLists) {
        Method method = null;
        for (Class<?>[] arguments : argumentLists) {
            method = getMethod(clazz, methodName, arguments);
            if (method != null) {
                return method;
            }
        }
        return null;
    }

    /**
     * Fail-safe.
     * 
     * @param clazz
     * @param parameterTypes
     * @return null on errors.
     */
    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            return clazz.getConstructor(parameterTypes);
        } catch (NoSuchMethodException | SecurityException e) {
            LOG.log(Level.FINE, "Failed to retrieve constructor", e);
        }
        return null;
    }

    /**
     * Fail-safe.
     * 
     * @param constructor
     * @param arguments
     * @return null on errors.
     */
    public static Object newInstance(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (InstantiationException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            LOG.log(Level.FINE, "Failed to create instance", e);
            return null;
        }
    }

    /**
     * Fail-safe class getting.
     * @param fullName
     * @return
     */
    public static Class<?> getClass(String fullName) {
        try {
            return Class.forName(fullName);
        } catch (ClassNotFoundException e) {
            LOG.log(Level.FINE, "Class not found: " + fullName, e);
            return null;
        }
    }

    /**
     * Convenience for debugging: Print fields and methods with types separated
     * by line breaks. Probably not safe for production use.
     * 
     * @param clazz
     * @return
     */
    public static String getClassDescription(final Class<?> clazz) {
        // Future enhancement: allow sorting by member names.
        final StringBuilder builder = new StringBuilder(512);
        builder.append("Class: ");
        builder.append(clazz);
        // Pending support for superclass, interface, and generic details.
        for (final Field field : clazz.getFields()) {
            builder.append("\n  ");
            builder.append(getSimpleMemberModifierDescription(field));
            builder.append(field.getType().getName());
            builder.append(' ');
            builder.append(field.getName());

        }
        for (final Method method : clazz.getMethods()) {
            builder.append("\n  ");
            builder.append(getSimpleMemberModifierDescription(method));
            builder.append(method.getReturnType().getName());
            builder.append(' ');
            builder.append(method.getName());
            builder.append("(");
            for (Class<?> type : method.getParameterTypes()) {
                builder.append(type.getName());
                builder.append(", ");
            }
            builder.append(")");
        }
        return builder.toString();
    }

    private static String getSimpleMemberModifierDescription(final Member member) {
        final int mod = member.getModifiers();
        final StringBuilder out = new StringBuilder();
        out.append(Modifier.isPublic(mod) ? "(public" : "( -");
        if (member instanceof AccessibleObject) {
            AccessibleObject ao = (AccessibleObject) member;
            boolean accessible = false;
            try {
                if (Modifier.isStatic(mod)) {
                    accessible = ao.canAccess(null);
                }
            } catch (RuntimeException ignored) {
            }
            if (accessible) {
                out.append(" accessible");
            }
        }
        out.append(Modifier.isStatic(mod) ? " static) " : ") ");
        return out.toString();
    }

}
