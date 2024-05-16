package yvalmor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable
import com.github.javaparser.utils.CodeGenerationUtils
import com.github.javaparser.utils.SourceRoot
import java.util.Stack

class Main

fun main() {
    val sourceRoot = SourceRoot(CodeGenerationUtils.mavenModuleRoot(Main::class.java).resolve("src/main/resources"))
    val cu: CompilationUnit = sourceRoot.parse("", "Test.java")
    val classStack = Stack<ClassOrInterfaceDeclaration>()
    val stack = Stack<Pair<ClassOrInterfaceDeclaration, MethodDeclaration>>()
    val map = HashMap<Pair<ClassOrInterfaceDeclaration, MethodDeclaration>, MutableSet<String>>()

    val modifierVisitor = object : ModifierVisitor<Void>() {
        override fun visit(n: ClassOrInterfaceDeclaration, arg: Void?): Visitable {
            classStack.push(n)
            val result: Visitable = super.visit(n, arg)
            classStack.pop()

            return result
        }

        override fun visit(n: MethodDeclaration, arg: Void?): Visitable {
            stack.push(Pair(classStack.peek(), n))
            val result: Visitable = super.visit(n, arg)
            stack.pop()

            return result
        }

        override fun visit(n: MethodCallExpr, arg: Void?): Visitable {
            val currentCalls: MutableSet<String> = map[stack.peek()] ?: mutableSetOf()
            currentCalls.add(n.toString())

            map[stack.peek()] = currentCalls

            return super.visit(n, arg)
        }
    }

    cu.accept(modifierVisitor, null)

    map.forEach {
        val clazz = it.key.first
        val method = it.key.second

        print("${clazz.name}.${method.name}(")
        method.parameters.forEach { p -> print("${p.name}") }
        println("):")

        it.value.forEach { value ->
            println("\t${value}")
        }
    }
}
