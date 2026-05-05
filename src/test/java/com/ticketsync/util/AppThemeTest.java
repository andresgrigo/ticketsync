package com.ticketsync.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppThemeTest {

    @Test
    void stylesheetUrl_resolvesSharedApplicationStylesheet() {
        String stylesheetUrl = AppTheme.stylesheetUrl();

        assertNotNull(stylesheetUrl);
        assertTrue(stylesheetUrl.endsWith("/com/ticketsync/application.css"),
                "shared stylesheet should resolve from the application resources");
    }

    @Test
    void applyStylesheet_addsSharedStylesheetOnlyOnce() {
        ObservableList<String> stylesheets = FXCollections.observableArrayList();

        AppTheme.applyStylesheet(stylesheets);
        AppTheme.applyStylesheet(stylesheets);

        assertEquals(1, stylesheets.size());
        assertEquals(AppTheme.stylesheetUrl(), stylesheets.getFirst());
    }
}
