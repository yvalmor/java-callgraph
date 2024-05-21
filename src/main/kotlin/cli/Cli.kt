package yvalmor.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ParserConfiguration.LanguageLevel
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.utils.SourceRoot
import mu.KotlinLogging
import yvalmor.graph.Printer
import yvalmor.parser.Visitor
import java.io.File
import java.nio.file.Path

class Cli : CliktCommand(
    name = "java-callgraph",
    help = "Generate a callgraph for a Java project",
    printHelpOnEmptyArgs = true
) {
    private val logger = KotlinLogging.logger(Cli::class.java.name)

    private val projectRoots: Set<Path> by argument("root")
        .help("Directory containing the packages to analyse")
        .path(mustExist = true, mustBeReadable = true)
        .multiple()
        .unique()

    private val javaVersion: LanguageLevel by option("--java-version")
        .help("The java version of the code to analyse")
        .enum<LanguageLevel>(ignoreCase = true, key = ::languageLevelToOption)
        .default(LanguageLevel.JAVA_18)

    private val libraryOptions by LibraryOptions()

    private val dotOuptut by option("--dot-output")
        .help("Whether to output the callgraph in dot format")
        .flag(default = false)

    private val outputFile: File? by option("-o", "--output")
        .help("Output file to write the results to")
        .file(canBeDir = false)

    override fun run() {
        logger.info { "Using Java version: $javaVersion" }

        val combinedTypeSolver = CombinedTypeSolver()
        addTypeSolvers(combinedTypeSolver)

        val parserConfiguration = ParserConfiguration()
        parserConfiguration.setSymbolResolver(JavaSymbolSolver(combinedTypeSolver))
        parserConfiguration.setLanguageLevel(javaVersion)

        outputFile?.let { Printer.setOutputFile(it) }

        val sourceRoots = projectRoots.map { SourceRoot(it, parserConfiguration) }
        processSourceRoot(sourceRoots, JavaParserFacade.get(combinedTypeSolver))
    }

    private fun languageLevelToOption(languageLevel: LanguageLevel): String {
        when (languageLevel) {
            LanguageLevel.JAVA_1_0 -> return "1.0"
            LanguageLevel.JAVA_1_1 -> return "1.1"
            LanguageLevel.JAVA_1_2 -> return "1.2"
            LanguageLevel.JAVA_1_3 -> return "1.3"
            LanguageLevel.JAVA_1_4 -> return "1.4"
            LanguageLevel.JAVA_5 -> return "5"
            LanguageLevel.JAVA_6 -> return "6"
            LanguageLevel.JAVA_7 -> return "7"
            LanguageLevel.JAVA_8 -> return "8"
            LanguageLevel.JAVA_9 -> return "9"
            LanguageLevel.JAVA_10 -> return "10"
            LanguageLevel.JAVA_11 -> return "11"
            LanguageLevel.JAVA_12 -> return "12"
            LanguageLevel.JAVA_13 -> return "13"
            LanguageLevel.JAVA_14 -> return "14"
            LanguageLevel.JAVA_15 -> return "15"
            LanguageLevel.JAVA_16 -> return "16"
            LanguageLevel.JAVA_17 -> return "17"
            LanguageLevel.JAVA_18 -> return "18"
            LanguageLevel.JAVA_10_PREVIEW -> return "10_p"
            LanguageLevel.JAVA_11_PREVIEW -> return "11_p"
            LanguageLevel.JAVA_12_PREVIEW -> return "12_p"
            LanguageLevel.JAVA_13_PREVIEW -> return "13_p"
            LanguageLevel.JAVA_14_PREVIEW -> return "14_p"
            LanguageLevel.JAVA_15_PREVIEW -> return "15_p"
            LanguageLevel.JAVA_16_PREVIEW -> return "16_p"
            LanguageLevel.JAVA_17_PREVIEW -> return "17_p"
        }
    }

    private fun addTypeSolvers(combinedTypeSolver: CombinedTypeSolver) {
        combinedTypeSolver.add(ReflectionTypeSolver())

        projectRoots.map { JavaParserTypeSolver(it) }
            .forEach {
                logger.trace { "Adding source root to type solver: ${it.root}" }
                combinedTypeSolver.add(it)
            }

        libraryOptions.addLibrariesToTypeSolver(combinedTypeSolver)
    }

    private fun processSourceRoot(sourceRoots: List<SourceRoot>, javaParserFacade: JavaParserFacade) {
        sourceRoots.forEach { sourceRoot ->
            logger.info { "Parsing source root: ${sourceRoot.root}" }
            sourceRoot.tryToParse()
                .forEach {
                    it.ifSuccessful { cu: CompilationUnit ->
                        val visitor = Visitor()
                        cu.accept(visitor, null)
                    }
                }
        }

        logger.info { "Starting callgraph computation" }
        Printer.print(javaParserFacade, dotOuptut)
    }
}