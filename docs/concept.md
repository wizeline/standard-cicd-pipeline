# Concept

*Flows*

- Applications
- Terraform
- Lambdas
- Libraies
- generator (CLI)

*Interfaces*

- Jenkins UI -same as flows
- app-manager
- Slack bot

*Components*

- docker-builder
- docker-runner
- kubernetes-deployer
- slack-notifier (or other channels)

*Stages*

- Linter:
- Unit-Test: language-specific
- other-testing: integration, functional, etc...
- Build: docker, tarball, language library packing
- Deploy: kubernetes

*Linters*

- JavaScript
- Java
- Bash
- PHP
- Python
- Dockerfile
- Go
- Web (HTML, CSS)
- Net

*Security Scans*

- Secrets in git
- truster advisor
- security monkey
- anti-virus (s3)
- AWS Config
- Language specific

*Configs/Secret management*

- app-manager
- vault?

*Constraints*

- Cost
- UX
- Mantainance
- Adoption
- Modular

*Notes*

- Separate jenkins for CI and CD
- Versioning
- Docker Registries

*Images (base)*

- languages-images
- linter-images
- testing-images
- lambda-sandbox

*To Do's*

- Define Concept
- Use Case Diagrams
- Process Diagrams
- Logic diagrams (arch)
