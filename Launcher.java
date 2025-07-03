import java.awt.*;
import java.awt.event.*;

public class Launcher {
    public static void main(String[] args) {
        // If Robocode Tank Royale supplies the server URL and secret on the
        // command line, skip the UI and start the bot immediately.
        if (args.length >= 2) {
            new PlayerBot(args[0], args[1]).start();
            return;
        }

        Frame frame = new Frame("PlayerBot Launcher");
        frame.setLayout(new GridLayout(3, 2));

        Label urlLabel = new Label("Server address:");
        TextField urlField = new TextField("ws://localhost:7654");
        Label secretLabel = new Label("Server secret:");
        TextField secretField = new TextField("");
        Button connectButton = new Button("Connect");
        Label statusLabel = new Label("");

        frame.add(urlLabel);
        frame.add(urlField);
        frame.add(secretLabel);
        frame.add(secretField);
        frame.add(connectButton);
        frame.add(statusLabel);

        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        frame.setVisible(true);

        connectButton.addActionListener(e -> {
            connectButton.setEnabled(false);
            statusLabel.setText("Connecting...");
            new Thread(() -> {
                try {
                    PlayerBot bot = new PlayerBot(urlField.getText(), secretField.getText());
                    bot.start();
                    frame.dispose();
                } catch (Exception ex) {
                    statusLabel.setText("Error: " + ex.getMessage());
                    connectButton.setEnabled(true);
                }
            }).start();
        });
    }
}
