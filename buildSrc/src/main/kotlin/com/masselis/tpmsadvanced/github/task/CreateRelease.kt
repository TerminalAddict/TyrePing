package com.masselis.tpmsadvanced.github.task

import CommitSha
import SemanticVersion
import com.android.build.api.variant.BuiltArtifactsLoader
import com.masselis.tpmsadvanced.github.GithubApi
import com.masselis.tpmsadvanced.github.valuesource.ReleaseNoteBetweenCommit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.from
import java.io.File
import javax.inject.Inject

internal abstract class CreateRelease : DefaultTask() {

    @get:Inject
    protected abstract val providerFactory: ProviderFactory

    @get:Internal
    abstract val githubToken: Property<String>

    @get:Input
    abstract val repository: Property<String>

    @get:Input
    abstract val tagName: Property<SemanticVersion>

    @get:Input
    abstract val preRelease: Property<Boolean>

    @get:Input
    abstract val lastReleaseCommitSha: Property<String>

    @get:InputFiles
    abstract val apkFolders: ListProperty<Directory>

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    private val releaseNotes
        get() = providerFactory.from(ReleaseNoteBetweenCommit::class) {
            fromCommitSha = lastReleaseCommitSha
            toCommitSha = providerFactory.from(CommitSha::class) { argument = "HEAD" }
        }

    init {
        group = "publishing"
        description = "Creates or updates a release on github"
    }

    @TaskAction
    internal fun process() {
        val api = GithubApi(githubToken.get(), repository.get())
        val apk = apkFolders.zip(builtArtifactsLoader) { folders, loader ->
            folders.flatMap { folder ->
                loader.load(folder)?.elements?.map { File(it.outputFile) }
                    ?: error("No APK built in $folder")
            }
        }.get().single()
        val response = api.request("POST", "/releases", JsonObject(
            mapOf(
                "tag_name" to JsonPrimitive(tagName.get().toString()),
                "body" to JsonPrimitive(releaseNotes.get()),
                "prerelease" to JsonPrimitive(preRelease.get()),
            )
        ).toString())
        val releaseId = Json.decodeFromString<JsonObject>(response).getValue("id").jsonPrimitive.long
        api.upload(releaseId, apk, "TyrePing-${tagName.get()}.apk")
    }
}
