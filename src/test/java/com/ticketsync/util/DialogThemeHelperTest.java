package com.ticketsync.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogThemeHelperTest {

    @Test
    void apply_addsSharedStylesheetAndDialogClassOnce() {
        ObservableList<String> stylesheets = FXCollections.observableArrayList();
        ObservableList<String> styleClasses = FXCollections.observableArrayList("dialog-pane");

        DialogThemeHelper.apply(stylesheets, styleClasses);
        DialogThemeHelper.apply(stylesheets, styleClasses);

        assertEquals(1, stylesheets.size());
        assertEquals(AppTheme.stylesheetUrl(), stylesheets.getFirst());
        assertTrue(styleClasses.contains("dialog-pane"));
        assertTrue(styleClasses.contains("app-dialog-pane"));
        assertEquals(2, styleClasses.size());
    }
}
