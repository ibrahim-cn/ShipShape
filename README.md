# ShipShape

## Project Description

Preparing a web application for deployment can involve several checks, such as verifying website accessibility, HTTPS usage, security headers, and Docker configuration. Developers may overlook some of these issues before publishing their applications. ShipShape aims to make these basic checks easier to perform in one place.

## Objective

The objective of ShipShape is to develop a web-based tool that helps developers identify potential issues before deploying their applications. The planned system will check a given website URL and analyze Docker configuration files, presenting the results with explanations and possible solutions.

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

- Check whether a website is accessible using its URL.
- Display the HTTP status code and response time.
- Check HTTPS usage.
- Inspect selected HTTP security headers and identify missing headers.
- Upload and analyze Dockerfiles for basic configuration issues.
- Display results as Pass, Warning, or Fail, with explanations and suggestions for improvement.

## Scope

ShipShape focuses on basic pre-deployment checks for websites and Dockerfiles.

**In scope:**
- Website accessibility and HTTP status checks
- HTTPS and response-time checks
- Checks for selected HTTP security headers
- Basic rule-based Dockerfile analysis
- Results with explanations and improvement suggestions

**Out of scope:**
- Penetration testing and advanced security analysis
- Continuous website monitoring
- Kubernetes configuration analysis
- Automatic correction of detected issues
- Executing uploaded Dockerfiles

Uploaded Dockerfiles will be analyzed as text and will not be executed. Website URL checks will validate user input and block requests to internal or private network addresses.

## Current Status

**Week 2 – Requirements Analysis**

The project description, target users, and initial scope have been defined. Functional and non-functional requirements, user stories, acceptance criteria, and a development milestone plan have been prepared.

The next step is to design the system architecture, define the backend API endpoints, and prepare the React and Spring Boot project structure. AWS deployment options will also be reviewed.

The planned features have not yet been implemented.
