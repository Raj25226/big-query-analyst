# Looker Gemini & MCP Integration Limitations

This document outlines the architectural constraints and limitations regarding the use of Looker's built-in Gemini 2.5 entitlement with external protocols like the Model Context Protocol (MCP) and Agent-to-Agent (A2A) frameworks.

## 1. Looker Gemini 2.5 is Not a Raw LLM API
* **Constraint**: The "unlimited Gemini 2.5" access included in the Enterprise Looker subscription is deeply integrated into Looker's product suite (e.g., Looker Conversational Analytics, Explore Assistant).
* **Limitation**: Google does **not** expose this entitlement as a general-purpose, standalone LLM API (like the Vertex AI Gemini API or Google AI Studio). You cannot generate a raw API key from Looker to power external custom applications, LangChain agents, or Spring AI clients.

## 2. Incompatibility with Custom MCP Clients
* **Constraint**: The Model Context Protocol (MCP) requires an "MCP Client" (an AI Agent acting as the brain) to connect to an "MCP Server" (the tool/data source, such as a BigQuery MCP server).
* **Limitation**: Because Looker's Gemini cannot be used as a raw LLM API, you cannot use it as the "brain" for a custom MCP client (e.g., inside a Spring Boot application). Connecting a custom Spring Boot MCP client to the BigQuery MCP Server would require purchasing a separate Vertex AI API subscription.

## 3. A2A Protocol Does Not Solve the API Constraint
* **Constraint**: The Agent-to-Agent (A2A) protocol is designed for horizontal collaboration (agents delegating tasks to other agents), whereas MCP is for vertical integration (agents talking to tools). 
* **Limitation**: Implementing A2A in a Spring Boot application does not bypass the need for an underlying LLM. Looker does not currently offer an A2A endpoint that exposes its Gemini agent to external applications.

## Summary & Recommended Architecture
If the strict requirement is to **avoid paying for any additional Gemini API subscriptions** and rely solely on the Enterprise Looker entitlement:
* **Avoid**: Attempting to use the BigQuery MCP Server, custom Spring Boot MCP clients, or A2A protocols.
* **Adopt**: Route all natural language processing and BigQuery execution through the **Looker Conversational Analytics API**. This API leverages Looker's internal Gemini and semantic layer, keeping all usage within the existing Enterprise subscription.
