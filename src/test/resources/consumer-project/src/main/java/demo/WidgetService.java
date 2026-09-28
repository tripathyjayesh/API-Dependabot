package demo;

import org.springframework.stereotype.Service;

@Service
public class WidgetService {
    public String display(Widget widget) {
        return widget.getName();
    }
}
