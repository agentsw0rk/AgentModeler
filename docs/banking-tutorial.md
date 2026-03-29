# Building an AI Banking Assistant with Koog

In this tutorial we’ll build a small banking assistant using **Koog** agents in Kotlin.
You’ll learn how to:
- Define domain models and sample data
- Expose capability-focused tools for **money transfers** and **transaction analytics**
- Classify user intent (Transfer vs Analytics)
- Orchestrate calls in two styles:
    1) a graph/subgraph strategy
    2) “agents as tools”

By the end, you’ll be able to route free-form user requests to the right tools and produce helpful, auditable responses.

## Setup & Dependencies

We’ll use the Kotlin Notebook kernel. Make sure your Koog artifacts are resolvable from Maven Central
and your LLM provider key is available via `OPENAI_API_KEY`.


```kotlin
%useLatestDescriptors
%use datetime

// uncomment this for using koog from Maven Central
// %use koog
```


```kotlin
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val apiKey = System.getenv("OPENAI_API_KEY") ?: error("Please set OPENAI_API_KEY environment variable")
val openAIExecutor = simpleOpenAIExecutor(apiKey)
```

## Defining the System Prompt

A well-crafted system prompt helps the AI understand its role and constraints. This prompt will guide all our agents' behavior.


```kotlin
val bankingAssistantSystemPrompt = """
    |You are a banking assistant interacting with a user (userId=123).
    |Your goal is to understand the user's request and determine whether it can be fulfilled using the available tools.
    |
    |If the task can be accomplished with the provided tools, proceed accordingly,
    |at the end of the conversation respond with: "Task completed successfully."
    |If the task cannot be performed with the tools available, respond with: "Can't perform the task."
""".trimMargin()
```

## Domain model & Sample data

First, let's define our domain models and sample data. We'll use Kotlin's data classes with serialization support.


```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: Int,
    val name: String,
    val surname: String? = null,
    val phoneNumber: String
)

val contactList = listOf(
    Contact(100, "Alice", "Smith", "+1 415 555 1234"),
    Contact(101, "Bob", "Johnson", "+49 151 23456789"),
    Contact(102, "Charlie", "Williams", "+36 20 123 4567"),
    Contact(103, "Daniel", "Anderson", "+46 70 123 45 67"),
    Contact(104, "Daniel", "Garcia", "+34 612 345 678"),
)

val contactById = contactList.associateBy(Contact::id)
```

## Tools: Money Transfer

Tools should be **pure** and predictable.

We model two “soft contracts”:
- `chooseRecipient` returns *candidates* when ambiguity is detected.
- `sendMoney` supports a `confirmed` flag. If `false`, it asks the agent to confirm with the user.


```kotlin
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for money transfer operations.")
class MoneyTransferTools : ToolSet {

    @Tool
    @LLMDescription(
        """
        Returns the list of contacts for the given user.
        The user in this demo is always userId=123.
        """
    )
    fun getContacts(
        @LLMDescription("The unique identifier of the user whose contact list is requested.") userId: Int
    ): String = buildString {
        contactList.forEach { c ->
            appendLine("${c.id}: ${c.name} ${c.surname ?: ""} (${c.phoneNumber})")
        }
    }.trimEnd()

    @Tool
    @LLMDescription("Returns the current balance (demo value).")
    fun getBalance(
        @LLMDescription("The unique identifier of the user.") userId: Int
    ): String = "Balance: 200.00 EUR"

    @Tool
    @LLMDescription("Returns the default user currency (demo value).")
    fun getDefaultCurrency(
        @LLMDescription("The unique identifier of the user.") userId: Int
    ): String = "EUR"

    @Tool
    @LLMDescription("Returns a demo FX rate between two ISO currencies (e.g. EUR→USD).")
    fun getExchangeRate(
        @LLMDescription("Base currency (e.g., EUR).") from: String,
        @LLMDescription("Target currency (e.g., USD).") to: String
    ): String = when (from.uppercase() to to.uppercase()) {
        "EUR" to "USD" -> "1.10"
        "EUR" to "GBP" -> "0.86"
        "GBP" to "EUR" -> "1.16"
        "USD" to "EUR" -> "0.90"
        else -> "No information about exchange rate available."
    }

    @Tool
    @LLMDescription(
        """
        Returns a ranked list of possible recipients for an ambiguous name.
        The agent should ask the user to pick one and then use the selected contact id.
        """
    )
    fun chooseRecipient(
        @LLMDescription("An ambiguous or partial contact name.") confusingRecipientName: String
    ): String {
        val matches = contactList.filter { c ->
            c.name.contains(confusingRecipientName, ignoreCase = true) ||
                (c.surname?.contains(confusingRecipientName, ignoreCase = true) ?: false)
        }
        if (matches.isEmpty()) {
            return "No candidates found for '$confusingRecipientName'. Use getContacts and ask the user to choose."
        }
        return matches.mapIndexed { idx, c ->
            "${idx + 1}. ${c.id}: ${c.name} ${c.surname ?: ""} (${c.phoneNumber})"
        }.joinToString("\n")
    }

    @Tool
    @LLMDescription(
        """
        Sends money from the user to a contact.
        If confirmed=false, return "REQUIRES_CONFIRMATION" with a human-readable summary.
        The agent should confirm with the user before retrying with confirmed=true.
        """
    )
    fun sendMoney(
        @LLMDescription("Sender user id.") senderId: Int,
        @LLMDescription("Amount in sender's default currency.") amount: Double,
        @LLMDescription("Recipient contact id.") recipientId: Int,
        @LLMDescription("Short purpose/description.") purpose: String,
        @LLMDescription("Whether the user already confirmed this transfer.") confirmed: Boolean = false
    ): String {
        val recipient = contactById[recipientId] ?: return "Invalid recipient."
        val summary = "Transfer €%.2f to %s %s (%s) for \"%s\"."
            .format(amount, recipient.name, recipient.surname ?: "", recipient.phoneNumber, purpose)

        if (!confirmed) {
            return "REQUIRES_CONFIRMATION: $summary"
        }

        // In a real system this is where you'd call a payment API.
        return "Money was sent. $summary"
    }
}
```

## Creating Your First Agent
Now let's create an agent that uses our money transfer tools.
An agent combines an LLM with tools to accomplish tasks.


```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking

val transferAgentService = AIAgentService(
    executor = openAIExecutor,
    llmModel = OpenAIModels.Chat.GPT4oMini,
    systemPrompt = bankingAssistantSystemPrompt,
    temperature = 0.0,  // Use deterministic responses for financial operations
    toolRegistry = ToolRegistry {
        tool(AskUser)
        tools(MoneyTransferTools().asTools())
    }
)

// Test the agent with various scenarios
println("Banking Assistant started")
val message = "Send 25 euros to Daniel for dinner at the restaurant."

// Other test messages you can try:
// - "Send 50 euros to Alice for the concert tickets"
// - "What's my current balance?"
// - "Transfer 100 euros to Bob for the shared vacation expenses"

runBlocking {
    val result = transferAgentService.createAgentAndRun(message)
    result
}
```

    Banking Assistant started
    There are two contacts named Daniel. Please confirm which one you would like to send money to:
    1. Daniel Anderson (+46 70 123 45 67)
    2. Daniel Garcia (+34 612 345 678)
    Please confirm the transfer of €25.00 to Daniel Garcia (+34 612 345 678) for "Dinner at the restaurant".





    Task completed successfully.

