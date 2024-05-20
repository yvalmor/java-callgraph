package yvalmor

import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.AnnotationDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import mu.KotlinLogging
import java.util.*

class Visitor : ModifierVisitor<Void>() {
    private val logger = KotlinLogging.logger(Visitor::class.java.name)

    private val packageStack = Stack<PackageDeclaration>()
    private val classStack = Stack<TypeDeclaration<*>>()
    private val methodStack = Stack<MethodDeclaration>()
    private val constructorStack = Stack<ConstructorDeclaration>()

    private val clazzMap = HashMap<TypeDeclaration<*>, PackageDeclaration>()
    private val methodDeclarationMap = HashMap<MethodDeclaration, TypeDeclaration<*>>()
    private val constructorDeclarationMap = HashMap<ConstructorDeclaration, TypeDeclaration<*>>()
    private val methodCallMap = HashMap<MethodDeclaration, MutableSet<MethodCallExpr>>()
    private val constructorCallMap = HashMap<ConstructorDeclaration, MutableSet<MethodCallExpr>>()
    private val staticCallMap = HashMap<TypeDeclaration<*>, MutableSet<MethodCallExpr>>()

    override fun visit(n: PackageDeclaration?, arg: Void?): Visitable {
        logger.debug { "Visiting package declaration: ${n?.nameAsString}" }
        packageStack.push(n)

        val result: Visitable = super.visit(n, arg)

        return result
    }

    override fun visit(n: ClassOrInterfaceDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting class declaration: ${n.nameAsString}" }
        classStack.push(n)
        clazzMap[n] = packageStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving class declaration: ${n.nameAsString}" }
        classStack.pop()

        if (classStack.isEmpty()) {
            logger.debug { "Leaving package declaration: ${packageStack.peek().nameAsString}" }
            packageStack.pop()
        }

        return result
    }

    override fun visit(n: EnumDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting enum declaration: ${n.nameAsString}" }
        classStack.push(n)
        clazzMap[n] = packageStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving enum declaration: ${n.nameAsString}" }
        classStack.pop()

        if (classStack.isEmpty()) {
            logger.debug { "Leaving package declaration: ${packageStack.peek().nameAsString}" }
            packageStack.pop()
        }

        return result
    }

    override fun visit(n: RecordDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting record declaration: ${n.nameAsString}" }
        classStack.push(n)
        clazzMap[n] = packageStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving record declaration: ${n.nameAsString}" }
        classStack.pop()

        if (classStack.isEmpty()) {
            logger.debug { "Leaving package declaration: ${packageStack.peek().nameAsString}" }
            packageStack.pop()
        }

        return result
    }

    override fun visit(n: AnnotationDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting annotation declaration: ${n.nameAsString}" }
        classStack.push(n)
        clazzMap[n] = packageStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving annotation declaration: ${n.nameAsString}" }
        classStack.pop()

        if (classStack.isEmpty()) {
            logger.debug { "Leaving package declaration: ${packageStack.peek().nameAsString}" }
            packageStack.pop()
        }

        return result
    }

    override fun visit(n: MethodDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting method declaration: ${n.nameAsString}" }
        methodStack.push(n)
        methodDeclarationMap[n] = classStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving method declaration: ${n.nameAsString}" }
        methodStack.pop()

        return result
    }

    override fun visit(n: ConstructorDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting constructor declaration: ${n.nameAsString}" }
        constructorStack.push(n)
        constructorDeclarationMap[n] = classStack.peek()

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving constructor declaration: ${n.nameAsString}" }
        constructorStack.pop()

        return result
    }

    override fun visit(n: MethodCallExpr, arg: Void?): Visitable {
        logger.debug { "Visiting method call: ${n.nameAsString}" }

        if (!methodStack.isEmpty()) {
            logger.debug { "Method call in method declaration: ${methodStack.peek().nameAsString}" }
            val methodDeclaration: MethodDeclaration = methodStack.peek()
            methodCallMap.computeIfAbsent(methodDeclaration) { HashSet() }.add(n)

            return super.visit(n, arg)
        }

        if (!constructorStack.empty()) {
            logger.debug { "Method call in constructor declaration: ${constructorStack.peek().nameAsString}" }
            val constructorDeclaration: ConstructorDeclaration = constructorStack.peek()
            constructorCallMap.computeIfAbsent(constructorDeclaration) { HashSet() }.add(n)

            return super.visit(n, arg)
        }

        logger.debug { "Method call in class declaration: ${classStack.peek().nameAsString}" }
        val classDeclaration: TypeDeclaration<*> = classStack.peek()
        staticCallMap.computeIfAbsent(classDeclaration) { HashSet() }.add(n)

        return super.visit(n, arg)
    }

    fun print(javaParserFacade: JavaParserFacade) {
        constructorDeclarationMap.keys.forEach { p -> printConstructorDeclaration(p, javaParserFacade) }
        methodDeclarationMap.keys.forEach { p -> printMethodDeclaration(p, javaParserFacade) }
        println()
    }

    private fun printMethodDeclaration(methodDeclaration: MethodDeclaration, javaParserFacade: JavaParserFacade) {
        val classDeclaration: TypeDeclaration<*> = methodDeclarationMap[methodDeclaration]!!
        val packageDeclaration: PackageDeclaration = clazzMap[classDeclaration]!!

        println("${packageDeclaration.nameAsString}.${classDeclaration.nameAsString}.${methodDeclaration.nameAsString}:")

        methodCallMap[methodDeclaration]?.forEach { p -> printMethodCall(p, javaParserFacade) }

        println()
    }

    private fun printConstructorDeclaration(constructorDeclaration: ConstructorDeclaration, javaParserFacade: JavaParserFacade) {
        val classDeclaration: TypeDeclaration<*> = constructorDeclarationMap[constructorDeclaration]!!
        val packageDeclaration: PackageDeclaration = clazzMap[classDeclaration]!!

        println("${packageDeclaration.nameAsString}.${classDeclaration.nameAsString}.${constructorDeclaration.nameAsString}:")

        constructorCallMap[constructorDeclaration]?.forEach { p -> printMethodCall(p, javaParserFacade) }

        println()
    }

    private fun printMethodCall(methodCallExpr: MethodCallExpr, javaParserFacade: JavaParserFacade) {
        val methodRef: ResolvedMethodDeclaration = javaParserFacade.solve(methodCallExpr).correspondingDeclaration
        println("\t${methodRef.qualifiedName}(${methodCallExpr.arguments.joinToString()})")
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