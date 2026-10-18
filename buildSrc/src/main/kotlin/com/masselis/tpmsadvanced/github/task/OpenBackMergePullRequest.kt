package com.masselis.tpmsadvanced.github.task

import com.masselis.tpmsadvanced.github.GithubApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal

/**
 * Opens (or reuses) a pull request merging [source] into [target] and enables GitHub's auto-merge
 * with a merge-commit strategy, so a production push automatically flows back into develop
 * without a human remembering to do it. The pull request's head is a disposable branch created on
 * [source]'s head commit, so [source] itself is never deleted once the pull request is merged.
 *
 * REST has no "enable auto-merge" endpoint, and no way to pin the merge method at all - only the
 * `enablePullRequestAutoMerge` GraphQL mutation supports `mergeMethod: MERGE` (as opposed to
 * squash/rebase, which would each retire one of the two branches). Both calls use authenticated HTTPS without subprocess credentials or redirects.
 */
internal abstract class OpenBackMergePullRequest : DefaultTask() {

    @get:Internal
    abstract val githubToken: Property<String>

    @get:Input
    abstract val repository: Property<String>

    private val api: GithubApi get() = GithubApi(githubToken.get(), repository.get())

    @get:Input
    abstract val source: Property<String>

    @get:Input
    abstract val target: Property<String>

    init {
        group = "publishing"
        description =
            "Opens a pull request merging \"source\" into \"target\" with merge-commit auto-merge enabled"
    }

    @TaskAction
    internal fun process() = api.request(
        "GET",
        "/git/ref/heads/${GithubApi.encode(source.get())}",
    )
        .let { Json.decodeFromString<JsonObject>(it) }
        .let { sourceRef ->
            sourceRef["object"]
                ?.jsonObject["sha"]
                ?.jsonPrimitive
                ?.contentOrNull
                ?: error("Failed to resolve the head commit of \"${source.get()}\": $sourceRef")
        }
        .let { sourceSha ->
            // The pull request never uses `source` as its head: once merged, GitHub's "automatically
            // delete head branches" setting would delete it (rulesets don't stop it, the deletion
            // runs as the auto-merge enabler who may bypass them). A disposable branch pinned on
            // the same commit produces the exact same merge commit and is free to be deleted.
            "chore/back-merge-${source.get()}-${sourceSha.take(SHORT_SHA_LENGTH)}".also { head ->
                api.request(
                    "POST",
                    "/git/refs",
                    JsonObject(
                        mapOf(
                            "ref" to JsonPrimitive("refs/heads/$head"),
                            "sha" to JsonPrimitive(sourceSha),
                        )
                    ).toString(),
                    acceptedErrors = setOf(422),
                )
                    .let { Json.decodeFromString<JsonObject>(it) }
                    .also { refCreationResponse ->
                        // A re-run of the job for the same `source` commit finds the branch already there
                        check(
                            refCreationResponse.containsKey("ref") ||
                                    refCreationResponse["message"]?.jsonPrimitive?.contentOrNull == "Reference already exists"
                        ) { "Failed to create the back-merge branch \"$head\": $refCreationResponse" }
                    }
            }
        }
        .let { head ->
            api.request(
                "POST",
                "/pulls",
                JsonObject(
                    mapOf(
                        "title" to JsonPrimitive("chore(gitflow): back-merge ${source.get()} into ${target.get()}"),
                        "head" to JsonPrimitive(head),
                        "base" to JsonPrimitive(target.get()),
                        "body" to JsonPrimitive(
                            "Automated git-flow back-merge of `${source.get()}` through the disposable " +
                                    "branch `$head`. Merge as a merge commit only."
                        ),
                    )
                ).toString(),
                acceptedErrors = setOf(422),
            )
                .let { Json.decodeFromString<JsonObject>(it) }
                .let { prCreationResponse ->
                    val errorMessages = prCreationResponse["errors"]
                        ?.jsonArray
                        .orEmpty()
                        .mapNotNull { it.jsonObject["message"]?.jsonPrimitive?.content }
                    when {
                        prCreationResponse["node_id"]?.jsonPrimitive?.contentOrNull != null ->
                            prCreationResponse["node_id"]?.jsonPrimitive?.content

                        errorMessages.any { "No commits between" in it } -> {
                            logger.lifecycle("Nothing to back-merge: \"${source.get()}\" and \"${target.get()}\" are already in sync")
                            api.request("DELETE", "/git/refs/heads/${GithubApi.encode(head)}")
                            null
                        }

                        errorMessages.any { "A pull request already exists" in it } ->
                            api.request(
                                "GET",
                                "/pulls?head=${GithubApi.encode("${api.owner}:$head")}&base=${GithubApi.encode(target.get())}&state=open",
                            ).let { Json.decodeFromString<JsonArray>(it) }
                                .let { prSearchResponse ->
                                    prSearchResponse.singleOrNull()
                                        ?.jsonObject["node_id"]
                                        ?.jsonPrimitive
                                        ?.content
                                        ?: error("Failed to find the existing pull request: $prSearchResponse")
                                }

                        else -> error("Failed to open the back-merge pull request: $prCreationResponse")
                    }
                }
        }
        ?.let { pullRequestNodeId ->
            api.graphql(
                JsonObject(
                    mapOf(
                        "query" to JsonPrimitive(
                            """
                                        mutation {
                                            enablePullRequestAutoMerge(input: {pullRequestId: ${JsonPrimitive(pullRequestNodeId)}, mergeMethod: MERGE}) {
                                                pullRequest { number }
                                            }
                                        }
                                        """.trimIndent()
                        )
                    )
                ).toString(),
            )
        }
        ?.also { autoMergeResponse ->
            // GraphQL returns HTTP 200 even on a logical failure - HTTP success proves
            // nothing, the "errors" field must be checked explicitly.
            check(Json.decodeFromString<JsonObject>(autoMergeResponse).containsKey("errors").not()) {
                "Failed to enable auto-merge: $autoMergeResponse"
            }
        }

    private companion object {
        private const val SHORT_SHA_LENGTH = 7
    }
}
