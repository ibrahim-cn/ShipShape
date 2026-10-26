# ShipShape – AWS Service Selection

## 1. Overview

ShipShape is a Pre-Deployment Readiness Checker built with React and TypeScript on the frontend and Java with Spring Boot on the backend.

The project requires website reachability checks, HTTP status inspection, response time measurement, HTTPS verification, selected HTTP security header checks, and static Dockerfile analysis.

The AWS architecture is designed to keep the application continuously accessible while minimizing infrastructure costs.

## 2. Selected AWS Services

### 2.1 AWS Amplify Hosting

**Purpose:** Host the React and TypeScript frontend.

**Reason for selection:**
- Supports hosting web frontend applications.
- Provides Git-based deployment workflows.
- Reduces the need to manage frontend servers manually.
- Supports HTTPS for the hosted application.

### 2.2 Amazon API Gateway – HTTP API

**Purpose:** Provide REST endpoints for communication between the frontend and backend.

**Reason for selection:**
- Routes frontend requests to backend functions.
- Supports an HTTP API model suitable for the project's REST endpoints.
- Provides a managed API entry point without requiring a continuously running API server.

Planned endpoints:

- `POST /api/v1/website-checks`
- `POST /api/v1/dockerfile-analysis`

### 2.3 AWS Lambda

**Purpose:** Run backend processing when API requests arrive.

**Reason for selection:**
- Executes code on demand.
- Removes the need to maintain a continuously running server.
- Can be suitable for a project with low or unpredictable request volume.

The backend is planned to use Java. The existing Spring Boot application will need a Lambda-compatible integration and packaging approach before deployment.

### 2.4 Amazon CloudWatch

**Purpose:** Collect application logs and monitor backend execution.

**Reason for selection:**
- Helps investigate request failures and timeouts.
- Supports troubleshooting during testing and demonstration.
- Provides visibility into Lambda execution behavior.

Log retention and monitoring configuration will be kept cost-conscious.

### 2.5 AWS Budgets

**Purpose:** Monitor AWS spending and configure cost notifications.

**Reason for selection:**
- Helps track project spending against the available AWS credits.
- Provides budget notifications to reduce the risk of unexpected costs.

A budget notification is not a hard spending limit and does not automatically stop all chargeable resources.

## 3. Planned Architecture

1. The user accesses the frontend hosted on AWS Amplify.
2. The frontend sends HTTPS requests to Amazon API Gateway.
3. API Gateway invokes the appropriate AWS Lambda function.
4. The backend validates the submitted URL before making outbound requests.
5. Website checks or static Dockerfile analysis are performed.
6. The backend returns structured results to the frontend.
7. CloudWatch collects execution logs for troubleshooting.

## 4. Cost Optimization

The architecture prioritizes managed services that can scale with demand.

The following measures are planned:

- Use AWS Amplify and Lambda within applicable free usage allowances where possible.
- Use API Gateway HTTP API instead of a more feature-rich API option when its capabilities are sufficient.
- Avoid provisioning a database because the current project requirements do not require persistent storage.
- Avoid Amazon EKS and other unnecessary infrastructure.
- Configure AWS Budgets and review actual usage regularly.
- Configure CloudWatch log retention to avoid keeping unnecessary logs indefinitely.
- Check regional pricing, data transfer, and applicable free-tier conditions before deployment.

AWS credits may offset eligible charges, but the project will not assume that every service or usage type is covered.

## 5. Alternatives Considered

### Amazon EC2

EC2 could host the Spring Boot application in a Docker container. However, a continuously running instance can incur compute charges even when the application receives no requests. It would also require additional server administration.

For this project's low-cost, continuously accessible design, Lambda is the preferred initial option.

### Amazon RDS

A relational database is not necessary for the current MVP because the project does not require persistent user accounts or stored analysis history.

RDS can be reconsidered if future requirements introduce persistent data.

### Amazon ECS and Amazon EKS

Container orchestration is not required for the initial MVP. ECS or EKS would introduce additional configuration and potential costs without being necessary for the current functionality.

Docker may still be used locally for development and testing.

## 6. Technical Considerations and Risks

- Spring Boot must be adapted for AWS Lambda rather than assumed to run as a conventional long-running web server.
- Cold starts and request timeouts must be tested against the website-check response-time target.
- Website checks must block localhost, private IP addresses, internal destinations, and unsafe redirects.
- URL validation must account for DNS resolution and connection handling.
- The Dockerfile analyzer must inspect uploaded text only and must never execute the uploaded file.
- Uploaded file size and request limits must be considered when configuring API Gateway and Lambda.
- Costs depend on region, request volume, execution duration, memory configuration, data transfer, and service-specific pricing.

## 7. Decision

The initial deployment architecture will use AWS Amplify Hosting, Amazon API Gateway HTTP API, AWS Lambda, Amazon CloudWatch, and AWS Budgets.

This combination is selected to support continuous availability while minimizing server administration and reducing idle compute costs. The architecture will be validated through deployment tests and cost monitoring before the final project demonstration.

This document records the planned architecture. The services have not yet been deployed as part of this design decision.