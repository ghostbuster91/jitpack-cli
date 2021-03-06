package io.ghostbuster91.ktm.identifier

import io.ghostbuster91.ktm.identifier.artifact.ArtifactSolverDispatcher
import io.ghostbuster91.ktm.identifier.version.VersionSolverDispatcher

class IdentifierResolver(
        private val artifactSolverDispatcher: ArtifactSolverDispatcher,
        private val versionSolverDispatcher: VersionSolverDispatcher
) {

    fun resolve(unparsed: Identifier.Unparsed, version: String?): Identifier.Parsed {
        return unparsed
                .let { ArtifactSolverDispatcher.Artifact.Unparsed(it.text) }
                .let(artifactSolverDispatcher::resolve)
                .let { versionSolverDispatcher.resolve(it, version) }
                .let { Identifier.Parsed(it.groupId, it.artifactId, it.version) }
    }
}