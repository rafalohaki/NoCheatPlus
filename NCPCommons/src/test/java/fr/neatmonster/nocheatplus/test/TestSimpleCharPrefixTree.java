package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.ds.prefixtree.SimpleCharPrefixTree;

/**
 * A test suite for the {@link SimpleCharPrefixTree} class.
 * It verifies the core functionalities like prefix matching by word and by character sequence.
 */
@DisplayName("SimpleCharPrefixTree Tests")
public class TestSimpleCharPrefixTree {

    // --- Test Data Providers ---

    private static final List<String> feedData = List.of("op", "op dummy", "ncp info");
    private static final List<String> uniqueNumbers = List.of("123456", "2345678", "34567", "456789");

    static Stream<String> shouldMatchByWordOrPrefix() {
        return Stream.of("op", "op dummy", "ncp info", "ncp info test");
    }

    static Stream<String> shouldNotMatchByWord() {
        return Stream.of("opp", "opp dummy", "op dummy2", "ncp", "ncp dummy");
    }

    static Stream<String> shouldNotMatchByPrefix() {
        return Stream.of("ok", "ncp", "ncp dummy", "ncp inf");
    }

    static Stream<String> provideUniqueNumbers() {
        return uniqueNumbers.stream();
    }
    
    /**
     * Tests for the hasPrefixWords() method, which checks if an input starts with
     * one of the complete phrases stored in the tree.
     */
    @Nested
    @DisplayName("hasPrefixWords() method")
    class HasPrefixWordsMethod {
        private SimpleCharPrefixTree tree;

        @BeforeEach
        void setUp() {
            tree = new SimpleCharPrefixTree();
            tree.feedAll(feedData, false, true);
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#shouldMatchByWordOrPrefix")
        @DisplayName("should return true for inputs that start with a known phrase")
        void shouldReturnTrueForMatchingPrefixWords(String input) {
            assertTrue(tree.hasPrefixWords(input),
                "Expected input '" + input + "' to be matched as having a prefix word.");
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#shouldNotMatchByWord")
        @DisplayName("should return false for inputs that do not start with a known phrase")
        void shouldReturnFalseForNonMatchingPrefixWords(String input) {
            assertFalse(tree.hasPrefixWords(input),
                "Expected input '" + input + "' not to be matched by a prefix word.");
        }
    }

    /**
     * Tests for the hasPrefix() method, which performs a classic character-by-character
     * prefix check against the tree's contents.
     */
    @Nested
    @DisplayName("hasPrefix() method")
    class HasPrefixMethod {
        private SimpleCharPrefixTree tree;

        @BeforeEach
        void setUp() {
            tree = new SimpleCharPrefixTree();
            tree.feedAll(feedData, false, true);
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#shouldMatchByWordOrPrefix")
        @DisplayName("should return true for inputs that are prefixed by a stored sequence")
        void shouldReturnTrueForMatchingPrefixes(String input) {
            assertTrue(tree.hasPrefix(input),
                "Expected input '" + input + "' to be matched as having a prefix.");
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#shouldNotMatchByPrefix")
        @DisplayName("should return false for inputs that are not prefixed by a stored sequence")
        void shouldReturnFalseForNonMatchingPrefixes(String input) {
            assertFalse(tree.hasPrefix(input),
                "Expected input '" + input + "' not to be matched by a prefix.");
        }

        @Test
        @DisplayName("should correctly identify a prefix for a longer, unseen word")
        void shouldIdentifyPrefixOfLongerWord() {
            assertTrue(tree.hasPrefix("ncp infocrabs"),
                "'ncp info' should be a prefix of 'ncp infocrabs'.");
        }
    }

    /**
     * Tests using the prefix tree to effectively perform suffix matching
     * by reversing the strings before insertion and lookup.
     */
    @Nested
    @DisplayName("when used for suffix matching")
    class SuffixMatching {
        private SimpleCharPrefixTree prefixTree;
        private SimpleCharPrefixTree suffixTree;

        @BeforeEach
        void setUp() {
            prefixTree = new SimpleCharPrefixTree();
            prefixTree.feedAll(uniqueNumbers, false, true);

            suffixTree = new SimpleCharPrefixTree();
            uniqueNumbers.forEach(s -> suffixTree.feed(StringUtil.reverse(s)));
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#provideUniqueNumbers")
        @DisplayName("should match original strings in prefix tree but not in suffix tree")
        void shouldMatchOriginalStringsInPrefixTreeOnly(String original) {
            assertTrue(prefixTree.hasPrefix(original), "Original string should match the prefix tree.");
            assertFalse(suffixTree.hasPrefix(original), "Original string should NOT match the suffix tree.");
        }

        @ParameterizedTest
        @MethodSource("fr.neatmonster.nocheatplus.test.TestSimpleCharPrefixTree#provideUniqueNumbers")
        @DisplayName("should match reversed strings in suffix tree but not in prefix tree")
        void shouldMatchReversedStringsInSuffixTreeOnly(String original) {
            String reversed = StringUtil.reverse(original);
            assertTrue(suffixTree.hasPrefix(reversed), "Reversed string should match the suffix tree.");
            assertFalse(prefixTree.hasPrefix(reversed), "Reversed string should NOT match the prefix tree.");
        }
    }
}