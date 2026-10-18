# ShipShape: Pre-Deployment Readiness Checker

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
- Guaranteeing the complete security of an application

For safety, uploaded Dockerfiles will be analyzed as text and will never be executed. Website URL checks will validate user input and block requests to private or internal network addresses, including domains that resolve to these addresses and redirects to internal addresses.

## Development Plan

| **Week** | **Planned Work** |
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

The project requirements, target users, scope, and planned features have been defined. Functional and non-functional requirements, user stories, and acceptance criteria have also been prepared. GitHub Issues have been created to organize the main development tasks.

The next step is to design the system architecture, define the backend API endpoints, and set up the React and Spring Boot project structure. AWS deployment options will be evaluated during development.

**Note:** The project is currently in the planning phase. The planned features have not yet been implemented.

## GitHub Issues

The main development tasks are tracked through GitHub Issues:

- [Implement Website Accessibility and HTTPS Checks](https://github.com/ibrahim-cn/ShipShape/issues/1)
- [Add Response Time and Security Header Analysis](https://github.com/ibrahim-cn/ShipShape/issues/2)
- [Implement Rule-Based Dockerfile Analysis](https://github.com/ibrahim-cn/ShipShape/issues/3)

## References

- [Docker Documentation – Building Best Practices](https://docs.docker.com/build/building/best-practices/)
- [OWASP Secure Headers Project](https://owasp.org/projects/secure-headers-project)
