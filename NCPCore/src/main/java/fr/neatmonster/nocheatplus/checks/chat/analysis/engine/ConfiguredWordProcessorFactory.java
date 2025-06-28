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
package fr.neatmonster.nocheatplus.checks.chat.analysis.engine;

import java.util.ArrayList;
import java.util.List;

import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.FlatWords;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.SimilarWordsBKL;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordPrefixes;
import fr.neatmonster.nocheatplus.checks.chat.analysis.engine.processors.WordProcessor;

/**
 * Default implementation creating processors based on {@link EnginePlayerConfig}.
 */
public class ConfiguredWordProcessorFactory implements WordProcessorFactory {

    @Override
    public List<WordProcessor> createProcessors(final EnginePlayerConfig config) {
        final List<WordProcessor> list = new ArrayList<WordProcessor>(3);
        if (config.ppWordsCheck) {
            list.add(new FlatWords("ppWords", config.ppWordsSettings));
        }
        if (config.ppPrefixesCheck) {
            list.add(new WordPrefixes("ppPrefixes", config.ppPrefixesSettings));
        }
        if (config.ppSimilarityCheck) {
            list.add(new SimilarWordsBKL("ppSimilarity", config.ppSimilaritySettings));
        }
        return list;
    }
}
