package com.algostudio.model.table;

public record TableCell(int row, int column) {
    public TableCell { if (row < 0 || column < 0) throw new IllegalArgumentException("单元格行列下标不能为负。"); }
}
