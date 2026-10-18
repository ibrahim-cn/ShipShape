# ShipShape

## Project Description

Preparing a web application for deployment involves several checks, such as verifying website accessibility, HTTPS usage, security headers, and Docker configuration. Developers may overlook some of these issues before publishing their applications. ShipShape aims to make these basic checks easier to perform in one place.

## Objective

The objective of ShipShape is to develop a web-based tool that helps developers identify potential issues before deploying their applications. The system will check a given website URL and analyze Docker configuration files, presenting the results with explanations and possible solutions.

## Target Users

- Software engineering students
- Individual developers
- Small development teams

## Planned Technologies

- **Frontend:** React and TypeScript
- **Backend:** Java and Spring Boot
- **Deployment:** AWS
- **Version Control:** Git and GitHub

## Planned Features

### Website Checks

- Check whether a website is accessible using its URL.
- Display the HTTP status code.
- Measure and display the website's response time.
- Check whether the website uses HTTPS.
- Inspect selected HTTP security headers and identify missing headers.

### Dockerfile Analysis

- Allow users to upload a Dockerfile.
- Analyze the file using predefined rules.
- Identify basic Docker configuration issues.
- Provide explanations and suggestions for improvement.

### Results

- Display check results using **Pass**, **Warning**, and **Fail** statuses.
- Explain detected issues in a clear and understandable way.
- Provide suggestions to help users improve their deployment readiness.

## Project Scope

### In Scope

- Website accessibility and HTTP status checks
- HTTPS verification
- Response-time measurement
- Checks for selected HTTP security headers
- Rule-based Dockerfile analysis
- Results with explanations and improvement suggestions

### Out of Scope

- Penetration testing and advanced security analysis
- Continuous website monitoring
- Kubernetes configuration analysis
- Automatic correction of detected issues
- Execution of uploaded Dockerfiles

For safety, uploaded Dockerfiles will be analyzed as text and will never be executed. Website URL checks will validate user input and block requests to internal or private network addresses.

## Development Plan

| Week | Planned Work |
|---|---|
| Week 1 | Project definition and initial research |
| Week 2 | Requirements analysis and GitHub issue planning |
| Week 3 | System architecture, API design, and project structure |
| Week 4 | First working prototype with website reachability and HTTP status checks |
| Week 5 | HTTPS verification, response-time measurement, and security header analysis |
| Week 6 | Rule-based Dockerfile analysis, testing, and validation evidence |
| Week 7 | Frontend/backend integration and reproducibility |
| Week 8 | Report completion, README refinement, and AWS deployment preparation |
| Week 9 | AWS deployment, final demonstration, and presentation |

The plan may be adjusted as development progresses.

## Current Status

**Week 2 – Requirements Analysis**

The project description, target users, initial scope, functional requirements, and non-functional requirements have been defined. User stories, acceptance criteria, and the initial development plan have also been prepared. GitHub Issues have been created to track the main development tasks.

The next step is to design the system architecture, define the backend API endpoints, and prepare the React and Spring Boot project structure. AWS deployment options will also be reviewed.

**Note:** The planned features have not yet been implemented.
