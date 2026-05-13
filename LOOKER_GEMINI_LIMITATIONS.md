# Looker Gemini & MCP Integration Limitations

This document outlines the architectural constraints and limitations regarding the use of Looker's built-in Gemini 2.5 entitlement with external protocols like the Model Context Protocol (MCP) and Agent-to-Agent (A2A) frameworks.

## 1. Looker Gemini 2.5 is Not a Raw LLM API
* **Constraint**: The "unlimited Gemini 2.5" access included in the Enterprise Looker subscription is deeply integrated into Looker's product suite (e.g., Looker Conversational Analytics, Explore Assistant).
* **Limitation**: Google does **not** expose this entitlement as a general-purpose, standalone LLM API (like the Vertex AI Gemini API or Google AI Studio). You cannot generate a raw API key from Looker to power external custom applications, LangChain agents, or Spring AI clients.

## 2. Incompatibility with Custom MCP Clients
* **Constraint**: The Model Context Protocol (MCP) requires an "MCP Client" (an AI Agent acting as the brain) to connect to an "MCP Server" (the tool/data source, such as a BigQuery MCP server).
* **Limitation**: Because Looker's Gemini cannot be used as a raw LLM API, you cannot use it as the "brain" for a custom MCP client (e.g., inside a Spring Boot application). Connecting a custom Spring Boot MCP client to the BigQuery MCP Server would require purchasing a separate Vertex AI API subscription.

## 3. Why the A2A Protocol Doesn't Work Here
It is common to confuse the **A2A (Agent-to-Agent)** protocol with the **MCP (Model Context Protocol)**. Here is exactly why you cannot use A2A between your Spring Boot app and the BigQuery MCP server:

* **Mismatched Roles (Agent vs. Tool)**: The A2A protocol is strictly for two "smart" Agents (both equipped with their own LLM brains) to collaborate. The BigQuery MCP server is **not an Agent**; it is a "dumb" tool that just exposes database schemas and executes SQL. You cannot use an Agent-to-Agent protocol to talk to a tool. Tools require the MCP protocol.
* **The Missing "Brain" Problem**: If you want your Spring Boot application to connect directly to the BigQuery MCP server (using the correct MCP protocol), your Spring Boot application must act as the "Client/Agent". To do this, Spring Boot requires its own LLM "brain" to understand the user's question and decide what SQL to write. That means you would have to buy a separate Vertex AI or OpenAI API key, violating your requirement for no extra costs.
* **Looker's Agent is Walled Off**: You might wonder, "Can I use A2A to have Spring Boot talk to Looker's Gemini Agent, and have Looker talk to BigQuery?" No. Looker's built-in Gemini does not support or expose an A2A endpoint. It can only be accessed via Looker's proprietary REST API (the Conversational Analytics API).

## Summary & Recommended Architecture
If the strict requirement is to **avoid paying for any additional Gemini API subscriptions** and rely solely on the Enterprise Looker entitlement:
* **Avoid**: Attempting to use the BigQuery MCP Server, custom Spring Boot MCP clients, or A2A protocols.
* **Adopt**: Route all natural language processing and BigQuery execution through the **Looker Conversational Analytics API**. This API leverages Looker's internal Gemini and semantic layer, keeping all usage within the existing Enterprise subscription.
