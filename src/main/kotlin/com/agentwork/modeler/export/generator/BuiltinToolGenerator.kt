package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.springframework.stereotype.Component

@Component
class BuiltinToolGenerator {

    fun generate(tool: KoogBuiltinTool): String? = when (tool.type) {
        BuiltinToolType.THINK -> null
        BuiltinToolType.USER_PROMPT -> null
        BuiltinToolType.FILESYSTEM -> null
        BuiltinToolType.SHELL -> generateShell(tool)
        BuiltinToolType.FETCH -> generateFetch(tool)
        BuiltinToolType.MEMORY -> generateMemory(tool)
        BuiltinToolType.TODO -> generateTodo()
    }

    private fun generateShell(tool: KoogBuiltinTool): String {
        val workingDir = tool.config["workingDir"] as? String ?: "."
        val fileType = ClassName("java.io", "File")

        val constructor = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("workingDir", String::class)
                    .defaultValue("%S", workingDir)
                    .build()
            )
            .build()

        val workingDirProp = PropertySpec.builder("workingDir", String::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer("workingDir")
            .build()

        val executeCommandFun = FunSpec.builder("executeCommand")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Execute a shell command and return its output"))
            .addParameter(
                ParameterSpec.builder("command", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("The shell command to execute"))
                    .build()
            )
            .returns(String::class)
            .addStatement("val process = %T(%S, %S, command)", ProcessBuilder::class, "sh", "-c")
            .addStatement("    .directory(%T(workingDir))", fileType)
            .addStatement("    .redirectErrorStream(true)")
            .addStatement("    .start()")
            .addStatement("val output = process.inputStream.bufferedReader().readText()")
            .addStatement("process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)")
            .addStatement("return output.take(100_000)")
            .build()

        val classSpec = TypeSpec.classBuilder("ShellToolSet")
            .addSuperinterface(KoogTypes.TOOL_SET)
            .addAnnotation(KoogAnnotations.llmDescription("Tools for executing shell commands"))
            .primaryConstructor(constructor)
            .addProperty(workingDirProp)
            .addFunction(executeCommandFun)
            .build()

        return FileSpec.builder("generated.tools", "ShellToolSet")
            .addType(classSpec)
            .build()
            .toString()
    }

    private fun generateFetch(tool: KoogBuiltinTool): String {
        val baseUrl = tool.config["baseUrl"] as? String ?: ""
        val httpClientType = ClassName("java.net.http", "HttpClient")
        val uriType = ClassName("java.net", "URI")
        val httpRequestType = ClassName("java.net.http", "HttpRequest")
        val httpResponseType = ClassName("java.net.http", "HttpResponse")

        val constructor = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("baseUrl", String::class)
                    .defaultValue("%S", baseUrl)
                    .build()
            )
            .build()

        val baseUrlProp = PropertySpec.builder("baseUrl", String::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer("baseUrl")
            .build()

        val clientProp = PropertySpec.builder("client", httpClientType)
            .addModifiers(KModifier.PRIVATE)
            .initializer("%T.newHttpClient()", httpClientType)
            .build()

        val fetchFun = FunSpec.builder("fetch")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Make an HTTP request and return the response body"))
            .addParameter(
                ParameterSpec.builder("url", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("The URL to fetch"))
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("method", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("HTTP method (GET, POST, PUT, DELETE)"))
                    .build()
            )
            .returns(String::class)
            .addStatement("val fullUrl = if (baseUrl.isNotEmpty()) baseUrl + url else url")
            .addStatement(
                "val request = %T.newBuilder()\n    .uri(%T.create(fullUrl))\n    .method(method.uppercase(), %T.BodyPublishers.noBody())\n    .build()",
                httpRequestType, uriType, httpRequestType
            )
            .addStatement(
                "val response = client.send(request, %T.BodyHandlers.ofString())",
                httpResponseType
            )
            .addStatement("""return "HTTP " + response.statusCode().toString() + "\n" + response.body()""")
            .build()

        val classSpec = TypeSpec.classBuilder("FetchToolSet")
            .addSuperinterface(KoogTypes.TOOL_SET)
            .addAnnotation(KoogAnnotations.llmDescription("Tools for making HTTP requests"))
            .primaryConstructor(constructor)
            .addProperty(baseUrlProp)
            .addProperty(clientProp)
            .addFunction(fetchFun)
            .build()

        return FileSpec.builder("generated.tools", "FetchToolSet")
            .addType(classSpec)
            .build()
            .toString()
    }

    private fun generateMemory(tool: KoogBuiltinTool): String {
        val path = tool.config["path"] as? String ?: "memory.db"
        val driverManagerType = ClassName("java.sql", "DriverManager")

        val constructor = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("path", String::class)
                    .defaultValue("%S", path)
                    .build()
            )
            .build()

        val pathProp = PropertySpec.builder("path", String::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer("path")
            .build()

        val initBlock = CodeBlock.builder()
            .addStatement(
                "%T.getConnection(%S + path).use { conn ->",
                driverManagerType, "jdbc:sqlite:"
            )
            .addStatement(
                "    conn.createStatement().execute(%S)",
                "CREATE TABLE IF NOT EXISTS memory (key TEXT PRIMARY KEY, value TEXT)"
            )
            .addStatement("}")
            .build()

        val connectionFun = FunSpec.builder("connection")
            .addModifiers(KModifier.PRIVATE)
            .returns(ClassName("java.sql", "Connection"))
            .addStatement(
                "return %T.getConnection(%S + path)",
                driverManagerType, "jdbc:sqlite:"
            )
            .build()

        val storeFun = FunSpec.builder("store")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Store a value with a key"))
            .addParameter(
                ParameterSpec.builder("key", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("The key"))
                    .build()
            )
            .addParameter(
                ParameterSpec.builder("value", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("The value to store"))
                    .build()
            )
            .returns(String::class)
            .addStatement("connection().use { conn ->")
            .addStatement(
                "    conn.prepareStatement(%S).use { stmt ->",
                "INSERT OR REPLACE INTO memory (key, value) VALUES (?, ?)"
            )
            .addStatement("        stmt.setString(1, key)")
            .addStatement("        stmt.setString(2, value)")
            .addStatement("        stmt.executeUpdate()")
            .addStatement("    }")
            .addStatement("}")
            .addStatement("""return "Stored key: " + key""")
            .build()

        val retrieveFun = FunSpec.builder("retrieve")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Retrieve a value by key"))
            .addParameter(
                ParameterSpec.builder("key", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("The key to look up"))
                    .build()
            )
            .returns(String::class)
            .addStatement("connection().use { conn ->")
            .addStatement(
                "    conn.prepareStatement(%S).use { stmt ->",
                "SELECT value FROM memory WHERE key = ?"
            )
            .addStatement("        stmt.setString(1, key)")
            .addStatement("        val rs = stmt.executeQuery()")
            .addStatement("""        return if (rs.next()) rs.getString("value") else "Key not found: " + key""")
            .addStatement("    }")
            .addStatement("}")
            .build()

        val listKeysFun = FunSpec.builder("listKeys")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("List all stored keys"))
            .returns(String::class)
            .addStatement("val keys = mutableListOf<String>()")
            .addStatement("connection().use { conn ->")
            .addStatement(
                "    conn.createStatement().executeQuery(%S).use { rs ->",
                "SELECT key FROM memory"
            )
            .addStatement("""        while (rs.next()) keys.add(rs.getString("key"))""")
            .addStatement("    }")
            .addStatement("}")
            .addStatement("""return if (keys.isEmpty()) "No keys stored" else keys.joinToString("\n")""")
            .build()

        val classSpec = TypeSpec.classBuilder("MemoryToolSet")
            .addSuperinterface(KoogTypes.TOOL_SET)
            .addAnnotation(KoogAnnotations.llmDescription("Persistent key-value memory storage"))
            .primaryConstructor(constructor)
            .addProperty(pathProp)
            .addInitializerBlock(initBlock)
            .addFunction(connectionFun)
            .addFunction(storeFun)
            .addFunction(retrieveFun)
            .addFunction(listKeysFun)
            .build()

        return FileSpec.builder("generated.tools", "MemoryToolSet")
            .addType(classSpec)
            .build()
            .toString()
    }

    private fun generateTodo(): String {
        val taskClass = TypeSpec.classBuilder("Task")
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("id", Int::class)
                    .addParameter("description", String::class)
                    .addParameter(
                        ParameterSpec.builder("completed", Boolean::class)
                            .defaultValue("false")
                            .build()
                    )
                    .build()
            )
            .addProperty(PropertySpec.builder("id", Int::class).initializer("id").build())
            .addProperty(PropertySpec.builder("description", String::class).initializer("description").build())
            .addProperty(
                PropertySpec.builder("completed", Boolean::class)
                    .mutable(true)
                    .initializer("completed")
                    .build()
            )
            .build()

        val taskType = ClassName("kotlin.collections", "MutableList")
            .parameterizedBy(ClassName("generated.tools", "Task"))
        val tasksProp = PropertySpec.builder("tasks", taskType)
            .addModifiers(KModifier.PRIVATE)
            .initializer("mutableListOf()")
            .build()

        val nextIdProp = PropertySpec.builder("nextId", Int::class)
            .addModifiers(KModifier.PRIVATE)
            .mutable(true)
            .initializer("1")
            .build()

        val addTaskFun = FunSpec.builder("addTask")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Add a new task to the list"))
            .addParameter(
                ParameterSpec.builder("description", String::class)
                    .addAnnotation(KoogAnnotations.llmDescription("Task description"))
                    .build()
            )
            .returns(String::class)
            .addStatement("val task = Task(nextId++, description)")
            .addStatement("tasks.add(task)")
            .addStatement("""return "Added task #" + task.id + ": " + task.description""")
            .build()

        val completeTaskFun = FunSpec.builder("completeTask")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Mark a task as completed"))
            .addParameter(
                ParameterSpec.builder("id", Int::class)
                    .addAnnotation(KoogAnnotations.llmDescription("Task ID"))
                    .build()
            )
            .returns(String::class)
            .addStatement("""val task = tasks.find { it.id == id } ?: return "Task #" + id + " not found"""")
            .addStatement("task.completed = true")
            .addStatement("""return "Completed task #" + id + ": " + task.description""")
            .build()

        val listTasksFun = FunSpec.builder("listTasks")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("List all tasks"))
            .returns(String::class)
            .addStatement("""if (tasks.isEmpty()) return "No tasks"""")
            .addStatement("""return tasks.joinToString("\n") { "#" + it.id + " [" + (if (it.completed) "x" else " ") + "] " + it.description }""")
            .build()

        val removeTaskFun = FunSpec.builder("removeTask")
            .addAnnotation(KoogAnnotations.tool())
            .addAnnotation(KoogAnnotations.llmDescription("Remove a task from the list"))
            .addParameter(
                ParameterSpec.builder("id", Int::class)
                    .addAnnotation(KoogAnnotations.llmDescription("Task ID"))
                    .build()
            )
            .returns(String::class)
            .addStatement("val removed = tasks.removeIf { it.id == id }")
            .addStatement("""return if (removed) "Removed task #" + id else "Task #" + id + " not found"""")
            .build()

        val classSpec = TypeSpec.classBuilder("TodoToolSet")
            .addSuperinterface(KoogTypes.TOOL_SET)
            .addAnnotation(KoogAnnotations.llmDescription("Task list management tools"))
            .addType(taskClass)
            .addProperty(tasksProp)
            .addProperty(nextIdProp)
            .addFunction(addTaskFun)
            .addFunction(completeTaskFun)
            .addFunction(listTasksFun)
            .addFunction(removeTaskFun)
            .build()

        return FileSpec.builder("generated.tools", "TodoToolSet")
            .addType(classSpec)
            .build()
            .toString()
    }
}
