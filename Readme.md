# BigQuery AI Agent — Google Conversational Analytics API (Java)

Natural language questions → Gemini → BigQuery → Answers.  
Built with **Spring Boot 3** using Google’s **Conversational Analytics API** (`geminidataanalytics.googleapis.com`).

-----

## How it works

```
Your API call  (POST /ask)
      ↓
Spring Boot Agent  (port 8000)
      ↓
Conversational Analytics API  (geminidataanalytics.googleapis.com)
      ↓
Gemini reasons over your question
      ↓
Generates + executes SQL on BigQuery
      ↓
Returns answer (text + SQL used)
```

-----

## GCP Setup (one-time)

```bash
# 1. Enable required APIs
gcloud services enable geminidataanalytics.googleapis.com \
                       cloudaicompanion.googleapis.com \
                       bigquery.googleapis.com \
                       --project=YOUR_PROJECT_ID

# 2. Grant your service account the required roles
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_SA@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/geminidataanalytics.dataAgentCreator"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_SA@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/bigquery.dataViewer"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:YOUR_SA@YOUR_PROJECT.iam.gserviceaccount.com" \
  --role="roles/bigquery.jobUser"
```

-----

## Running Locally

```bash
# Option A: Service account key file
export GCP_PROJECT_ID=your-project-id
export GCP_SA_KEY_PATH=/path/to/sa.json
mvn spring-boot:run

# Option B: Application Default Credentials (ADC)
gcloud auth application-default login
export GCP_PROJECT_ID=your-project-id
mvn spring-boot:run
```

-----

## API Reference

### `GET /health`

```bash
curl http://localhost:8000/health
```

-----

### `POST /ask` — Three usage modes

#### Mode 1: Inline context (no setup needed, send schema every time)

```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question":   "What are the top 10 customers by revenue?",
    "project_id": "my-gcp-project",
    "dataset_id": "sales",
    "table_ids":  ["orders", "customers"]
  }'
```

#### Mode 2: Persistent agent (faster, more accurate — create once, reuse forever)

```bash
# First: create the agent (one-time)
curl -X POST http://localhost:8000/agents/create \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id":            "sales-agent",
    "project_id":          "my-gcp-project",
    "dataset_id":          "sales",
    "table_ids":           ["orders", "customers", "products"],
    "system_instructions": "You are a sales analyst. Focus on revenue and customer metrics."
  }'

# Then: ask questions using the agent
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question":   "What was revenue last quarter?",
    "project_id": "my-gcp-project",
    "agent_id":   "sales-agent"
  }'
```

#### Mode 3: Multi-turn conversation (agent remembers previous questions)

```bash
# First question
RESPONSE=$(curl -s -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "Total revenue last month?", "project_id": "...", "agent_id": "sales-agent"}')

CONV_ID=$(echo $RESPONSE | python3 -c "import sys,json; print(json.load(sys.stdin)['conversation_id'])")

# Follow-up (agent remembers the previous question)
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d "{
    \"question\":        \"Now break that down by region\",
    \"project_id\":      \"my-gcp-project\",
    \"agent_id\":        \"sales-agent\",
    \"conversation_id\": \"${CONV_ID}\"
  }"
```

**Response shape:**

```json
{
  "answer":          "Top 10 customers by revenue are...",
  "sql_used":        "SELECT customer_id, SUM(revenue) FROM ...",
  "conversation_id": "abc-123",
  "agent_id":        "sales-agent"
}
```

Add `?debug=true` to also get the agent’s reasoning steps in `thoughts[]`.

-----

### `POST /ask/raw`

Returns the raw NDJSON stream from the CA API — every THOUGHT, SQL query, data table, and chart spec.

-----

### `POST /agents/create`

Creates a persistent, reusable data agent in GCP.

-----

## Deploy to Cloud Run

```bash
docker build -t gcr.io/YOUR_PROJECT/bq-agent-looker .
docker push gcr.io/YOUR_PROJECT/bq-agent-looker

gcloud run deploy bq-agent-looker \
  --image gcr.io/YOUR_PROJECT/bq-agent-looker \
  --set-env-vars GCP_PROJECT_ID=your-project \
  --service-account your-sa@your-project.iam.gserviceaccount.com \
  --region us-central1 \
  --allow-unauthenticated
```

On Cloud Run, no `GCP_SA_KEY_PATH` needed — ADC is used automatically via the service account.

-----

## Troubleshooting

|Error                  |Fix                                                                       |
|-----------------------|--------------------------------------------------------------------------|
|`403 PERMISSION_DENIED`|Grant `roles/geminidataanalytics.dataAgentCreator` to your service account|
|`API not enabled`      |Run `gcloud services enable geminidataanalytics.googleapis.com`           |
|`No answer returned`   |Try `/ask/raw` to see full agent response stream                          |
|`Token refresh failed` |Check `GCP_SA_KEY_PATH` or run `gcloud auth application-default login`    |

-----

## Step-by-Step Execution Guide

### Prerequisites

- **Java 21** — [Download](https://www.oracle.com/java/technologies/downloads/#java21)
- **Maven 3.9+** — [Download](https://maven.apache.org/download.cgi)
- **gcloud CLI** — [Install Guide](https://cloud.google.com/sdk/docs/install)
- **GCP Account** with billing enabled (free trial $300 works)

-----

### Step 1 — Authenticate with GCP

```bash
# Login to GCP
gcloud auth login

# Set your project
gcloud config set project YOUR_PROJECT_ID

# Set up Application Default Credentials (ADC)
# This creates credentials that the Java app can use
gcloud auth application-default login
```

-----

### Step 2 — Enable Required APIs

```bash
gcloud services enable bigquery.googleapis.com \
                       geminidataanalytics.googleapis.com \
                       cloudaicompanion.googleapis.com \
                       --project=YOUR_PROJECT_ID
```

-----

### Step 3 — Grant IAM Roles

```bash
# Replace YOUR_EMAIL with your GCP account email

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="user:YOUR_EMAIL" \
  --role="roles/geminidataanalytics.dataAgentCreator"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="user:YOUR_EMAIL" \
  --role="roles/bigquery.dataEditor"

gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="user:YOUR_EMAIL" \
  --role="roles/bigquery.jobUser"
```

-----

### Step 4 — Create BigQuery Sample Dataset

#### 4a. Create the dataset

```bash
bq mk --dataset --location=US YOUR_PROJECT_ID:sample_sales
```

#### 4b. Load sample data

Run the SQL in `sql/setup_sample_data.sql` via the [BigQuery Console](https://console.cloud.google.com/bigquery).
Execute each section (CREATE + INSERT) one at a time:

1. **Customers** — 15 rows (name, email, city, country, segment)
2. **Products** — 10 rows (name, category, price, cost)
3. **Orders** — 40 rows (customer_id, product_id, quantity, total_amount, order_date, status, region)

#### 4c. Verify data loaded

```sql
SELECT 'customers' AS table_name, COUNT(*) AS row_count FROM `YOUR_PROJECT_ID.sample_sales.customers`
UNION ALL
SELECT 'products',  COUNT(*) FROM `YOUR_PROJECT_ID.sample_sales.products`
UNION ALL
SELECT 'orders',    COUNT(*) FROM `YOUR_PROJECT_ID.sample_sales.orders`;
```

Expected: `customers → 15`, `products → 10`, `orders → 40`

-----

### Step 5 — Configure the App

Edit `src/main/resources/application.properties`:

```properties
gcp.project.id=${GCP_PROJECT_ID:YOUR_PROJECT_ID}
```

Or set it as an environment variable:

```bash
# Linux/Mac
export GCP_PROJECT_ID=YOUR_PROJECT_ID

# Windows (PowerShell)
$env:GCP_PROJECT_ID = "YOUR_PROJECT_ID"
```

-----

### Step 6 — Start the Application

```bash
mvn spring-boot:run
```

You should see:

```
Started DemoApplication in X seconds
Tomcat started on port 8000
No SA key path — using Application Default Credentials
```

-----

### Step 7 — Verify Health

```bash
curl http://localhost:8000/health
```

Expected response:

```json
{
  "status":  "ok",
  "api":     "geminidataanalytics.googleapis.com",
  "project": "YOUR_PROJECT_ID"
}
```

-----

### Step 8 — Create a Persistent Agent (one-time)

```bash
curl -X POST http://localhost:8000/agents/create \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id":            "sales-agent",
    "project_id":          "YOUR_PROJECT_ID",
    "dataset_id":          "sample_sales",
    "table_ids":           ["customers", "orders", "products"],
    "system_instructions": "You are a sales data analyst. Focus on revenue, customer metrics, and product performance. Always include relevant numbers."
  }'
```

> **Note:** Agent creation is asynchronous. The response will show `"done": false`.
> Wait **1–2 minutes** for the agent to finish provisioning before asking questions.

-----

### Step 9 — Ask Questions!

```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question":   "What are my top 5 customers by total revenue?",
    "project_id": "YOUR_PROJECT_ID",
    "agent_id":   "sales-agent"
  }'
```

**Example questions to try:**

| # | Question |
|---|----------|
| 1 | What are my top 5 customers by total revenue? |
| 2 | Which product category generates the most revenue? |
| 3 | Show me monthly revenue trends |
| 4 | How many orders were cancelled and what was the lost revenue? |
| 5 | What is the average order value by region? |
| 6 | Which country has the most enterprise customers? |
| 7 | Compare APAC vs Americas revenue |
| 8 | What is the profit margin by product? |

**Expected response:**

```json
{
  "answer":          "The top 5 customers by total revenue are...",
  "sql_used":        "SELECT c.name, SUM(o.total_amount) AS revenue FROM ...",
  "conversation_id": "abc-123",
  "agent_id":        "sales-agent"
}
```

-----

### Step 10 — Multi-turn Conversations (optional)

Use the `conversation_id` from a previous response to ask follow-up questions:

```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question":        "Now break that down by region",
    "project_id":      "YOUR_PROJECT_ID",
    "agent_id":        "sales-agent",
    "conversation_id": "CONVERSATION_ID_FROM_PREVIOUS_RESPONSE"
  }'
```

-----

### Step 11 — Debug with Raw Response (optional)

To see the full Gemini reasoning chain (thoughts, SQL, data):

```bash
curl -X POST http://localhost:8000/ask/raw \
  -H "Content-Type: application/json" \
  -d '{
    "question":   "Total revenue by product",
    "project_id": "YOUR_PROJECT_ID",
    "agent_id":   "sales-agent"
  }'
```

Or add `?debug=true` to `/ask` to include thoughts in the response:

```bash
curl -X POST "http://localhost:8000/ask?debug=true" \
  -H "Content-Type: application/json" \
  -d '{
    "question":   "Total revenue by product",
    "project_id": "YOUR_PROJECT_ID",
    "agent_id":   "sales-agent"
  }'
```