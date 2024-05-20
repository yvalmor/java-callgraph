package yvalmor

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.utils.SourceRoot
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.size <= 1) {
        println("Usage: java -jar <path-to-jar> <path-to-src-directory> [project-java-version]")
        return
    }

    val path: Path = Path.of(args[0])
    val sourceRoot = SourceRoot(path)

    val combinedTypeSolver = CombinedTypeSolver()
    combinedTypeSolver.add(ReflectionTypeSolver())
    combinedTypeSolver.add(JavaParserTypeSolver(path))

    sourceRoot.parserConfiguration.setSymbolResolver(JavaSymbolSolver(combinedTypeSolver))
    sourceRoot.parserConfiguration.setLanguageLevel(getJavaVersion(args))

    processSourceRoot(sourceRoot, JavaParserFacade.get(combinedTypeSolver))
}

fun getJavaVersion(args: Array<String>): ParserConfiguration.LanguageLevel {
    if (args.size < 2) {
        return ParserConfiguration.LanguageLevel.JAVA_11
    }

    when (val version: String = args[1]) {
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
        else -> throw IllegalArgumentException("Unsupported Java version: $version")
    }
}

fun processSourceRoot(sourceRoot: SourceRoot, javaParserFacade: JavaParserFacade) {
    val visitors: MutableList<Visitor> = mutableListOf()

    sourceRoot.tryToParse()
        .forEach {
            it.ifSuccessful { cu: CompilationUnit ->
                val visitor = Visitor()
                cu.accept(visitor, null)

                visitors.add(visitor)
            }
        }

    visitors.forEach { it.print(javaParserFacade) }
}
