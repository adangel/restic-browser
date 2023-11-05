package org.adangel.resticbrowser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

import org.adangel.resticbrowser.fuse.ResticFS;

public class GUI {

    public static void main(String[] args) {
        Logger logger = Logger.getLogger("org.adangel.resticbrowser");
        logger.setLevel(Level.FINE);
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            handler.setLevel(Level.FINE);
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            Logger.getLogger(GUI.class.getName()).log(Level.WARNING, "Couldn't set native look and feel", e);
        }

        JFrame mainframe = new JFrame("restic-browser");
        JPanel container = new JPanel();
        BoxLayout layout = new BoxLayout(container, BoxLayout.Y_AXIS);
        container.setLayout(layout);

        JLabel info = new JLabel(" ");

        JPanel repoPath = new JPanel();
        JTextField repoPathField = new JTextField(15);
        JButton repoPathChooser = new JButton("...");
        repoPathChooser.addActionListener((event) -> {
            JFileChooser chooser = new JFileChooser(Path.of(".").toAbsolutePath().toFile());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int ret = chooser.showOpenDialog(mainframe);
            if (ret == JFileChooser.APPROVE_OPTION) {
                repoPathField.setText(chooser.getSelectedFile().toString());
            }
        });
        repoPath.add(repoPathField);
        repoPath.add(repoPathChooser);
        container.add(new JLabel("RESTIC_REPOSITORY"));
        container.add(repoPath);

        container.add(new JLabel("RESTIC_PASSWORD"));
        JPasswordField repoPassword = new JPasswordField(15);
        container.add(repoPassword);

        AtomicReference<WebServer> ws = new AtomicReference<>();
        JButton webserver = new JButton("Start webserver");
        JButton stopWebserver = new JButton("Stop webserver");
        stopWebserver.setEnabled(false);
        stopWebserver.addActionListener(e -> {
            ws.get().stop();
            ws.set(null);
            webserver.setEnabled(true);
            stopWebserver.setEnabled(false);
            info.setText("Webserver stopped");
        });
        webserver.addActionListener(e -> {
            try {
                WebServer server = new WebServer(8080, Path.of(repoPathField.getText()), repoPassword.getPassword());
                server.start();
                ws.set(server);
                info.setText("Webserver started at http://localhost:8080");
                webserver.setEnabled(false);
                stopWebserver.setEnabled(true);
            } catch (IOException ex) {
                info.setText(ex.toString());
                throw new RuntimeException(ex);
            }
        });
        JPanel webserverButtons = new JPanel();
        webserverButtons.add(webserver);
        webserverButtons.add(stopWebserver);
        container.add(webserverButtons);

        container.add(new JLabel("Mountpoint"));
        JPanel mountPath = new JPanel();
        JTextField mountPathField = new JTextField("/tmp/fuse-restic", 15);
        JButton mountPathChooser = new JButton("...");
        mountPathChooser.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int ret = chooser.showOpenDialog(mainframe);
            if (ret == JFileChooser.APPROVE_OPTION) {
                mountPathField.setText(chooser.getSelectedFile().toString());
            }
        });
        mountPath.add(mountPathField);
        mountPath.add(mountPathChooser);
        container.add(mountPath);
        AtomicReference<ResticFS> fs = new AtomicReference<>();
        JButton mount = new JButton("Mount");
        JButton umount = new JButton("Umount");
        umount.setEnabled(false);
        mount.addActionListener(e -> {
            try {
                fs.set(new ResticFS(Path.of(repoPathField.getText()), Map.of("RESTIC_PASSWORD", new String(repoPassword.getPassword()))));
                fs.get().mount(Path.of(mountPathField.getText()));
                info.setText("Mounted at " + mountPathField.getText());
                mount.setEnabled(false);
                umount.setEnabled(true);
            } catch (IOException ex) {
                info.setText(ex.toString());
                throw new RuntimeException(ex);
            }
        });
        umount.addActionListener(e -> {
            fs.get().umount();
            fs.set(null);
            info.setText("Unmounted");
            mount.setEnabled(true);
            umount.setEnabled(false);
        });
        JPanel mountButtons = new JPanel();
        mountButtons.add(mount);
        mountButtons.add(umount);
        container.add(mountButtons);

        container.add(info);

        mainframe.setContentPane(container);
        mainframe.pack();
        mainframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainframe.setVisible(true);
    }
}
