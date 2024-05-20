package yvalmor.graph

import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import mu.KotlinLogging
import java.io.File
import java.io.PrintStream

class Printer private constructor() {
    private val logger = KotlinLogging.logger(Printer::class.java.name)

    private val clazzMap = HashMap<TypeDeclaration<*>, PackageDeclaration>()
    private val methodDeclarationMap = HashMap<CallableDeclaration<*>, TypeDeclaration<*>>()
    private val methodCallMap = HashMap<CallableDeclaration<*>, MutableSet<MethodCallExpr>>()
    private val staticCallMap = HashMap<TypeDeclaration<*>, MutableSet<MethodCallExpr>>()

    private var outputFile: PrintStream = System.out

    companion object {
        @Volatile
        private var instance: Printer? = null

        private fun getInstance(): Printer {
            return instance ?: synchronized(this) {
                instance ?: Printer().also { instance = it }
            }
        }

        fun registerClass(clazz: TypeDeclaration<*>, packageDeclaration: PackageDeclaration) {
            getInstance().clazzMap[clazz] = packageDeclaration
        }

        fun registerMethodDeclaration(methodDeclaration: CallableDeclaration<*>, clazz: TypeDeclaration<*>) {
            getInstance().methodDeclarationMap[methodDeclaration] = clazz
        }

        fun registerMethodCall(methodDeclaration: CallableDeclaration<*>, methodCall: MethodCallExpr) {
            val methodCalls = getInstance().methodCallMap.getOrPut(methodDeclaration) { mutableSetOf() }
            methodCalls.add(methodCall)
        }

        fun registerStaticCall(clazz: TypeDeclaration<*>, methodCall: MethodCallExpr) {
            val methodCalls = getInstance().staticCallMap.getOrPut(clazz) { mutableSetOf() }
            methodCalls.add(methodCall)
        }

        fun print(javaParserFacade: JavaParserFacade) {
            getInstance().print(javaParserFacade)
        }

        fun setOutputFile(outputFile: File) {
            getInstance().outputFile = PrintStream(outputFile)
        }
    }

    fun print(javaParserFacade: JavaParserFacade) {
        methodDeclarationMap.keys.forEach { methodDeclaration -> printMethodDeclaration(methodDeclaration, javaParserFacade) }
    }

    private fun printMethodDeclaration(methodDeclaration: CallableDeclaration<*>, javaParserFacade: JavaParserFacade) {
        val classDeclaration: TypeDeclaration<*> = methodDeclarationMap[methodDeclaration]!!
        val packageDeclaration: PackageDeclaration = clazzMap[classDeclaration]!!

        outputFile.println("${packageDeclaration.nameAsString}.${classDeclaration.nameAsString}.${methodDeclaration.nameAsString}:")

        methodCallMap[methodDeclaration]?.forEach { p -> printMethodCall(p, javaParserFacade) }
    }

    private fun printMethodCall(methodCallExpr: MethodCallExpr, javaParserFacade: JavaParserFacade) {
        try {
            val methodRef: ResolvedMethodDeclaration = javaParserFacade.solve(methodCallExpr).correspondingDeclaration
            outputFile.println("\t${methodRef.qualifiedName}(${methodRef.typeParameters.joinToString(", ") { it.qualifiedName }})")
        } catch (exception: Exception) {
            logger.error { "Failed to resolve method call: ${methodCallExpr.nameAsString} [${exception.cause}]" }
            outputFile.println("\t${methodCallExpr.nameAsString}(${methodCallExpr.arguments.joinToString(", ")})")
        }
    }

    private fun <T> Iterable<T>.joinToString(separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = ""): String {
        val sb = StringBuilder(prefix)
        val iterator = iterator()
        if (iterator.hasNext()) {
            sb.append(iterator.next())
            while (iterator.hasNext()) {
                sb.append(separator)
                sb.append(iterator.next())
            }
        }
        sb.append(postfix)
        return sb.toString()
    }
}