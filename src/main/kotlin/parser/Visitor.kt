package yvalmor.parser

import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable
import mu.KotlinLogging
import yvalmor.graph.Printer
import java.util.*

class Visitor : ModifierVisitor<Void>() {
    private val logger = KotlinLogging.logger(Visitor::class.java.name)

    private val packageStack = Stack<PackageDeclaration>()
    private val classStack = Stack<TypeDeclaration<*>>()
    private val methodStack = Stack<CallableDeclaration<*>>()

    override fun visit(n: PackageDeclaration?, arg: Void?): Visitable {
        logger.debug { "Visiting package declaration: ${n?.nameAsString}" }
        packageStack.push(n)

        val result: Visitable = super.visit(n, arg)

        return result
    }

    override fun visit(n: ClassOrInterfaceDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting class declaration: ${n.nameAsString}" }
        classStack.push(n)
        Printer.registerClass(n, packageStack.peek())

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
        Printer.registerClass(n, packageStack.peek())

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
        Printer.registerClass(n, packageStack.peek())

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
        Printer.registerClass(n, packageStack.peek())

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
        Printer.registerMethodDeclaration(n, classStack.peek())

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving method declaration: ${n.nameAsString}" }
        methodStack.pop()

        return result
    }

    override fun visit(n: ConstructorDeclaration, arg: Void?): Visitable {
        logger.debug { "Visiting constructor declaration: ${n.nameAsString}" }
        methodStack.push(n)
        Printer.registerMethodDeclaration(n, classStack.peek())

        val result: Visitable = super.visit(n, arg)

        logger.debug { "Leaving constructor declaration: ${n.nameAsString}" }
        methodStack.pop()

        return result
    }

    override fun visit(n: MethodCallExpr, arg: Void?): Visitable {
        logger.debug { "Visiting method call: ${n.nameAsString}" }

        if (!methodStack.isEmpty()) {
            logger.debug { "Method call in method declaration: ${methodStack.peek().nameAsString}" }
            val methodDeclaration: CallableDeclaration<*> = methodStack.peek()
            Printer.registerMethodCall(methodDeclaration, n)

            return super.visit(n, arg)
        }

        logger.debug { "Method call in class declaration: ${classStack.peek().nameAsString}" }
        val classDeclaration: TypeDeclaration<*> = classStack.peek()
        Printer.registerStaticCall(classDeclaration, n)

        return super.visit(n, arg)
    }
}