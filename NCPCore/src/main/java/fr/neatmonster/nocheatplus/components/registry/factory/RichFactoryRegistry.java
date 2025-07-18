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
package fr.neatmonster.nocheatplus.components.registry.factory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.data.IDataOnRemoveSubCheckData;
import fr.neatmonster.nocheatplus.components.registry.meta.RichTypeSetRegistry;
import fr.neatmonster.nocheatplus.utilities.CheckTypeUtil;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;

/**
 * Thread safe "read" factory registry with additional convenience
 * functionality. (Registration might not be fully thread-safe.)
 * <hr>
 * Thread safety further depends on the registered factories for fetching new
 * instances.
 * 
 * @author asofold
 *
 * @param <A>
 *            Factory argument type.
 */
public class RichFactoryRegistry<A> extends RichTypeSetRegistry implements IRichFactoryRegistry<A> {

    public static class CheckRemovalSpec {

        public final Collection<Class<?>> completeRemoval = new LinkedHashSet<Class<?>>();
        public final Collection<Class<? extends IDataOnRemoveSubCheckData>> subCheckRemoval = new LinkedHashSet<Class<? extends IDataOnRemoveSubCheckData>>();
        public final Collection<CheckType> checkTypes;

        public CheckRemovalSpec(final CheckType checkType, 
                final boolean withDescendantCheckTypes, 
                final IRichFactoryRegistry<?> factoryRegistry
                ) {
            this(withDescendantCheckTypes 
                    ? CheckTypeUtil.getWithDescendants(checkType)
                            : Collections.singletonList(checkType), factoryRegistry);
        }

        public CheckRemovalSpec(final Collection<CheckType> checkTypes, 
                final IRichFactoryRegistry<?> factoryRegistry) {
            this.checkTypes = checkTypes;
            for (final CheckType refType : checkTypes) {
                completeRemoval.addAll(factoryRegistry.getGroupedTypes(
                        IData.class, refType));
                subCheckRemoval.addAll(factoryRegistry.getGroupedTypes(
                        IDataOnRemoveSubCheckData.class, refType));
            }
            if (checkTypes.contains(CheckType.ALL)) {
                completeRemoval.addAll(factoryRegistry.getGroupedTypes(
                        IData.class));
                subCheckRemoval.addAll(factoryRegistry.getGroupedTypes(
                        IDataOnRemoveSubCheckData.class));
            }
        }
    }

    private final Lock lock;
    private final FactoryOneRegistry<A> factoryRegistry;
    @SuppressWarnings("unchecked")
    private Set<Class<?>> autoGroups = Collections.EMPTY_SET;


    public RichFactoryRegistry(final Lock lock) {
        super(lock);
        this.lock = lock;
        factoryRegistry = new FactoryOneRegistry<A>(
                lock, CheckUtils.primaryServerThreadContextTester);
    }

    @Override
    public <T> T getNewInstance(final Class<T> registeredFor, final A arg) {
        return factoryRegistry.getNewInstance(registeredFor, arg);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> void registerFactory(final Class<T> registerFor,
            final IFactoryOne<A, T> factory) {
        lock.lock();
        try {
            factoryRegistry.registerFactory(registerFor, factory);
            for (final Class<?> groupType: autoGroups) {
                if (groupType.isAssignableFrom(registerFor)) {
                    addToGroups(registerFor, List.of((Class<? super T>) groupType));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <G> void createAutoGroup(final Class<G> groupType) {
        lock.lock();
        try {
            createGroup(groupType);
            final Set<Class<?>> autoGroups = new LinkedHashSet<Class<?>>(this.autoGroups);
            autoGroups.add(groupType);
            this.autoGroups = autoGroups;
        } finally {
            lock.unlock();
        }
    }

}
