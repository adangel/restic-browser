package org.adangel.resticbrowser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.List;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (args.length != 1) {
            System.err.println("Expected one argument - repository path");
            System.exit(1);
        }

        Path repoPath = Paths.get(args[0]);
        if (!Files.exists(repoPath)) {
            System.err.println("Repo path '" + repoPath + "' doesn't exist!");
            System.exit(1);
        }

        String password = System.getenv("RESTIC_PASSWORD");
        if (password == null) {
            System.err.println("Please provide the password in the env variable 'RESTIC_PASSWORD'.");
            System.exit(1);
        }

        Repository repository = new Repository(repoPath, password);

        System.out.println("Repository @ " + repository.getPath());
        System.out.println("id = " + repository.getId());
        System.out.println("snapshots:");
        System.out.println("ID\tTime\tHost\tTags\tPaths");
        repository.listSnapshots().forEach(snapshot -> {
            String id = snapshot.getString("id");
            String time = OffsetDateTime.parse(snapshot.getString("time"))
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT));;
            String host = snapshot.getString("hostname");
            List<String> tags = Collections.emptyList();
            if (snapshot.has("tags")) {
                tags = snapshot.getJSONArray("tags").toList().stream().map(Object::toString).toList();
            }
            List<String> paths = snapshot.getJSONArray("paths").toList().stream().map(Object::toString).toList();
            System.out.printf("%s\t%s\t%s\t%s\t%s%n", id, time, host, tags, paths);
        });
    }
}
