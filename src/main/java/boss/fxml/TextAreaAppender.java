package boss.fxml;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class TextAreaAppender extends AppenderBase<ILoggingEvent> {
    private static TextArea textArea;

    public static void setTextArea(TextArea textArea) {
        TextAreaAppender.textArea = textArea;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (textArea != null) {
            String logMessage = event.getFormattedMessage() + "\n";
            
            // 使用Platform.runLater确保在JavaFX线程上更新UI
            Platform.runLater(() -> {
                textArea.appendText(logMessage);
                // 自动滚动到底部
                textArea.setScrollTop(Double.MAX_VALUE);
            });
        }
    }
} 