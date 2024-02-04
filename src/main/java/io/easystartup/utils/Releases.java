package io.easystartup.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @author indianBond
 */
public class Releases {

    private static final String GITHUB_DELIM_LINKS = ",";
    private static final Pattern GITHUB_LINK_REGEX = Pattern.compile("<(?<link>[^>]+)>; rel=\"(?<rel>[^\"]+)\"");

    /**
     * Returns in asc sorted order. So index 0 will contain version 1.x.x
     * and index 10 might contain version 2.x.x
     * */
    public List<String> availableReleases() {
        try {
            String RELEASES_FILENAME = getReleaseFileName(new Date());
            File file = new File(RELEASES_FILENAME);
            if (file.exists()) {
                Yaml yaml = new Yaml();
                return yaml.load(new FileInputStream(file));
            }

            Thread.startVirtualThread(() -> deleteExistingFilesExceptLatest(RELEASES_FILENAME));

            System.out.println("\nFetching latest k3s releases from the internet ...\n");

            List<String> releases = fetchAllReleasesFromGithub();
            Yaml yaml = new Yaml();
            yaml.dump(releases, new FileWriter(RELEASES_FILENAME));
            return releases;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches releases from k3s-io repo from github
     * And returns in asc sorted order. So index 0 will contain version 1.x.x
     * and index 10 might contain version 2.x.x
     * */
    private List<String> fetchAllReleasesFromGithub() throws IOException {
        List<String> releases = new ArrayList<>();
        String nextPageUrl = "https://api.github.com/repos/k3s-io/k3s/tags?per_page=100";

        while (nextPageUrl != null) {
            HttpURLConnection conn = (HttpURLConnection) new URL(nextPageUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            String link = conn.getHeaderField("Link");

            releases.addAll(getReleaseNames(conn, releases));

            if (conn.getInputStream() != null) {
                IOUtils.consume(conn.getInputStream());
            }

            nextPageUrl = extractNextGithubPageUrl(link);
        }

        Collections.reverse(releases);
        return releases;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GithubTagsResponse {
        private String name;

        public GithubTagsResponse() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private String extractNextGithubPageUrl(String linkHeader) {
        if (linkHeader == null || linkHeader.isEmpty()) {
            return null;
        }

        String[] links = linkHeader.split(GITHUB_DELIM_LINKS);
        for (String link : links) {
            Matcher matcher = GITHUB_LINK_REGEX.matcher(link.trim());
            if (matcher.find()) {
                String rel = matcher.group("rel");
                if ("next".equals(rel)) {
                    return matcher.group("link");
                }
            }
        }

        return null;
    }

    private List<String> getReleaseNames(HttpURLConnection conn, List<String> releases) throws IOException {
        List<String> releaseNames = new ArrayList<>();
        String response = IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
        TypeReference<List<GithubTagsResponse>> listTypeReference = new TypeReference<>() {
        };
        List<GithubTagsResponse> githubTagsResponses = new ObjectMapper().readValue(response, listTypeReference);
        githubTagsResponses.forEach(val -> {
            releaseNames.add(val.getName());
        });
        return releaseNames;
    }

    private String getReleaseFileName(Date date) {
        SimpleDateFormat mmDd = new SimpleDateFormat("yyyy_MM_dd");
        String format = mmDd.format(date);
        return "/tmp/k3s-simplified-releases_" + format + ".yaml";
    }

    private void deleteExistingFilesExceptLatest(String RELEASES_FILENAME) {
        try {
            String directoryPath = "/tmp";
            String regexPattern = "k3s-simplified-releases_.*.yaml";
            File directory = new File(directoryPath);
            File[] files = directory.listFiles();
            if (files == null) {
                return;
            }
            Pattern pattern = Pattern.compile(regexPattern);
            for (File file : files) {
                if (file.getName().equalsIgnoreCase(RELEASES_FILENAME)) {
                    continue;
                }
                if (!pattern.matcher(file.getName()).matches()) {
                    continue;
                }
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}
