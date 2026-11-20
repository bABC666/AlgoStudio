package com.algostudio.model;

import com.algostudio.model.frame.*;
import com.algostudio.model.table.TableCell;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TableFrameTest {
    @Test void nestedRowsLabelsAndHighlightSetsCannotPolluteHistory() {
        var row = new ArrayList<>(List.of(0, -1)); var values = new ArrayList<List<Integer>>(List.of(row, new ArrayList<>(List.of(0, 1))));
        var labels = new ArrayList<>(List.of("0", "1")); var dependencies = new HashSet<>(Set.of(new TableCell(0, 1)));
        var path = new HashSet<>(Set.of(new TableCell(1, 0)));
        var frame = frame(labels, labels, values, new TableCell(1, 1), dependencies, path);
        row.set(1, 99); values.clear(); labels.set(0, "changed"); dependencies.clear(); path.clear();
        assertEquals(List.of(List.of(0, -1), List.of(0, 1)), frame.values()); assertEquals(List.of("0", "1"), frame.rowLabels());
        assertEquals(Set.of(new TableCell(0, 1)), frame.dependencies()); assertEquals(Set.of(new TableCell(1, 0)), frame.path());
        assertThrows(UnsupportedOperationException.class, () -> frame.values().getFirst().set(0, 7));
        assertThrows(UnsupportedOperationException.class, () -> frame.values().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.rowLabels().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.columnLabels().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.dependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> frame.path().clear());
    }
    @Test void rejectsEmptyRaggedMismatchedOrMissingLabelsAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> simple(List.of(), List.of("0"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of("0"), List.of(), List.of(List.of())));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of("0", "1"), List.of("0"), List.of(List.of(0))));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of("0", "1"), List.of("0"), List.of(List.of(0), List.of(0, 1))));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of(" "), List.of("0"), List.of(List.of(0))));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of("0"), List.of(""), List.of(List.of(0))));
        assertThrows(IllegalArgumentException.class, () -> simple(List.of("0"), List.of("0"), List.of(List.of(-2))));
    }
    @Test void rejectsOutOfTableCurrentDependencyAndPathReferences() {
        assertThrows(IllegalArgumentException.class, () -> new TableCell(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TableCell(0, -1));
        var labels = List.of("0"); var values = List.of(List.of(0)); var invalid = new TableCell(1, 0);
        assertThrows(IllegalArgumentException.class, () -> frame(labels, labels, values, invalid, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> frame(labels, labels, values, null, Set.of(invalid), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> frame(labels, labels, values, null, Set.of(), Set.of(invalid)));
    }
    @Test void pendingIsDifferentFromZeroAndRepeatedCharacterLabelsAreAllowed() {
        var frame = simple(List.of("A", "A"), List.of("B", "B"), List.of(List.of(-1, 0), List.of(0, 1)));
        assertEquals(-1, frame.values().getFirst().getFirst()); assertEquals(0, frame.values().getFirst().get(1));
        assertEquals(2, frame.rowLabels().size());
    }
    private static TableFrame simple(List<String> rows, List<String> columns, List<List<Integer>> values) { return frame(rows, columns, values, null, Set.of(), Set.of()); }
    private static TableFrame frame(List<String> rows, List<String> columns, List<List<Integer>> values, TableCell current, Set<TableCell> deps, Set<TableCell> path) {
        return new TableFrame("行", "列", rows, columns, values, current, deps, path, EventKind.INITIAL, AlgorithmMetrics.ZERO, "表格示意", null);
    }
}
