package yvalmor.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.path
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.isDirectory

class LibraryOptions: OptionGroup(
    name = "Lirabry options",
    help = "Options controlling where jar used to resolve types and methods are searched"
) {
    private val logger = KotlinLogging.logger(LibraryOptions::class.java.name)

    private val libraryDirectories: Set<Path> by option("-l", "--libraries")
        .help("Directories where we should search for jar libraries to use while resolving types and methods (will search recursively)")
        .path(mustExist = true, mustBeReadable = true, canBeFile = false)
        .multiple()
        .unique()

    private val jarLibraries: Set<Path> by option("-j", "--jars", help = "Jars libraries to use while resolving types and methods")
        .path(mustExist = true, mustBeReadable = true, canBeDir = false)
        .multiple()
        .unique()

    fun addLibrariesToTypeSolver(combinedTypeSolver: CombinedTypeSolver) {
        libraryDirectories.forEach {
            addLibrarySolver(combinedTypeSolver, it)
        }

        jarLibraries.forEach {
            logger.trace { "Adding jar to type solver: $it" }
            combinedTypeSolver.add(JarTypeSolver(it))
        }
    }

    private fun addLibrarySolver(combinedTypeSolver: CombinedTypeSolver, path: Path) {
        if (!path.isDirectory()) return

        path.toFile().listFiles()?.forEach {
            if (it.isDirectory)
                addLibrarySolver(combinedTypeSolver, it.toPath())
            else if (it.name.endsWith(".jar")) {
                logger.trace { "Adding jar to type solver: ${it.toPath()}" }
                combinedTypeSolver.add(JarTypeSolver(it.toPath()))
            }
        }
    }
}