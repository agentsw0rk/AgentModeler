package com.agentwork.modeler.export.generator

import com.agentwork.modeler.export.model.BuiltinToolType
import com.agentwork.modeler.export.model.KoogBuiltinTool
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeName
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
        BuiltinToolType.JIRA -> generateJira(tool)
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

    private fun generateJira(tool: KoogBuiltinTool): String {
        val adfFun = FunSpec.builder("adf")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("text", STRING)
            .returns(JSON_OBJECT)
            // ADF forbids empty text nodes, so blank lines become empty paragraphs
            .addCode(
                """
                |return %M {
                |    %M("type", "doc")
                |    %M("version", 1)
                |    %M("content") {
                |        for (line in text.lines()) {
                |            %M {
                |                %M("type", "paragraph")
                |                if (line.isNotBlank()) %M("content") {
                |                    %M { %M("type", "text"); %M("text", line) }
                |                }
                |            }
                |        }
                |    }
                |}
                |""".trimMargin(),
                BUILD_JSON_OBJECT, PUT, PUT, PUT_JSON_ARRAY, ADD_JSON_OBJECT, PUT, PUT_JSON_ARRAY, ADD_JSON_OBJECT, PUT, PUT
            )
            .build()

        val classSpec = atlassianToolSet(tool, "JiraToolSet", "Tools for searching, reading, creating and updating Jira issues")
            .addFunction(adfFun)
            .addFunction(
                atlassianTool(
                    "searchIssues", "Search Jira issues with JQL. Returns key, summary, status and assignee per issue.",
                    listOf(
                        Triple("jql", STRING, "JQL query, e.g. project = ABC AND status = 'In Progress'"),
                        Triple("maxResults", INT, "Maximum number of issues (1-50)")
                    ),
                    CodeBlock.of(
                        "return text(call(\"GET\", \"/rest/api/3/search/jql?jql=\" + enc(jql) + \"&maxResults=\" + maxResults.coerceIn(1, 50) + \"&fields=summary,status,assignee\"))\n"
                    )
                )
            )
            .addFunction(
                atlassianTool(
                    "getIssue", "Get a Jira issue with all its fields",
                    listOf(Triple("issueKey", STRING, "Issue key, e.g. ABC-123")),
                    CodeBlock.of("return text(call(\"GET\", \"/rest/api/3/issue/\" + issueKey))\n")
                )
            )
            .addFunction(
                atlassianTool(
                    "createIssue", "Create a Jira issue",
                    listOf(
                        Triple("projectKey", STRING, "Project key, e.g. ABC"),
                        Triple("issueType", STRING, "Issue type name, e.g. Task, Bug, Story"),
                        Triple("summary", STRING, "One-line summary"),
                        Triple("description", STRING, "Plain text; line breaks start new paragraphs")
                    ),
                    CodeBlock.of(
                        """
                        |val body = %M {
                        |    %M("fields") {
                        |        %M("project") { %M("key", projectKey) }
                        |        %M("issuetype") { %M("name", issueType) }
                        |        %M("summary", summary)
                        |        put("description", adf(description))
                        |    }
                        |}
                        |return text(call("POST", "/rest/api/3/issue", body))
                        |""".trimMargin(),
                        BUILD_JSON_OBJECT, PUT_JSON_OBJECT, PUT_JSON_OBJECT, PUT, PUT_JSON_OBJECT, PUT, PUT
                    )
                )
            )
            .addFunction(
                atlassianTool(
                    "addComment", "Add a comment to a Jira issue",
                    listOf(
                        Triple("issueKey", STRING, "Issue key, e.g. ABC-123"),
                        Triple("comment", STRING, "Plain text; line breaks start new paragraphs")
                    ),
                    CodeBlock.of(
                        "return text(call(\"POST\", \"/rest/api/3/issue/\" + issueKey + \"/comment\", %M { put(\"body\", adf(comment)) }))\n",
                        BUILD_JSON_OBJECT
                    )
                )
            )
            .addFunction(
                atlassianTool(
                    "transitionIssue", "Move a Jira issue to another status",
                    listOf(
                        Triple("issueKey", STRING, "Issue key, e.g. ABC-123"),
                        Triple("statusName", STRING, "Target status name, e.g. Done")
                    ),
                    CodeBlock.of(
                        """
                        |val path = "/rest/api/3/issue/" + issueKey + "/transitions"
                        |val response = call("GET", path)
                        |if (response.statusCode() !in 200..299) return text(response)
                        |val transitions = %T.parseToJsonElement(response.body()).%M["transitions"]?.%M.orEmpty().map { it.%M }
                        |val match = transitions.firstOrNull { t ->
                        |    t["name"]?.%M?.content.equals(statusName, ignoreCase = true) ||
                        |        t["to"]?.%M?.get("name")?.%M?.content.equals(statusName, ignoreCase = true)
                        |} ?: return "No transition to '" + statusName + "'. Available: " +
                        |    transitions.joinToString { it["name"]?.%M?.content.orEmpty() }
                        |val id = match["id"]!!.%M.content
                        |return text(call("POST", path, %M { %M("transition") { %M("id", id) } }))
                        |""".trimMargin(),
                        JSON, JSON_OBJECT_EXT, JSON_ARRAY_EXT, JSON_OBJECT_EXT,
                        JSON_PRIMITIVE_EXT, JSON_OBJECT_EXT, JSON_PRIMITIVE_EXT, JSON_PRIMITIVE_EXT, JSON_PRIMITIVE_EXT,
                        BUILD_JSON_OBJECT, PUT_JSON_OBJECT, PUT
                    )
                )
            )
            .build()

        return FileSpec.builder("generated.tools", "JiraToolSet")
            .addType(classSpec)
            .build()
            .toString()
    }

    /** Class skeleton shared by the Atlassian Cloud tool sets: base URL, Basic auth from env, HTTP helpers. */
    private fun atlassianToolSet(tool: KoogBuiltinTool, className: String, description: String): TypeSpec.Builder {
        val httpClient = ClassName("java.net.http", "HttpClient")
        val httpRequest = ClassName("java.net.http", "HttpRequest")
        val httpResponse = ClassName("java.net.http", "HttpResponse")
        val responseType = httpResponse.parameterizedBy(STRING)

        val envFun = FunSpec.builder("env")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("name", STRING)
            .returns(STRING)
            .addStatement("return System.getenv(name)?.takeIf { it.isNotBlank() } ?: error(name + %S)", " is not set")
            .build()

        val callFun = FunSpec.builder("call")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("method", STRING)
            .addParameter("path", STRING)
            .addParameter(ParameterSpec.builder("body", JSON_OBJECT.copy(nullable = true)).defaultValue("null").build())
            .returns(responseType)
            .addCode(
                """
                |val request = %T.newBuilder(%T.create(baseUrl + path))
                |    .header("Authorization", auth)
                |    .header("Accept", "application/json")
                |if (body != null) request.header("Content-Type", "application/json")
                |request.method(method, if (body != null) %T.BodyPublishers.ofString(body.toString()) else %T.BodyPublishers.noBody())
                |return client.send(request.build(), %T.BodyHandlers.ofString())
                |""".trimMargin(),
                httpRequest, ClassName("java.net", "URI"), httpRequest, httpRequest, httpResponse
            )
            .build()

        val textFun = FunSpec.builder("text")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("response", responseType)
            .returns(STRING)
            .addStatement("""return "HTTP " + response.statusCode() + "\n" + response.body()""")
            .build()

        val encFun = FunSpec.builder("enc")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("value", STRING)
            .returns(STRING)
            .addStatement("return %T.encode(value, Charsets.UTF_8)", ClassName("java.net", "URLEncoder"))
            .build()

        return TypeSpec.classBuilder(className)
            .addSuperinterface(KoogTypes.TOOL_SET)
            .addAnnotation(KoogAnnotations.llmDescription(description))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        ParameterSpec.builder("baseUrl", STRING)
                            .defaultValue("%S", tool.config["baseUrl"] as? String ?: "")
                            .build()
                    )
                    .build()
            )
            .addProperty(PropertySpec.builder("baseUrl", STRING, KModifier.PRIVATE).initializer("baseUrl.trimEnd('/')").build())
            .addProperty(PropertySpec.builder("client", httpClient, KModifier.PRIVATE).initializer("%T.newHttpClient()", httpClient).build())
            // Read in the constructor so a missing variable fails at startup, not on the first tool call
            .addProperty(
                PropertySpec.builder("auth", STRING, KModifier.PRIVATE)
                    .initializer(
                        "\"Basic \" + %T.getEncoder().encodeToString((env(%S) + \":\" + env(%S)).toByteArray())",
                        ClassName("java.util", "Base64"), "ATLASSIAN_EMAIL", "ATLASSIAN_API_TOKEN"
                    )
                    .build()
            )
            .addFunction(envFun)
            .addFunction(callFun)
            .addFunction(textFun)
            .addFunction(encFun)
    }

    private fun atlassianTool(
        name: String,
        description: String,
        params: List<Triple<String, TypeName, String>>,
        body: CodeBlock
    ): FunSpec = FunSpec.builder(name)
        .addAnnotation(KoogAnnotations.tool())
        .addAnnotation(KoogAnnotations.llmDescription(description))
        .addParameters(params.map { (paramName, type, paramDescription) ->
            ParameterSpec.builder(paramName, type)
                .addAnnotation(KoogAnnotations.llmDescription(paramDescription))
                .build()
        })
        .returns(STRING)
        .addCode(body)
        .build()

    private companion object {
        const val JSON_PKG = "kotlinx.serialization.json"
        val JSON = ClassName(JSON_PKG, "Json")
        val JSON_OBJECT = ClassName(JSON_PKG, "JsonObject")
        val BUILD_JSON_OBJECT = MemberName(JSON_PKG, "buildJsonObject")
        val PUT = MemberName(JSON_PKG, "put")
        val PUT_JSON_OBJECT = MemberName(JSON_PKG, "putJsonObject")
        val PUT_JSON_ARRAY = MemberName(JSON_PKG, "putJsonArray")
        val ADD_JSON_OBJECT = MemberName(JSON_PKG, "addJsonObject")
        val JSON_OBJECT_EXT = MemberName(JSON_PKG, "jsonObject")
        val JSON_ARRAY_EXT = MemberName(JSON_PKG, "jsonArray")
        val JSON_PRIMITIVE_EXT = MemberName(JSON_PKG, "jsonPrimitive")
    }
}
