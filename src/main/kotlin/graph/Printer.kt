package yvalmor.graph

import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.nodeTypes.NodeWithArguments
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
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
    private val objectCreationMap = HashMap<CallableDeclaration<*>, MutableSet<ObjectCreationExpr>>()
    private val staticObjectCreationMap = HashMap<TypeDeclaration<*>, MutableSet<ObjectCreationExpr>>()

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

        fun registerObjectCreation(methodDeclaration: CallableDeclaration<*>, objectCreation: ObjectCreationExpr) {
            val objectCreations = getInstance().objectCreationMap.getOrPut(methodDeclaration) { mutableSetOf() }
            objectCreations.add(objectCreation)
        }

        fun registerStaticObjectCreation(clazz: TypeDeclaration<*>, objectCreation: ObjectCreationExpr) {
            val objectCreations = getInstance().staticObjectCreationMap.getOrPut(clazz) { mutableSetOf() }
            objectCreations.add(objectCreation)
        }

        fun print(javaParserFacade: JavaParserFacade, dotOutput: Boolean) {
            if (dotOutput)
                getInstance().printDot(javaParserFacade)
            else
                getInstance().print(javaParserFacade)
        }

        fun setOutputFile(outputFile: File) {
            getInstance().outputFile = PrintStream(outputFile)
        }
    }

    private fun printDot(javaParserFacade: JavaParserFacade) {
        outputFile.println("digraph G {")
        outputFile.println("  rankdir=LR")
        outputFile.println("  node [shape=record]")
        outputFile.println("  edge [arrowhead=vee]")
        outputFile.println()

        clazzMap.keys.forEach { clazz -> printStaticCallDot(clazz, javaParserFacade) }
        methodDeclarationMap.keys.forEach { methodDeclaration ->
            printMethodDeclarationDot(
                methodDeclaration,
                javaParserFacade
            )
        }

        outputFile.println("}")
    }

    private fun printStaticCallDot(clazz: TypeDeclaration<*>, javaParserFacade: JavaParserFacade) {
        val packageDeclaration: PackageDeclaration = clazzMap[clazz]!!

        val classQualifiedName = "${packageDeclaration.nameAsString}.${clazz.nameAsString}"
        val classId = formatDotName(classQualifiedName)
        val label = formatDotLabel(classQualifiedName)

        outputFile.println("  subgraph $classId {")
        outputFile.println("    $classId [label=\"$label\"]")

        staticCallMap[clazz]?.forEach { p -> printMethodCallDot(p, classId, javaParserFacade) }
        staticObjectCreationMap[clazz]?.forEach { p -> printObjectCreationDot(p, classId, javaParserFacade) }

        outputFile.println("  }")
    }

    private fun printMethodDeclarationDot(
        methodDeclaration: CallableDeclaration<*>,
        javaParserFacade: JavaParserFacade
    ) {
        val classDeclaration: TypeDeclaration<*> = methodDeclarationMap[methodDeclaration]!!
        val packageDeclaration: PackageDeclaration = clazzMap[classDeclaration]!!

        val functionQualifiedName =
            "${packageDeclaration.nameAsString}.${classDeclaration.nameAsString}.${methodDeclaration.nameAsString}"
        val arguments = methodDeclaration.parameters.joinToString(", ") { it.typeAsString }
        val functionId = formatDotName(functionQualifiedName)
        val label = formatDotLabel("$functionQualifiedName($arguments)")

        outputFile.println("  subgraph $functionId {")
        outputFile.println("    $functionId [label=\"$label\"]")

        methodCallMap[methodDeclaration]?.forEach { p -> printMethodCallDot(p, functionId, javaParserFacade) }
        objectCreationMap[methodDeclaration]?.forEach { p -> printObjectCreationDot(p, functionId, javaParserFacade) }

        outputFile.println("  }")
    }

    private fun printMethodCallDot(
        methodCallExpr: MethodCallExpr,
        parentId: String,
        javaParserFacade: JavaParserFacade
    ) {
        try {
            val methodRef: ResolvedMethodDeclaration = javaParserFacade.solve(methodCallExpr).correspondingDeclaration
            printResolvedMethodLikeDeclarationDot(methodRef, parentId)
        } catch (exception: Exception) {
            logger.warn { "Failed to resolve method call: ${methodCallExpr.nameAsString} [${exception.cause}]" }

            val methodQualifiedName = methodCallExpr.nameAsString
            printNonResolvedMethodLikeDeclarationDot(methodQualifiedName, methodCallExpr, parentId)
        }
    }

    private fun printObjectCreationDot(
        objectCreationExpr: ObjectCreationExpr,
        parentId: String,
        javaParserFacade: JavaParserFacade
    ) {
        try {
            val objectRef: ResolvedConstructorDeclaration =
                javaParserFacade.solve(objectCreationExpr).correspondingDeclaration
            printResolvedMethodLikeDeclarationDot(objectRef, parentId)
        } catch (exception: Exception) {
            logger.warn { "Failed to resolve object creation: ${objectCreationExpr.typeAsString} [${exception.cause}]" }

            val objectQualifiedName = objectCreationExpr.typeAsString
            printNonResolvedMethodLikeDeclarationDot(objectQualifiedName, objectCreationExpr, parentId)
        }
    }

    private fun printResolvedMethodLikeDeclarationDot(ref: ResolvedMethodLikeDeclaration, parentId: String) {
        val methodQualifiedName = ref.qualifiedName
        val arguments = ref.typeParameters.joinToString(", ") { it.qualifiedName }
        val methodId = formatDotName(methodQualifiedName)
        val label = formatDotLabel("$methodQualifiedName($arguments)")

        outputFile.println("    $methodId [label=\"$label\"]")
        outputFile.println("    $parentId -> $methodId")
    }

    private fun printNonResolvedMethodLikeDeclarationDot(
        methodQualifiedName: String,
        methodLikeDeclaration: NodeWithArguments<*>,
        parentId: String
    ) {
        val arguments = formatDotArguments(methodLikeDeclaration.arguments.joinToString(", "))
        val methodId = formatDotName(methodQualifiedName)
        val label = formatDotLabel("$methodQualifiedName($arguments)")

        outputFile.println("    $methodId [label=\"$label\"]")
        outputFile.println("    $parentId -> $methodId")
    }

    fun print(javaParserFacade: JavaParserFacade) {
        clazzMap.keys.forEach { clazz -> printStaticCall(clazz, javaParserFacade) }
        methodDeclarationMap.keys.forEach { methodDeclaration ->
            printMethodDeclaration(
                methodDeclaration,
                javaParserFacade
            )
        }
    }

    private fun printStaticCall(clazz: TypeDeclaration<*>, javaParserFacade: JavaParserFacade) {
        val packageDeclaration: PackageDeclaration = clazzMap[clazz]!!

        outputFile.println("${packageDeclaration.nameAsString}.${clazz.nameAsString}:")

        staticCallMap[clazz]?.forEach { p -> printMethodCall(p, javaParserFacade) }
        staticObjectCreationMap[clazz]?.forEach { p -> printObjectCreation(p, javaParserFacade) }
    }

    private fun printMethodDeclaration(methodDeclaration: CallableDeclaration<*>, javaParserFacade: JavaParserFacade) {
        val classDeclaration: TypeDeclaration<*> = methodDeclarationMap[methodDeclaration]!!
        val packageDeclaration: PackageDeclaration = clazzMap[classDeclaration]!!

        outputFile.println("${packageDeclaration.nameAsString}.${classDeclaration.nameAsString}.${methodDeclaration.nameAsString}:")

        methodCallMap[methodDeclaration]?.forEach { p -> printMethodCall(p, javaParserFacade) }
        objectCreationMap[methodDeclaration]?.forEach { p -> printObjectCreation(p, javaParserFacade) }
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

    private fun printObjectCreation(objectCreationExpr: ObjectCreationExpr, javaParserFacade: JavaParserFacade) {
        try {
            val objectRef: ResolvedConstructorDeclaration =
                javaParserFacade.solve(objectCreationExpr).correspondingDeclaration
            outputFile.println("\t${objectRef.qualifiedName}(${objectRef.typeParameters.joinToString(", ") { it.qualifiedName }})")
        } catch (exception: Exception) {
            logger.error { "Failed to resolve object creation: ${objectCreationExpr.typeAsString} [${exception.cause}]" }
            outputFile.println("\t${objectCreationExpr.typeAsString}(${objectCreationExpr.arguments.joinToString(", ")})")
        }
    }

    private fun formatDotName(s: String): String = s.replace(Regex("[^a-zA-Z0-9_]"), "_")
    private fun formatDotArguments(s: String): String = s.replace("\"", "\\\"")
    private fun formatDotLabel(s: String) = s
        .replace("<", "\\<").replace(">", "\\>")
        .replace("{", "\\{").replace("}", "\\}")
        .replace("\n", "<br/>").replace("\r", "<br/>")

    private fun <T> Iterable<T>.joinToString(
        separator: CharSequence = ", ",
        prefix: CharSequence = "",
        postfix: CharSequence = ""
    ): String {
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