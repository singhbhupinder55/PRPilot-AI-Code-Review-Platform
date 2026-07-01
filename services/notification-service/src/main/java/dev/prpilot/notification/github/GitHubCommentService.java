package dev.prpilot.notification.github;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Posts review comments to GitHub PRs via the GitHub REST API.
 *
 * Uses a Personal Access Token (PAT) with repo scope.
 * In a production system this would be replaced with a GitHub App
 * installation token for better security and rate limits.
 */
@Service
@Slf4j
public class GitHubCommentService {

    private final RestClient restClient;

    public GitHubCommentService(
            @Value("${prpilot.github.token}") String token,
            @Value("${prpilot.github.api-url}") String apiUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Posts a comment on a GitHub PR.
     *
     * @param repoFullName e.g. "singhbhupinder55/real-time-notification-system"
     * @param prNumber     the PR number
     * @param body         the comment markdown body
     */
    public void postPrComment(String repoFullName, Long prNumber, String body) {
        String url = "/repos/" + repoFullName + "/issues/" + prNumber + "/comments";

        log.info("Posting review comment to {}/pull/{}", repoFullName, prNumber);

        GitHubCommentRequest request = new GitHubCommentRequest(
                "## 🤖 PRPilot AI Review\n\n" + body);

        restClient.post()
                .uri(url)
                .body(request)
                .retrieve()
                .toBodilessEntity();

        log.info("Successfully posted comment to {}/pull/{}", repoFullName, prNumber);
    }

    private record GitHubCommentRequest(String body) {}
}