package demo;

import org.junit.jupiter.api.Test;

class WidgetServiceTest {
    @Test
    void displayUsesWidgetName() {
        Widget widget = new Widget();
        org.junit.jupiter.api.Assertions.assertEquals("demo", widget.getName());
    }
}
