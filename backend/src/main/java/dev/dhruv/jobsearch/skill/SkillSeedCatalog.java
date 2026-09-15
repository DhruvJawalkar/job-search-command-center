package dev.dhruv.jobsearch.skill;

import java.util.List;

final class SkillSeedCatalog {

    private SkillSeedCatalog() {}

    static List<SeedSkill> reviewedBacklog() {
        return List.of(
                skill("AI agents", SkillCategory.AI_AGENT, "Agentic AI systems that reason, use tools, and execute workflows.", "AI agent", "agentic AI"),
                skill("AI agents, skills, workflows, and automation capabilities", SkillCategory.AI_AGENT, "Composable agents, reusable skills, governed workflows, and automation capabilities for reliable business and engineering processes."),
                skill("Agent SDKs", SkillCategory.AI_AGENT, "SDKs for agent tools, handoffs, sessions, state, tracing, guardrails, and production integration.", "Agent SDK", "Agents SDK"),
                skill("Agent orchestration", SkillCategory.AI_AGENT, "Frameworks and patterns for stateful, multi-step and multi-agent execution.", "AI orchestration", "agent orchestration frameworks", "Agent orchestration and execution frameworks", "multi-agent orchestration"),
                skill("Agentic AI development patterns", SkillCategory.AI_AGENT, "Multi-step reasoning, tool calling, retrieval, human approval, verification, and recovery patterns for agentic systems.", "agentic development patterns"),
                skill("Agentic engineering toolchain", SkillCategory.AI_AGENT, "Coding agents, harnesses, MCP servers, reusable skills, and agent frameworks used to ship verified software.", "agentic engineering tools", "coding harnesses"),
                skill("Agent observability and evaluation", SkillCategory.AI_AGENT, "Tracing, evaluation, quality measurement, and production monitoring for agents.", "agent evaluation", "AI evaluation", "LLM evaluation", "agent tracing"),
                skill("Agent memory", SkillCategory.AI_AGENT, "Short- and long-term memory systems for agent workflows.", "agent memory systems"),
                skill("Modern AI platforms and agent frameworks", SkillCategory.AI_AGENT, "Selection and use of modern model platforms and agent frameworks across orchestration, evaluation, governance, and deployment."),
                skill("Azure OpenAI", SkillCategory.AI_AGENT, "Azure-hosted OpenAI model access and enterprise operations.", "Azure OpenAI Service"),
                skill("Azure AI Foundry", SkillCategory.AI_AGENT, "Microsoft platform for building, evaluating, governing, and deploying AI applications.", "AI Foundry", "Microsoft Foundry"),
                skill("Microsoft Agent Framework", SkillCategory.AI_AGENT, "Microsoft SDK and runtime patterns for production agents and workflows."),
                skill("Semantic Kernel", SkillCategory.AI_AGENT, "Microsoft SDK for model orchestration, plugins, memory, and agents."),
                skill("Model Context Protocol", SkillCategory.AI_AGENT, "Standard protocol for connecting AI applications to tools and context.", "MCP", "MCP servers", "MCP services"),
                skill("FastMCP", SkillCategory.AI_AGENT, "Framework and tooling for building Model Context Protocol servers, tools, resources, and clients.", "Fast MCP"),
                skill("LangChain", SkillCategory.AI_AGENT, "Framework for LLM applications, tools, retrieval, and chains."),
                skill("LangGraph", SkillCategory.AI_AGENT, "Stateful graph orchestration for durable agent workflows.", "Lang Graph"),
                skill("AutoGen", SkillCategory.AI_AGENT, "Framework for conversational and multi-agent applications.", "Auto Gen"),
                skill("CrewAI", SkillCategory.AI_AGENT, "Framework for role-based multi-agent workflows.", "Crew AI"),
                skill("Copilot Studio", SkillCategory.AI_AGENT, "Microsoft low-code platform for governed enterprise agents."),
                skill("OpenAI", SkillCategory.AI_AGENT, "OpenAI APIs, SDKs, models, and agent tooling.", "OpenAI API", "OpenAI SDK"),
                skill("Anthropic", SkillCategory.AI_AGENT, "Anthropic models and agentic coding tooling.", "Claude", "Claude Code"),
                skill("Retrieval-Augmented Generation", SkillCategory.AI_AGENT, "Retrieval-grounded generation using enterprise knowledge.", "RAG", "retrieval augmented generation"),
                skill("Tool calling", SkillCategory.AI_AGENT, "Model-directed invocation of external tools and functions.", "function calling", "tool use"),
                skill("Human-in-the-loop workflows", SkillCategory.AI_AGENT, "Explicit approval and escalation gates in automated workflows.", "human in the loop", "HITL", "human approval"),
                skill("LLM serving and inference", SkillCategory.AI_AGENT, "Serving, scaling, and operating large-language-model inference.", "LLM serving", "LLM inference", "inference frameworks", "LLM serving and inference frameworks"),
                skill("Production-grade AI solution delivery", SkillCategory.AI_AGENT, "Shipping and operating AI-driven capabilities with deployment, evaluation, SLOs, and customer feedback practices.", "production AI delivery", "production AI solutions"),
                skill("Unified AIOps Platform", SkillCategory.AI_AGENT, "Unified telemetry, event correlation, incident intelligence, automation, approvals, and outcome verification.", "Unified AIOps", "AIOps platform"),
                skill("Automated AIOps remediation workflows", SkillCategory.AI_AGENT, "Guardrailed incident remediation with approvals, rollback, and outcome verification.", "AIOps remediation", "automated remediation workflows"),
                skill("Vector search", SkillCategory.AI_AGENT, "Embedding-based semantic retrieval and vector database integration.", "vector database", "vector databases", "embedding search", "embeddings"),
                skill("Java", SkillCategory.BACKEND, "Production backend engineering with modern Java.", "Java 17", "Java 21"),
                skill("Spring Boot", SkillCategory.BACKEND, "Java service development using Spring Boot.", "Spring", "Spring Framework"),
                skill("REST APIs", SkillCategory.BACKEND, "Design and operation of HTTP-based service APIs.", "REST API", "RESTful APIs"),
                skill("Microservices", SkillCategory.BACKEND, "Distributed service boundaries, APIs, and operational ownership.", "microservice architecture", "microservices architecture"),
                skill("Distributed systems", SkillCategory.BACKEND, "Scalable and reliable systems spanning multiple processes or nodes."),
                skill("Event-driven architecture", SkillCategory.BACKEND, "Asynchronous services and workflows driven by events.", "event driven architecture", "event-driven systems"),
                skill("Concurrency", SkillCategory.BACKEND, "Thread-safe, concurrent, and memory-efficient production systems.", "multithreading", "multi-threading", "concurrent programming"),
                skill("Asynchronous job processing", SkillCategory.BACKEND, "Reliable background jobs, queues, retries, and idempotency.", "async jobs", "job scheduling", "job scheduler"),
                skill("AWS", SkillCategory.CLOUD_INFRASTRUCTURE, "Amazon Web Services cloud platform.", "Amazon Web Services"),
                skill("Microsoft Azure", SkillCategory.CLOUD_INFRASTRUCTURE, "Microsoft Azure cloud platform.", "Azure"),
                skill("Google Cloud", SkillCategory.CLOUD_INFRASTRUCTURE, "Google Cloud Platform.", "GCP", "Google Cloud Platform"),
                skill("Oracle Cloud Infrastructure", SkillCategory.CLOUD_INFRASTRUCTURE, "Oracle Cloud Infrastructure platform.", "OCI", "Oracle Cloud"),
                skill("Kubernetes", SkillCategory.CLOUD_INFRASTRUCTURE, "Container orchestration and production workload operation.", "K8s"),
                skill("Docker", SkillCategory.CLOUD_INFRASTRUCTURE, "Container packaging and execution.", "containers", "containerization"),
                skill("Terraform", SkillCategory.CLOUD_INFRASTRUCTURE, "Infrastructure-as-code provisioning and change management.", "OpenTofu", "infrastructure as code", "IaC"),
                skill("CI/CD", SkillCategory.DEVOPS, "Automated build, test, and deployment pipelines.", "continuous integration", "continuous delivery", "continuous deployment"),
                skill("Jenkins", SkillCategory.DEVOPS, "Build and continuous-integration automation."),
                skill("Argo CD", SkillCategory.DEVOPS, "GitOps-based Kubernetes deployment automation.", "ArgoCD"),
                skill("Observability", SkillCategory.DEVOPS, "Metrics, logging, tracing, alerting, and production diagnosis.", "monitoring", "telemetry"),
                skill("Apache Spark", SkillCategory.DATA_PLATFORM, "Distributed batch and streaming data processing.", "Spark", "PySpark"),
                skill("Apache Kafka", SkillCategory.DATA_PLATFORM, "Distributed event streaming and ingestion.", "Kafka"),
                skill("Apache Flink", SkillCategory.DATA_PLATFORM, "Stateful real-time stream processing.", "Flink"),
                skill("Delta Lake", SkillCategory.DATA_PLATFORM, "Transactional lakehouse storage and table management.", "Delta"),
                skill("Apache Iceberg", SkillCategory.DATA_PLATFORM, "Open table format for large analytic datasets.", "Iceberg"),
                skill("Apache Hive", SkillCategory.DATA_PLATFORM, "Distributed data warehouse and SQL ecosystem.", "Hive"),
                skill("Apache Airflow", SkillCategory.DATA_PLATFORM, "Data-pipeline scheduling and orchestration.", "Airflow"),
                skill("Kubeflow", SkillCategory.DATA_PLATFORM, "Kubernetes-native machine-learning workflows."),
                skill("MLflow", SkillCategory.DATA_PLATFORM, "ML experiment tracking, model registry, and lifecycle management."),
                skill("Ray", SkillCategory.DATA_PLATFORM, "Distributed Python and AI workload execution."),
                skill("Snowflake", SkillCategory.DATA_PLATFORM, "Cloud data platform and application ecosystem."),
                skill("Data lakehouse", SkillCategory.DATA_PLATFORM, "Lakehouse architecture combining data-lake and warehouse capabilities.", "lakehouse", "data lake"),
                skill("SQL", SkillCategory.DATABASE, "Relational querying, transformations, and performance tuning."),
                skill("NoSQL", SkillCategory.DATABASE, "Non-relational data modeling and storage.", "NoSQL databases"),
                skill("PostgreSQL", SkillCategory.DATABASE, "PostgreSQL application and data-platform usage.", "Postgres"),
                skill("MySQL", SkillCategory.DATABASE, "MySQL application and data-platform usage."),
                skill("Identity and access management", SkillCategory.SECURITY, "Authentication, authorization, and enterprise identity controls.", "IAM", "identity management"),
                skill("OAuth 2.0 and OpenID Connect", SkillCategory.SECURITY, "Standards-based delegated authorization and authentication.", "OAuth", "OAuth2", "OIDC", "OpenID Connect"),
                skill("Secrets management", SkillCategory.SECURITY, "Secure storage, rotation, and use of application secrets.", "secret management"),
                skill("Encryption", SkillCategory.SECURITY, "Encryption and protection of data in transit and at rest.", "AES-256", "TLS"),
                skill("Secure enterprise tool integrations", SkillCategory.SECURITY, "Governed, least-privilege connections between AI agents and enterprise tools.", "secure tool integrations", "secure agentic workflows"));
    }

    private static SeedSkill skill(String name, SkillCategory category, String description, String... aliases) {
        return new SeedSkill(name, category, description, List.of(aliases));
    }

    record SeedSkill(String name, SkillCategory category, String description, List<String> aliases) {}
}
