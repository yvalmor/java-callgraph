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
    if (args.size != 1) {
        println("Usage: java -jar <path-to-jar> <path-to-src-directory>")
        return
    }

    val path: Path = Path.of(args[0])
    val sourceRoot = SourceRoot(path)

    val combinedTypeSolver = CombinedTypeSolver()
    combinedTypeSolver.add(ReflectionTypeSolver())
    combinedTypeSolver.add(JavaParserTypeSolver(path))

    sourceRoot.parserConfiguration.setSymbolResolver(JavaSymbolSolver(combinedTypeSolver))
    sourceRoot.parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11)

    processSourceRoot(sourceRoot, JavaParserFacade.get(combinedTypeSolver))
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
