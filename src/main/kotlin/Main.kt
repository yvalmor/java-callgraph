package yvalmor

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.utils.SourceRoot
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.isDirectory

class Main

private val logger = KotlinLogging.logger(Main::class.java.name)

fun main(args: Array<String>) {
    if (args.size <= 1) {
        logger.error { "Usage: java -jar <path-to-jar> <path-to-src-directory> [project-java-version]" }
        return
    }

    val path: Path = Path.of(args[0])
    if (!path.toFile().exists()) {
        logger.error { "Path does not exist: $path" }
        return
    }

    val sourceRoot = SourceRoot(path)
    val combinedTypeSolver = CombinedTypeSolver()
    if (!addTypeSolvers(combinedTypeSolver, path)) {
        logger.error { "Failed to add type solvers" }
        return
    }

    sourceRoot.parserConfiguration.setSymbolResolver(JavaSymbolSolver(combinedTypeSolver))

    val javaVersion = getJavaVersion(args)
    if (javaVersion == null) {
        logger.error { "Invalid Java version" }
        return
    }

    logger.info { "Java version: $javaVersion" }
    sourceRoot.parserConfiguration.setLanguageLevel(javaVersion)

    processSourceRoot(sourceRoot, JavaParserFacade.get(combinedTypeSolver))
}

fun addTypeSolvers(combinedTypeSolver: CombinedTypeSolver, path: Path): Boolean {
    combinedTypeSolver.add(ReflectionTypeSolver())
    combinedTypeSolver.add(JavaParserTypeSolver(path))

    return addMavenLibrariesSolvers(combinedTypeSolver)
}

fun addMavenLibrariesSolvers(combinedTypeSolver: CombinedTypeSolver): Boolean {
    val projectRepository = System.getenv("MAVEN_REPOSITORY")
    val homeRepository = System.getProperty("user.home")

    if (projectRepository == null && homeRepository == null) {
        logger.error { "Maven repository not found" }
        return false
    }

    if (projectRepository != null)
        addLibrarySolver(combinedTypeSolver, Path.of(projectRepository))

    val mavenRepositoryResolved = Path.of(homeRepository).resolve(".m2/repository")
    if (mavenRepositoryResolved.toFile().exists())
        addLibrarySolver(combinedTypeSolver, mavenRepositoryResolved)

    return true
}

fun addLibrarySolver(combinedTypeSolver: CombinedTypeSolver, path: Path) {
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

fun getJavaVersion(args: Array<String>): ParserConfiguration.LanguageLevel? {
    if (args.size < 2) {
        return ParserConfiguration.LanguageLevel.JAVA_11
    }

    when (args[1]) {
        "1" -> return ParserConfiguration.LanguageLevel.JAVA_1_1
        "2" -> return ParserConfiguration.LanguageLevel.JAVA_1_2
        "3" -> return ParserConfiguration.LanguageLevel.JAVA_1_3
        "4" -> return ParserConfiguration.LanguageLevel.JAVA_1_4
        "5" -> return ParserConfiguration.LanguageLevel.JAVA_5
        "6" -> return ParserConfiguration.LanguageLevel.JAVA_6
        "7" -> return ParserConfiguration.LanguageLevel.JAVA_7
        "8" -> return ParserConfiguration.LanguageLevel.JAVA_8
        "9" -> return ParserConfiguration.LanguageLevel.JAVA_9
        "10" -> return ParserConfiguration.LanguageLevel.JAVA_10
        "11" -> return ParserConfiguration.LanguageLevel.JAVA_11
        "12" -> return ParserConfiguration.LanguageLevel.JAVA_12
        "13" -> return ParserConfiguration.LanguageLevel.JAVA_13
        "14" -> return ParserConfiguration.LanguageLevel.JAVA_14
        "15" -> return ParserConfiguration.LanguageLevel.JAVA_15
        "16" -> return ParserConfiguration.LanguageLevel.JAVA_16
        "17" -> return ParserConfiguration.LanguageLevel.JAVA_17
        else -> return null
    }
}

fun processSourceRoot(sourceRoot: SourceRoot, javaParserFacade: JavaParserFacade) {
    val visitors: MutableList<Visitor> = mutableListOf()

    logger.info { "Parsing source root: ${sourceRoot.root}"}
    sourceRoot.tryToParse()
        .forEach {
            it.ifSuccessful { cu: CompilationUnit ->
                val visitor = Visitor()
                cu.accept(visitor, null)

                visitors.add(visitor)
            }
        }

    logger.info { "Starting callgraph computation" }
    visitors.forEach { it.print(javaParserFacade) }
}
