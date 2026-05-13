# big-query-analyst — Looker Conversational Analytics API (Java)

Natural language questions → Looker Gemini → BigQuery → Answers.  
Built with **Spring Boot 3** using **Looker's Conversational Analytics REST API v4.0**.

-----

## How it works

```
Your API call  (POST /ask)
      ↓
Spring Boot App  (port 8000)
      ↓
Looker Auth API (/api/4.0/login) -> Bearer Token
      ↓
Looker Chat API (/api/4.0/conversational_analytics/chat)
      ↓
Looker Gemini reasons over your semantic model (Explore)
      ↓
Generates + executes SQL on BigQuery
      ↓
Returns answer (text + data)
```

-----

## Prerequisites

1. **Java 21** — [Download](https://www.oracle.com/java/technologies/downloads/#java21)
2. **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
3. **Looker Enterprise Instance** with Gemini enabled.
4. **Looker API Credentials** (`client_id` and `client_secret`) generated from your Looker User Profile.
5. **Looker Model & Explore** properly configured in your Looker instance pointing to your BigQuery data.

-----

## Step-by-Step Execution Guide

### Step 1 — Configure Looker Credentials

You must provide the application with your Looker instance URL and API keys.

Edit `src/main/resources/application.properties` and add your details:

```properties
looker.base.url=https://your-instance.looker.com:19999
looker.client.id=YOUR_LOOKER_CLIENT_ID
looker.client.secret=YOUR_LOOKER_CLIENT_SECRET
```

Alternatively, you can set these as environment variables before starting the application:

```bash
# Linux/Mac
export LOOKER_BASE_URL="https://your-instance.looker.com:19999"
export LOOKER_CLIENT_ID="YOUR_LOOKER_CLIENT_ID"
export LOOKER_CLIENT_SECRET="YOUR_LOOKER_CLIENT_SECRET"

# Windows (PowerShell)
$env:LOOKER_BASE_URL="https://your-instance.looker.com:19999"
$env:LOOKER_CLIENT_ID="YOUR_LOOKER_CLIENT_ID"
$env:LOOKER_CLIENT_SECRET="YOUR_LOOKER_CLIENT_SECRET"
```

-----

### Step 2 — Start the Application

Run the application using Maven:

```bash
mvn spring-boot:run
```

You should see:

```
Started DemoApplication in X seconds
Tomcat started on port 8000
```

-----

### Step 3 — Verify Health

Check if the API is running correctly:

```bash
curl http://localhost:8000/health
```

Expected response:

```json
{
  "status":  "ok",
  "api":     "Looker Conversational Analytics API (v4.0)",
  "lookerUrl": "https://your-instance.looker.com:19999"
}
```

-----

### Step 4 — Initialize a Conversation (Required)

Looker's Chat API requires an active `conversation_id`. First, create a new conversation:

```bash
curl -X POST http://localhost:8000/conversations/create
```

Save the `id` or `name` returned in the JSON payload. You will need this `conversation_id` for all subsequent chat requests.

-----

### Step 5 — Create an Agent (Optional but Recommended)

You can create a persistent agent tied to a specific Looker model and explore.

```bash
curl -X POST http://localhost:8000/agents/create \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sales-agent",
    "description": "Sales data analyst",
    "looker_model": "your_looker_model_name",
    "looker_explore": "your_looker_explore_name"
  }'
```

-----

### Step 6 — Ask Questions!

Use the `conversation_id` generated in Step 4 to ask questions. You can either use the `agent_id` you created in Step 5, or pass inline context directly.

**Option A: Using the Persistent Agent**
```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are my top 5 customers by total revenue?",
    "conversation_id": "YOUR_CONVERSATION_ID",
    "agent_id": "sales-agent"
  }'
```

**Option B: Using Inline Looker Context (No predefined agent)**
```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What are my top 5 customers by total revenue?",
    "conversation_id": "YOUR_CONVERSATION_ID",
    "looker_model": "your_looker_model_name",
    "looker_explore": "your_looker_explore_name"
  }'
```

**Expected response format:**

```json
{
  "answer": "The top 5 customers by total revenue are...",
  "conversation_id": "YOUR_CONVERSATION_ID",
  "agent_id": null,
  "sql_used": null,
  "thoughts": null
}
```

-----

### Troubleshooting

| Error | Fix |
|-------|-----|
| `Failed to get Looker access token` | Verify your `looker.client.id` and `looker.client.secret` in `application.properties`. |
| `Looker CA API chat failed [400]` | Ensure you have passed a valid `conversation_id`. |
| `Connection Refused` | Ensure your `looker.base.url` includes the correct port (e.g., `:19999` is standard for Looker API unless routed over standard 443). |