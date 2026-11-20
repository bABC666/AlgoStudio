package com.algostudio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectTest {
    @Test
    void applicationStylesheetIsPackaged() {
        assertNotNull(App.class.getResource("/style.css"), "启动界面的样式必须随应用打包");
    }
}
